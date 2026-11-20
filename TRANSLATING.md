# Translating Bolt PDF

Thank you for helping translate **Bolt PDF**!

Bolt PDF uses standard Android XML string resources with **automatic language discovery**, so adding a new language requires **only one XML file** and **zero Kotlin code changes**.

## How to Add a New Language

1. Copy the base English file:
   ```text
   android/app/src/main/res/values/strings.xml
   ```
2. Create a new folder for your language using its Android resource qualifier (ISO 639-1 code, plus optional `-rREGION` for regional variants):
   - French: `android/app/src/main/res/values-fr/strings.xml`
   - Spanish: `android/app/src/main/res/values-es/strings.xml`
   - German: `android/app/src/main/res/values-de/strings.xml`
   - Turkish: `android/app/src/main/res/values-tr/strings.xml`
   - Simplified Chinese: `android/app/src/main/res/values-zh-rCN/strings.xml`
   - Brazilian Portuguese: `android/app/src/main/res/values-pt-rBR/strings.xml`
3. Translate the text inside each `<string name="...">...</string>` tag.
4. Open a Pull Request with your new `strings.xml` file.

Once added, Gradle automatically:
- Registers your language in Android's Per-App Language Settings (`locales_config.xml`).
- Adds your language (with its native display name) to the in-app **Settings → Language** list.
- Applies RTL layout automatically for right-to-left languages (Arabic, Persian, Hebrew, Urdu, etc.).
- Falls back to English for any untranslated keys, so partial translations are always safe and welcome!

## Translation Guidelines

- **Do not translate `name="..."` attributes** — only translate the text between `<string ...>` and `</string>`.
- **Keep `app_name` untranslated** (you can omit `<string name="app_name">` in translated files).
- **Preserve placeholders** such as `%1$d`, `%2$d`, `%1$s`, `%2$s`, and `%%` (escaped percent sign). You may reorder placeholders to match natural grammar in your language.
- **Escape XML characters**:
  - Use `\'` for apostrophes (e.g., `Don\'t`)
  - Use `\"` for double quotes
  - Use `&amp;` for `&`
