# Nani

Nani is a controlled personal Android screen/file agent intended to run inside a separate Android user profile on a Samsung tablet.

Nani plans through an AI provider, validates every plan locally, shows a preview, and only executes locally allowed operations. The AI never directly controls Android, files, permissions, or apps.

## Current Agent MVP

- Compose main screen with Agent, Guard, Accessibility, AI provider, file access, current screen, internet gate, plan preview, execution controls, security rules, and logs.
- DeepSeek API is the recommended default planning provider.
- OpenAI-compatible and Custom `/chat/completions` providers are available.
- Local Dummy / Gemma planned provider is a deterministic offline test stub only.
- SAF Workspace Mode for one or more user-selected work folders.
- Optional Broad Agent Storage Mode using `MANAGE_EXTERNAL_STORAGE` for local/private builds only.
- Broad storage status is passive. Nani does not open or operate Android Settings to grant it.
- Foreground app logging and Settings/permission/package-installer guard remain active when Agent and Guard are enabled.
- AccessibilityService stays inactive until manually enabled by the user in Android settings.
- Accessibility can read the visible screen tree and perform controlled, node-based gestures after local validation.
- Stop, Pause, Discard, Internet Confirmation, and Final Submit Confirmation controls are visible in the UI.
- Agent Loop runs one visible step at a time: plan, validate, execute one operation, read the screen again, then continue.
- Agent Loop has a default max of 20 steps and a 10 second timeout per step.

## What Nani Can Do

- List files.
- Read text files up to the preview limit.
- Summarize files and folders with local metadata.
- Search and classify files.
- Create folders.
- Create text files.
- Edit or append text files after preview and confirmation.
- Copy files without overwriting existing targets.
- Rename files inside the same allowed folder without overwriting.
- Read the current screen structure.
- Find visible buttons, text fields, and scrollable containers.
- Tap visible nodes in allowed apps.
- Fill safe visible text fields.
- Scroll visible containers.
- Open Browser/URLs only after Internet Confirmation.
- Prepare forms without submitting them.
- Run a step-by-step personal agent loop for simple app, browser, webpage, and form tasks.
- Use DeepSeek/OpenAI-compatible/Custom API planning when configured.
- Use Local Dummy for offline safety tests.

## What Nani Cannot Do

- Delete files.
- Move files by deleting originals.
- Open or operate Android Settings.
- Change permissions.
- Install or uninstall apps.
- Use root, Device Admin, or overlay permissions.
- Access the main Android user profile.
- Operate banking, payment, authenticator, password-manager, Settings, permission, or package-installer screens.
- Use Browser/Internet/Web/online services without separate user confirmation.
- Submit forms, send messages, post, book, buy, or pay without Final Submit Confirmation.
- Fill password, PIN, TAN, 2FA, captcha, credit-card, IBAN, ID, or health fields automatically.
- Use Google Drive.
- Run a native local model runtime.
- Perform blind clicks or coordinate-only clicks.
- Run autonomous background loops without visible controls.

## UI Agent Safety

Nani reads a shortened `ScreenSnapshot` from Accessibility:

- foreground package
- app label when available
- window title when available
- visible node text, content description, view ID, class name, traits, and bounds

Text is shortened, password nodes are masked, and logs do not store full screen contents or form values. UI actions must target visible Accessibility nodes. Settings, permission controllers, package installers, app stores, banking, payment, authenticator, password-manager, Knox/security, and account-management screens are blocked by policy.

Allowed UI operations include `read_screen`, `tap_node`, `set_text`, `append_text`, `scroll`, `press_back`, `press_home`, `open_app`, `wait_for_screen`, `find_node`, `select_option`, `open_url`, `set_field_by_label`, `set_field_by_hint`, `set_field_by_node_id`, and `click_button_by_text`.

The Agent Loop asks the AI for only the next small step using `actionType="agent_step"`. Long multi-step UI plans are not executed blindly. After each step, Nani reads the screen again and waits for the next controlled step.

Forbidden UI operations include settings/permission changes, app install/uninstall, payment approval, purchase confirmation, password entry, 2FA entry, captcha solving, file deletion, root, device-admin, and overlay actions.

## File Access Modes

### SAF Workspace Mode

SAF is the recommended mode. The user manually selects one or more work folders. Nani can act only inside the granted workspace roots and still cannot delete files.

### Broad Agent Storage Mode

Broad storage is optional for local/private Agent-profile builds. The manifest declares:

```text
android.permission.MANAGE_EXTERNAL_STORAGE
```

At runtime Nani checks `Environment.isExternalStorageManager()`. If access is not granted, the UI only shows:

```text
Broad file access not granted. Enable manually if you want full Agent-user shared storage access.
```

The user must grant this manually. Nani must not enter Settings or permission-management screens to grant it.

## Internet Gate

Normal AI planning through the configured provider is allowed because the user explicitly configures it in AI Settings.

Agent actions that use Browser, URLs, uploads, sharing, messages, email, web search, cloud, posts, or other online services require a separate plan flag and a separate confirmation in the UI before execution can proceed. Without that confirmation, the executor refuses the plan.

`open_url` can open a browser through Android `ACTION_VIEW` after Internet Confirmation even if Accessibility is not ready yet. Reading or operating the webpage after that still requires Accessibility.

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
Öffne Chrome und suche nach Mathe Ableitungen
```

```text
Fülle dieses Formular aus
```

```text
Lies diese Webseite zusammen
```

```text
Lösche alte Dateien
```

Deletion requests should be blocked by Nani safety rules. Fallback recognition for `loesche` is also kept.

## MVP Limits

- The loop is not a long-running autonomous background agent.
- The AI plans one step at a time; the user remains in control with Continue, Pause, Stop, and Discard.
- Webpage interaction uses Android Accessibility, not perfect DOM automation.
- Login, password entry, 2FA, TAN, captcha, banking, payment, purchases, bookings, app installs, permission changes, Settings, and file deletion remain blocked.
- Form fields can be prepared, but sending/submitting requires Final Submit Confirmation.

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

## Build

From PowerShell:

```powershell
.\setup-java.ps1
.\gradlew.bat assembleDebug
```

If Java is not found, set `JAVA_HOME` to Android Studio's bundled JBR first.
