# Nani

Nani is a controlled Android screen/file agent intended to run inside a separate Android user profile on a Samsung tablet.

## Current MVP

- API-powered planning via DeepSeek, OpenAI-compatible, or custom `/chat/completions` APIs.
- DeepSeek is the recommended default provider.
- Local Dummy / Gemma planned provider for deterministic offline test plans only.
- Android Storage Access Framework work folder selection.
- Persisted SAF work-folder permission.
- Files can be listed inside the selected work folder.
- Folders can be created inside the selected work folder.
- Files can be safely copied inside the selected work folder.
- Existing files are not overwritten; copy targets get a suffix when needed.
- Preview and local validation before execution.
- Explicit user confirmation through the Execute button.
- Foreground app logging and Settings/permission-screen guard.
- Action and plan logs without API keys, full prompts, full API responses, or full JSON plans.

## Not Implemented

- File deletion.
- Real move operations.
- Rename operations.
- Google Drive.
- Real local Gemma inference.
- Generic Accessibility automation.
- App installation or uninstallation.
- Device admin, root, or overlay capabilities.
- Notification listener or usage access permissions.

## DeepSeek Setup

1. Open **AI Settings**.
2. Select **DeepSeek API (Recommended)**.
3. Use the default Base URL:

   ```text
   https://api.deepseek.com
   ```

4. Choose a model:

   ```text
   deepseek-v4-flash
   ```

   or:

   ```text
   deepseek-v4-pro
   ```

5. Enter your API key.
6. Tap **Save**.
7. Tap **Test API**.

API keys are currently stored locally in SharedPreferences.

TODO: Move API key storage to Android Keystore before production use.

## Example Commands

```text
Liste meine Dateien
```

```text
Fasse den Arbeitsordner zusammen
```

```text
Ordner erstellen Schule/Mathe
```

```text
Sortiere meine PDFs für Schule
```

```text
Lösche alte Dateien
```

Deletion requests should be blocked by Nani safety rules.

## Build

From PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

If Java is not found, set `JAVA_HOME` to Android Studio's bundled JBR first.
