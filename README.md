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

## Pleos Connect end-to-end verification

To verify that a Virtual Console vehicle signal reaches the installed app and starts its character/voice reaction, follow [the Pleos Virtual Console E2E guide](docs/PLEOS_VIRTUAL_CONSOLE_E2E.md). It covers the shared AVD/Console setup, brake and turn-signal test cases, evidence criteria, cleanup, and troubleshooting.

The Android runtime startup path and its boot-versus-ignition limitation are documented in [Pleos runtime startup](docs/PLEOS_RUNTIME_STARTUP.md).

For an internal Android build, place GitHub Packages credentials outside source control in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_CLASSIC_PAT_WITH_READ_PACKAGES
```

The release target is **33 characters**, with **13 dialogue slots per character** (one greeting plus 12 vehicle states, 429 slots total). The stable character-to-voice mapping is listed in [docs/CHARACTER_TARGET_INVENTORY_V1.csv](docs/CHARACTER_TARGET_INVENTORY_V1.csv), and the current coverage and release blockers are in [docs/RELEASE_VOICE_CHARACTER_PLAN_V1.md](docs/RELEASE_VOICE_CHARACTER_PLAN_V1.md). The 13-slot count is never a character-count limit. The release roster remains an explicit allowlist and stays empty until every listed pack passes its release gates.

## Product and user-flow design

- [Product requirements (PRD v2)](docs/PRODUCT_REQUIREMENTS_V2.md) — product scope, vehicle input contract, safety policy, release criteria, and current constraints.
- [User flows v2](docs/USER_FLOWS_V2.md) — WindowManager overlay-first startup, avatar gestures, 5+11 acquisition, dialogue, and driving lock.
- [Wireframes v2](docs/WIREFRAMES_V2.md) — Pleos HMI stays visible while avatar-anchored floating controls and compact overlay sheets handle companion actions.
- [Interactive overlay-first mockup v2](docs/mockups/voice-companion-flow-v2.html) and [PNG preview](docs/mockups/voice-companion-flow-v2.png) — real Pleos HMI frame/vehicle area with a schematic map underlay replacing the old debug Activity pane; approved roster assets remain unassigned.
- [Project and PR history](docs/PROJECT_AND_PR_HISTORY_V1.md) — repository roles, merged PR inventory, open work, and evidence gaps across all three repositories.

Package releases dispatch `voicepack-published` or `characterpack-published` with a version, or `packs-published` with both package versions. The protected `internal-build` workflow resolves private AARs, runs roster/unit validation, assembles the internal release APK, records supplied content metadata in `docs/PACK_RELEASES.md`, and only then opens or refreshes a reviewable PR. Applied versions are a no-op. It never auto-merges. Keep `DRIVEPERSONA_PACKAGES_USER`, `DRIVEPERSONA_PACKAGES_TOKEN`, and the fine-grained `DRIVEPERSONA_SYNC_TOKEN` in the protected environment; the sync token needs `Contents: write` and `Pull requests: write` for this repository. The private SDK publish workflows use the same sync token only to send the dispatch.

The protected `internal-android` CI job runs only after main receives a reviewed change and `INTERNAL_PACKAGES_ENABLED` is true. Public pull-request CI never receives package credentials.

## Asset policy

No Qwen model cache, reference voice, raw audition, unverified reference-derived character, or approved production pack binary is committed to this public repository.
