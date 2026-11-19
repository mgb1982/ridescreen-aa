// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.ParcelUuid
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in BLE listener that answers dash clock requests on already-bonded devices.
 *
 * Never scans. Never writes until the peer sends a valid EC-BTP frame (so Cardo / OBD / TPMS
 * that share `ffe0`/`fff0` stay silent). Off unless [AppSettings.bluetoothClockSync] is on.
 *
 * v2.5 experiment — one exception: a device whose NAME says it is the dash ([DashClock.nameLooksLikeDash],
 * e.g. ZT851066) and that stays silent for [PUSH_AFTER_MS] gets ONE unsolicited SYNC_TIME (epoch + raw
 * offset, the exact Carbit shape). Why: in Zontes riders' logs the BLE link opens and the dash never says a
 * word, while Carbit's own `sendSyncTime()` is an unsolicited push, not a reply (MOTO-HUB's decompile notes,
 * EcBtpClockLab). Same aim as the 00:00 work over Wi-Fi; the log says whether the clock was right after.
 */
@SuppressLint("MissingPermission")
internal class EcBtpTimeLink(
    context: Context,
    private val log: (String) -> Unit,
    private val now: () -> Date = { Date() },
    private val zone: () -> TimeZone = { TimeZone.getDefault() },
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val closed = AtomicBoolean(false)
    private val connections = mutableListOf<BluetoothGatt>()
    private val lock = Any()

    fun start(): Int {
        val adapter = bluetoothManager?.adapter
        if (adapter == null) {
            log("[EC-BTP] no Bluetooth adapter")
            return 0
        }
        if (!adapter.isEnabled) {
            log("[EC-BTP] Bluetooth is off — pair the bike in Android settings, then turn this on")
            return 0
        }
        val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        if (bonded.isEmpty()) {
            log("[EC-BTP] no bonded devices — pair the bike Bluetooth first")
            return 0
        }
        val candidates = bonded.filter { candidateWorthOpening(it) }
        val skipped = bonded.filterNot { candidateWorthOpening(it) }
            .map { runCatching { it.name }.getOrNull() ?: it.address }
        log("[EC-BTP] ${bonded.size} bonded, ${candidates.size} candidate(s) — listen only")
        if (skipped.isNotEmpty()) {
            log("[EC-BTP] skipped (no dash UUID/name): ${skipped.take(8).joinToString()}")
        }
        candidates.forEach { openGatt(it) }
        return candidates.size
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            connections.forEach { gatt ->
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
            connections.clear()
        }
    }

    private fun candidateWorthOpening(device: BluetoothDevice): Boolean {
        val name = runCatching { device.name }.getOrNull()
        if (DashClock.nameLooksLikeDash(name)) return true
        val cached: Array<ParcelUuid>? = runCatching { device.uuids }.getOrNull()
        if (cached.isNullOrEmpty()) return true
        return cached.any { SERVICE_UUIDS.contains(it.uuid) }
    }

    private fun openGatt(device: BluetoothDevice) {
        val label = runCatching { device.name }.getOrNull() ?: device.address
        val callback = object : BluetoothGattCallback() {
            private val proven = AtomicBoolean(false)
            @Volatile private var dataCharacteristic: BluetoothGattCharacteristic? = null

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (closed.get()) {
                        runCatching { gatt.disconnect() }
                        return
                    }
                    runCatching { gatt.discoverServices() }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    forget(gatt)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = SERVICE_UUIDS.firstNotNullOfOrNull { runCatching { gatt.getService(it) }.getOrNull() }
                val characteristic = service?.let { dataCharacteristicOf(it) }
                if (characteristic == null) {
                    log("[EC-BTP] $label has no EC-BTP characteristic — disconnect")
                    runCatching { gatt.disconnect() }
                    return
                }
                dataCharacteristic = characteristic
                subscribe(gatt, characteristic)
                log("[EC-BTP] listening to $label")
                if (DashClock.nameLooksLikeDash(label)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (closed.get() || proven.get()) return@postDelayed
                        val target = dataCharacteristic ?: return@postDelayed
                        val push = EcBtpProtocol.syncTimeReply(now().time, zone().rawOffset)
                        val written = runCatching {
                            gatt.writeCharacteristic(target, push, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                        }.getOrNull()
                        log("[EC-BTP] $label silent for ${PUSH_AFTER_MS / 1000} s — pushed SYNC_TIME once " +
                            "(Carbit shape, ${push.size}B, result=$written). Check the dash clock.")
                    }, PUSH_AFTER_MS)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (closed.get()) return
                val frame = EcBtpProtocol.parse(value)
                if (frame == null) {
                    log("[EC-BTP] $label sent ${value.size}B (not EC-BTP) — stay silent")
                    return
                }
                if (proven.compareAndSet(false, true)) {
                    log("[EC-BTP] $label speaks EC-BTP — clock replies enabled")
                }
                val cmd = frame.command
                log("[EC-BTP] $label cmd=0x${(cmd.toInt() and 0xFF).toString(16)} len=${frame.payload.size}")
                val reply = when (cmd) {
                    EcBtpProtocol.CMD_SYNC_TIME ->
                        EcBtpProtocol.syncTimeReply(now().time, zone().rawOffset)
                    EcBtpProtocol.CMD_QUERY_TIME ->
                        EcBtpProtocol.queryTimeReply(now(), zone())
                    else -> null
                } ?: return
                val target = dataCharacteristic ?: return
                val written = runCatching {
                    gatt.writeCharacteristic(target, reply, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                }.getOrNull()
                log("[EC-BTP] answered clock (${reply.size}B, result=$written)")
            }
        }
        val opened = openGattTransport(device, callback, label) ?: return
        synchronized(lock) {
            if (closed.get()) {
                runCatching { opened.disconnect() }
                runCatching { opened.close() }
            } else {
                connections += opened
            }
        }
    }

    /** LE first (dash clock), then AUTO — some Zontes names never reach HCI on LE-only. */
    private fun openGattTransport(
        device: BluetoothDevice,
        callback: BluetoothGattCallback,
        label: String,
    ): BluetoothGatt? {
        val le = runCatching {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull()
        if (le != null) return le
        log("[EC-BTP] LE open missed $label — retry AUTO")
        val auto = runCatching {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_AUTO)
        }.getOrNull()
        if (auto == null) log("[EC-BTP] could not open $label")
        return auto
    }

    private fun dataCharacteristicOf(service: BluetoothGattService): BluetoothGattCharacteristic? =
        CHARACTERISTIC_UUIDS.firstNotNullOfOrNull { runCatching { service.getCharacteristic(it) }.getOrNull() }

    private fun subscribe(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        runCatching { gatt.setCharacteristicNotification(characteristic, true) }
        val descriptor = runCatching { characteristic.getDescriptor(CCC_UUID) }.getOrNull() ?: return
        runCatching { gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
    }

    private fun forget(gatt: BluetoothGatt) {
        synchronized(lock) { connections.remove(gatt) }
        runCatching { gatt.close() }
    }

    private companion object {
        const val PUSH_AFTER_MS = 4_000L
        val SERVICE_UUIDS = listOf(
            UUID.fromString("00001c00-d102-11e1-9b23-000efb0000b2"),
            UUID.fromString("0000474d-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("00001c00-d102-11e1-9b23-000efb0000c3"),
            UUID.fromString("0000474e-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("00001c00-d102-11e1-9b23-000efb0000c6"),
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        )
        val CHARACTERISTIC_UUIDS = listOf(
            UUID.fromString("00001c0f-d102-11e1-9b23-000efb0000b2"),
            UUID.fromString("00004b59-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("00001c0f-d102-11e1-9b23-000efb0000c6"),
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
        )
        val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
