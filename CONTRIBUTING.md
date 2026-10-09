# Contributing to Meowzix

Thanks for helping improve Meowzix. Keep changes focused, testable, and consistent with its local-first architecture.

## Before starting

- Review the [README](README.md), [system specification](docs/MEOWZIX_SYSTEM_SPEC.md), and any relevant architecture decisions.
- For substantive changes, open an issue or describe the intended behavior in the pull request.
- Never commit Telegram credentials, release signing keys, personal media, session files, or private logs.

## Development

1. Create a descriptive branch from the latest `main` (for example, `fix/queue-resume`).
2. Implement the smallest coherent change.
3. Add or update tests for changed behavior.
4. Run the applicable validation commands:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Android-specific behavior should also be checked with an emulator or physical device using `./gradlew :app:connectedDebugAndroidTest` when relevant.

## Pull requests

Use an actionable title and describe **what changed**, **why**, **how it was tested**, and **any limitations or follow-up work**. Keep unrelated refactors separate.

Pay particular attention to playback and queue correctness, offline recovery, Room schema migrations, Telegram authorization, privacy, and accessibility.

## Engineering boundaries

- Treat Telegram and MediaStore as sources of canonical tracks, not competing music libraries.
- Keep playback and user interface concerns separate.
- Keep all listening-history-based ranking out of Pure Shuffle.
- Handle absent, expired, or inaccessible sources without breaking local playback.
- Prefer explicit, recoverable errors over silent failures.

## Licensing

Contributions are made under the repository's [AGPL-3.0 license](LICENSE).
