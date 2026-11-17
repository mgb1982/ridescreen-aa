# Translations

OpenCfMoto follows the **phone language** (and Android 13+ *Settings → Apps → OpenCfMoto → Language*).

Draft locales ship for Discord community languages:

| Folder | Language |
|--------|----------|
| `app/src/main/res/values/` | English (source of truth) |
| `values-th/` | ไทย (in-app picker or the phone language) |
| `values-de/` | Deutsch |
| `values-it/` | Italiano |
| `values-fr/` | Français |
| `values-es/` | Español |
| `values-ca/` | Català |
| `values-pt/` | Português |
| `values-pl/` | Polski |
| `values-cs/` | Čeština |
| `values-ro/` | Română |
| `values-nl/` | Nederlands |
| `values-hu/` | Magyar |
| `values-tr/` | Türkçe |
| `values-ko/` | 한국어 |
| `values-bg/` | Български (sashop2001) |

## How to improve a translation

1. Fork [zanderp/open-cfmoto](https://github.com/zanderp/open-cfmoto).
2. Edit only `app/src/main/res/values-<lang>/strings.xml`.
3. **Keep `name="…"` keys unchanged** — translate the text between the tags.
4. Preserve Android placeholders exactly: `%1$s`, `%2$d`, `\n`, and escaped apostrophes (`\'`).
5. Leave brand / model tokens alone when they are product names (CFMoto, Android Auto, GPX, bike codes like `800NK`).
6. Open a PR titled e.g. `i18n(de): improve Setup and Connect strings`.

Missing keys fall back to English automatically.

## Adding a new language

1. Add `<locale android:name="xx"/>` in `app/src/main/res/xml/locales_config.xml`.
2. Copy `values/strings.xml` → `values-xx/strings.xml` and translate.
3. Mention the new locale in the PR description.

## Notes for maintainers

- English source: `app/src/main/res/values/strings.xml`.
- New UI copy must be added there first, then mirrored into each `values-*` (or left for English fallback until translated).
- Diagnostic **Logs** lines stay English on purpose so support can read shared logs.
