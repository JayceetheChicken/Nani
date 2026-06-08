# Nani

Nani is a controlled Android screen/file agent intended to run inside a separate Android user profile on a Samsung tablet.

## Core safety rules

- No file deletion in the first milestones.
- No root permissions.
- No device administrator permissions.
- No overlay permissions at the start.
- Accessibility may be used only through a constrained service.
- Settings and permission-management screens must be blocked/exited.
- All actions must be logged.
- File and Drive actions must require preview and explicit confirmation.
- The main user profile and main Google account must not be used for the agent.

## Planned milestones

1. Accessibility guard and foreground app logging.
2. Local folder access via Android Storage Access Framework.
3. Google Drive access using the agent Google account.
4. Rule-based command planner producing validated action plans.
5. Optional LLM/local AI planner that may only output validated JSON action plans.

## Current milestone

MVP 1: Build the Android app shell with AccessibilityService, settings blocking, logs, and Compose UI.
