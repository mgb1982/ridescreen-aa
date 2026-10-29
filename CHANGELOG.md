# Changelog

Changes for the RideScreen AA fork. Version 2.4 is currently a preview line, not a stable release.

## 2.4 preview · build 368

- **PKE battery/status card:** read the keyless box battery voltage and reported lock state over Bluetooth; live voltage is available while projecting.
- **PKE controls:** explicitly lock or unlock the bike and release the seat or fuel cap. Seat and fuel-cap actions ask for confirmation.
- **Home controls:** a bike-controls card and a home-screen widget with Connect, Unlock, Lock, Seat and Fuel cap actions. The widget adapts to a compact icon-only layout when space is limited.
- **Unlock on Connect:** optional and off by default. It only runs when the rider taps Connect, never during automatic connection.
- **Faster PKE actions:** open the Bluetooth LE link while the rider chooses an action and record timings for the connection, challenge and command reply.
- **McuID setup:** scan the bike's information QR to find the McuID. The stored McuID is excluded from Android backup and device transfer.
- **Visual refresh:** line icons for the home tiles and PKE controls, plus road/seat icons and a garage photo in the widget.
- **Language coverage:** English, Spanish and Catalan resources are joined by French, Italian, German and Portuguese translations.

### Compatibility and limitations

- The PKE controls require a compatible keyless box, the PKE code and the McuID from the bike's information QR. The feature has been validated on a Zontes 125X; compatibility with other bikes is not guaranteed.
- PKE provides battery voltage and lock information, not full vehicle telemetry. RideScreen AA does not currently read engine RPM or ECU data.
- The widget does not display the actual lock state. Check the battery/status card instead.
- Sending a command is not confirmation that the bike acted on it. Check the bike after each command.
- The keyless box accepts one Bluetooth client at a time. If another app is connected to it, RideScreen AA cannot connect.

## 2.4 preview · builds 363–367

- Re-established the clean 2.4 line with PKE control while retaining the existing Android Auto connection and trip functionality.
- Restored the lock/unlock dialog flow validated on the earlier test line.
- Added the PKE home widget and home-screen actions, including the garage photo.
- Added optional Unlock on Connect and refreshed the home-screen tiles with line icons.
- Opened the Bluetooth LE link ahead of the action to reduce tap-to-command delay and added per-phase timing to the log.

## Earlier versions

For previous release notes and downloadable builds, see [GitHub Releases](https://github.com/mgb1982/ridescreen-aa/releases).
