# Greek, Russian and Italian website localization

These Markdown files are translation handoffs for the homepage, phone installation guide and Wear OS guide. The live website source is in `site/` in this repository, and the Pages workflow is `.github/workflows/pages.yml`.

## Included drafts

- `el/home.md` and `ru/home.md`: home page.
- `el/phone-guide.md` and `ru/phone-guide.md`: installation and troubleshooting guide for the phone.
- `el/watch-guide.md` and `ru/watch-guide.md`: Wear OS watch guide.

## Website integration

The Greek, Russian and Italian HTML translations have been integrated into the actual static site in a separate pull request targeting `main`, because `.github/workflows/pages.yml` publishes `site/**` to ridescreen.app only from `main`:

- Website PR: [ridescreen-aa#3](https://github.com/mgb1982/ridescreen-aa/pull/3)
- Greek pages: `/el/`, `/el/guide/`, `/el/watch/`
- Russian pages: `/ru/`, `/ru/guide/`, `/ru/watch/`
- Italian pages: `/it/`, `/it/guide/`, `/it/watch/`

The site PR keeps the existing CSS, screenshots and asset paths, adds Italian, Greek and Russian language navigation and browser-language routing, sets canonical and `hreflang` metadata, and updates `site/sitemap.xml`. The pages will not go live until PR #3 is reviewed and merged.

## Stable-release-only rule

Public website copy and download buttons must refer only to the latest stable release. Do not mention prerelease builds, test APKs, test branch names or preview-version features on the website, and never link public download buttons to GitHub prereleases. The new pages are based on the current public stable website copy and keep its `releases/latest` download URL; they do not mention the 2.4 test line.

The public feature descriptions must stay aligned with the stable release actually distributed by the download link. For example, do not replace the current read-only PKE battery description with controls that only exist in a test build.

The translations preserve product names and interface labels where readers need to recognize them in the app or Android settings: RideScreen AA, Android Auto, Wear OS, OpenCfMoto, Zontes, CFMoto, Carbit, EasyConnection, MotoPlay, PKE, McuID, APK, ADB, Wi-Fi, Bluetooth, Key Mapper, Tasker, Bugjaeger and Wear Installer 2.
