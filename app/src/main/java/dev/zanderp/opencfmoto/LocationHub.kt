// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * The one place that registers for GPS/NETWORK updates. Trip recorder, speed volume, the map
 * and live share subscribe here with their own cadence ([LocationFanout.Spec]); the hub keeps a
 * single registration per provider with the combined plan and hands each subscriber only the
 * fixes its own spec would have produced. Fix delivery happens on the subscriber's looper.
 */
object LocationHub {
    private class Sub(
        val specs: List<LocationFanout.Spec>,
        val listener: LocationListener,
        val looper: Looper,
    ) {
        val lastAt = HashMap<String, Long>()
        val lastLoc = HashMap<String, Location>()
    }

    private class Reg(val plan: LocationFanout.Plan, val listener: LocationListener)

    private val subs = LinkedHashMap<Any, Sub>()
    private val regs = HashMap<String, Reg>()
    private var lm: LocationManager? = null
    private val hubLooper: Looper by lazy { Looper.getMainLooper() }

    /** Returns false if registration failed; the hub state is then as before the call. */
    @Synchronized
    fun subscribe(
        ctx: Context,
        owner: Any,
        providers: List<LocationFanout.Spec>,
        listener: LocationListener,
        looper: Looper = Looper.getMainLooper(),
    ): Boolean {
        val mgr = lm ?: (ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
            ?.also { lm = it } ?: return false
        val previous = subs.remove(owner)
        val touched = (providers.map { it.provider } + (previous?.specs?.map { it.provider } ?: emptyList())).toSet()
        subs[owner] = Sub(providers, listener, looper)
        return try {
            touched.forEach { sync(mgr, it) }
            true
        } catch (_: Exception) { // includes SecurityException
            subs.remove(owner)
            if (previous != null) subs[owner] = previous
            touched.forEach { runCatching { sync(mgr, it) } }
            false
        }
    }

    @Synchronized
    fun unsubscribe(owner: Any) {
        val old = subs.remove(owner) ?: return
        val mgr = lm ?: return
        old.specs.map { it.provider }.toSet().forEach { runCatching { sync(mgr, it) } }
    }

    @SuppressLint("MissingPermission")
    private fun sync(mgr: LocationManager, provider: String) {
        val specs = subs.values.flatMap { s -> s.specs.filter { it.provider == provider } }
        val plan = LocationFanout.plan(specs)
        val cur = regs[provider]
        if (plan == null) {
            if (cur != null) {
                regs.remove(provider)
                try { mgr.removeUpdates(cur.listener) } catch (_: Exception) {}
            }
            return
        }
        if (cur?.plan == plan) return
        if (cur != null) {
            regs.remove(provider)
            try { mgr.removeUpdates(cur.listener) } catch (_: Exception) {}
        }
        val l = internalListener(provider)
        mgr.requestLocationUpdates(provider, plan.minTimeMs, plan.minDistanceM, l, hubLooper)
        regs[provider] = Reg(plan, l)
        val n = subs.values.count { s -> s.specs.any { it.provider == provider } }
        LogBus.log("[LOC] hub: ${provider.uppercase()} ${plan.minTimeMs} ms/${plan.minDistanceM} m for $n subscribers")
    }

    private fun internalListener(provider: String) = object : LocationListener {
        override fun onLocationChanged(location: Location) = dispatchFix(provider, location)
        override fun onProviderEnabled(p: String) = forward(provider) { it.onProviderEnabled(p) }
        override fun onProviderDisabled(p: String) = forward(provider) { it.onProviderDisabled(p) }
    }

    private fun post(looper: Looper, block: () -> Unit) {
        if (looper === Looper.myLooper()) block() else Handler(looper).post(block)
    }

    private fun dispatchFix(provider: String, location: Location) {
        val out = ArrayList<Pair<Sub, Location>>()
        synchronized(this) {
            val reg = regs[provider] ?: return
            val now = SystemClock.elapsedRealtime()
            for (s in subs.values) {
                val spec = s.specs.firstOrNull { it.provider == provider } ?: continue
                val prev = s.lastLoc[provider]
                val moved = prev?.distanceTo(location)
                if (LocationFanout.shouldDeliver(spec, reg.plan.minTimeMs, s.lastAt[provider] ?: 0L, now, moved)) {
                    s.lastAt[provider] = now
                    s.lastLoc[provider] = location
                    out.add(s to location)
                }
            }
        }
        for ((s, loc) in out) post(s.looper) { s.listener.onLocationChanged(loc) }
    }

    private fun forward(provider: String, call: (LocationListener) -> Unit) {
        val targets = synchronized(this) {
            subs.values.filter { s -> s.specs.any { it.provider == provider } }
        }
        for (s in targets) post(s.looper) { call(s.listener) }
    }
}
