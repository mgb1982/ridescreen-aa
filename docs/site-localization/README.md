# Greek and Russian website localization

These files are page-by-page Greek and Russian translation drafts for the public pages currently visible at [ridescreen.app](https://ridescreen.app/). They are prepared for integration into the website's real source while preserving the current layout, screenshots, download buttons and other assets.

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

## Website source and publishing

The user indicates that the source and deployment workflow should be in the `ridescreen` repository. However, `mgb1982/ridescreen` is not present in the repositories currently exposed by the connected GitHub integration, and a direct content lookup returns 404. Do not assume the repository does not exist; its access may need to be enabled for the GitHub integration.

These drafts are not published by the app-repository PR. Once the `ridescreen` repository is accessible, inspect its current source, routing, assets and deployment workflow, then integrate these translations using that project's conventions.

## Stable-release-only website rule

Public website copy and download links must refer only to the latest stable release. Do not mention prerelease builds, test APKs, test branch names or preview-version features on the website, and do not link public download buttons to GitHub prereleases. The translated homepage currently follows the existing stable public page's version wording; verify the stable-release URL in the actual site source before publishing.

The currently published source copy includes a statement that the PKE battery card is read-only. Keep public feature descriptions aligned with the stable release actually distributed by the download link; do not replace this claim with preview-only functionality.

The translations preserve product names and interface labels where readers need to recognize them in the app or Android settings: RideScreen AA, Android Auto, Wear OS, OpenCfMoto, Zontes, CFMoto, Carbit, EasyConnection, MotoPlay, PKE, McuID, APK, ADB, Wi-Fi, Bluetooth, Key Mapper, Tasker, Bugjaeger and Wear Installer 2.
