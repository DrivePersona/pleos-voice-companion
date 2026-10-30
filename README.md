# DrivePersona — Pleos Voice Companion

DrivePersona is an in-car voice companion for Pleos. It converts vehicle events such as braking, turn signals, drive-mode changes, and tire checks into concise character-led voice feedback.

This repository is the public review surface for the application: vehicle reaction rules, UI/UX, simulator, and integration contracts are visible here. Approved voice and character assets are intentionally isolated in private SDK repositories.

## Repository map

| Repository | Visibility | Purpose |
| --- | --- | --- |
| `DrivePersona/pleos-voice-companion` | Public | This app, web simulator, design documents, and pack integration contracts |
| `DrivePersona/voice-pack-sdk` | Private | Voice-pack runtime AAR and approved audio packs |
| `DrivePersona/character-pack-sdk` | Private | Character-pack runtime AAR and approved character packs |

## Build variants

- `reviewDebug` contains no commercial voice or character assets. It remains the safe presentation build.
- `internalRelease` resolves approved private AARs from GitHub Packages and is for the DrivePersona team only.

The web simulator has no external dependency:

```powershell
node --test
python -m http.server 5183
```

For an internal Android build, place GitHub Packages credentials outside source control in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

The package versions are fixed in `android/gradle.properties`; they are updated through a reviewable pull request after a private pack release.

The `internal-android` CI job is deliberately disabled until an organization owner adds the package credentials to the protected `internal-build` environment and sets its `INTERNAL_PACKAGES_ENABLED` variable to `true`. Public pull-request CI never receives these credentials.

## Asset policy

No Qwen model cache, reference voice, raw audition, unverified reference-derived character, or approved production pack binary is committed to this public repository.
