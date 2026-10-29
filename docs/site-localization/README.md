# Greek and Russian website localization

These files are translation handoffs for the public pages currently visible at [ridescreen.app](https://ridescreen.app/). They are organized by page so they can be moved into the website's real source without reconstructing text from this PR.

## Included

- `el/home.md` and `ru/home.md`: home page.
- `el/phone-guide.md` and `ru/phone-guide.md`: installation and troubleshooting guide for the phone.
- `el/watch-guide.md` and `ru/watch-guide.md`: Wear OS watch guide.

Proposed URL structure, subject to the website's actual routing conventions:

| Page | Greek | Russian |
|---|---|---|
| Home | `/el/` | `/ru/` |
| Phone guide | `/el/guide/` | `/ru/guide/` |
| Watch guide | `/el/watch/` | `/ru/watch/` |

## Deployment blocker

The connected GitHub repositories do not include the source or deployment configuration for `ridescreen.app`. These translations are therefore not published by this PR. To integrate them into the live site, provide access to the website repository or its supported publishing workflow. Keep the existing screenshots, download buttons, and image assets in their current positions when inserting the translations.

## Source-page notes before publishing

The currently public website still contains v2.3 copy, including references to older APK version examples and a statement that the PKE battery card is read-only. The current 2.4 preview line has newer PKE controls. This handoff translates the currently published page structure rather than silently changing the product claims; review and update the Spanish/Catalan/English source copy at the same time before a public release.

The translations preserve product names and interface labels where readers need to recognize them in the app or Android settings: RideScreen AA, Android Auto, Wear OS, OpenCfMoto, Zontes, CFMoto, Carbit, EasyConnection, MotoPlay, PKE, McuID, APK, ADB, Wi-Fi, Bluetooth, Key Mapper, Tasker, Bugjaeger and Wear Installer 2.
