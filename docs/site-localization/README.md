# Italian, Greek and Russian localization

The actual website source is in `site/` in this repository. Its deployment workflow is `.github/workflows/pages.yml`.

## App resources

The Italian resource file `app/src/main/res/values-it/strings_v23.xml` has been updated to the current 267-key set. Greek and Russian each also contain all 267 keys. The app translations and localization notes are in this branch and PR #2.

## Website pages

The HTML translations are integrated in a separate pull request targeting `main`, because the Pages workflow publishes `site/**` from `main`:

- Website PR: [ridescreen-aa#3](https://github.com/mgb1982/ridescreen-aa/pull/3)
- Italian: `/it/`, `/it/guide/`, `/it/watch/`
- Greek: `/el/`, `/el/guide/`, `/el/watch/`
- Russian: `/ru/`, `/ru/guide/`, `/ru/watch/`

The translated HTML preserves the existing layout, CSS, screenshots and asset paths; language navigation/routing, canonical URLs, `hreflang` alternates and the sitemap include the six supported languages. The pages will not go live until PR #3 is reviewed and merged.

## Stable-release-only rule

Public website copy and download buttons must refer only to the latest stable release. Do not mention prerelease builds, test APKs, test branch names or preview-version features, and never link public download buttons to GitHub prereleases. The localized pages use `releases/latest` and do not mention the 2.4 test line.

Keep feature descriptions aligned with the stable release actually distributed by the download link. For example, do not replace the read-only PKE battery description with controls that exist only in a test build.

Product names and interface labels are preserved where readers need to recognize them in the app or Android settings: RideScreen AA, Android Auto, Wear OS, OpenCfMoto, Zontes, CFMoto, Carbit, EasyConnection, MotoPlay, PKE, McuID, APK, ADB, Wi-Fi, Bluetooth, Key Mapper, Tasker, Bugjaeger and Wear Installer 2.
