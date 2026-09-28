# Online Android release preparation

## Current release direction — 28 September 2026

The user now wants online development treated as release work, with online and offline
play in the release app. This supersedes the private-testing-only, offline-only release,
mandatory separate product-flavor, and disabled-online-release requirements recorded
below. Continue on `feat/online-testing`; production publication and backend deployment
still require explicit authorization. Web/parity work remains paused.

Offline startup and play must continue to work without internet, sign-in, or a backend.
Online guests must be able to create/join rooms without a Google account. The retained
implementation supports anonymous Firebase authentication through **Play as Guest**;
automatic guest entry without an authentication screen has not been implemented.

Current compatibility assessment from source inspection:
- Android and engine source match the merged offline baseline on `main`.
- Online uses the shared current rules engine and deterministic move application,
  including stacked-pawn selection and the home-entry choice.
- Online still renders `LudoBoardCanvas` with its older player rails and dice, while
  offline uses `TabletopGameLayout` and `TabletopBoard`. Offline movement animation,
  feedback/settings integration, saves, and history/replay are not wired into online.
- Online currently creates human-only free-for-all games; offline team/bot setup is
  not exposed online. Offline undo/redo is not an online multiplayer contract.
- Navigation is still debug-only. Release inclusion requires implementation, not just
  this scope change. Shared rules do not establish reliable network play.

The user selected a server-controlled Kotlin engine on Cloud Run, with Firebase guest
identity and Firestore persistence. Follow [plan 011](../plans/011-authoritative-online-release-plan.md)
for implementation and validation. Its backend foundation precedes Android migration,
tabletop integration and release entry. The old client-written relay is being replaced,
not promoted to release unchanged. See [server setup](../online-server/README.md).
Keep test and production backend configuration deliberate. Validate release builds,
offline startup without a connection, guest room entry, and complete two-device games
before declaring release readiness. Backend unit tests, distribution build, Java 17
startup smoke checks and Android compilation now pass; emulator/container checks remain
blocked by runtime download failures. See plan 011 for exact validation and remaining work.
No device verification or deployment has been performed.

## Historical branch handoff — 28 September 2026

The sections below preserve the earlier preparation decision; conflicting release
scope and build-isolation requirements are superseded by the current direction above.

The user requested merging the completed offline improvements into `main`, preserving
this branch's preparation, deleting `feat/offline-improvements` after verifying ancestry,
and continuing work on `feat/online-testing`.

- `main` contains all 34 commits from the former offline branch plus updated branch guidance.
- `feat/online-testing` merges that baseline and preserves its original preparation commit
  (`bff049e`); it is the active development branch.
- The merged app includes the debug-only private-room preview from `cea04e3`.
  Its debug unit tests and APK build passed before consolidation. Firebase deployment,
  two-device play, and online release readiness remain unverified.
- The merge changes documentation only relative to `main`; the Android and rules-engine
  source trees are identical. Resolve future shared work against `main`.

Next online implementation remains build isolation below. The preview currently shares
production app identity, Firebase dependencies, and app data; debug-only navigation is
not the intended final isolation. After isolating builds, address the sync/authorization
issues recorded in the [online handoff](../plans/007-online-multiplayer-plan.md).

## Original testing decision — 25 September 2026 (branch names updated)

Online Android development may resume before the offline Play Store release.
The online version is for private testing; it must not be submitted to Play Store.
The production release remains entirely offline.

- Offline release baseline: `main`.
- Online development branch: `feat/online-testing`.
- Shared starting commit: `6fa82375e580c83ca1d2a9f8610146feed8026ed`.
- This preparation changes branch guidance only; it does not enable multiplayer,
  change Android build variants, deploy Firebase, or create a new APK.

## First implementation: isolate Android builds

Introduce `offline` and `online` product flavors and move the current online preview
into the online flavor before distributing it to private testers.
Keep `com.failureludo` for offline production. Use `com.failureludo.online.test`
and the launcher name “Failure Ludo Online Test” for private online builds so both
apps can be installed together with separate saves and app data.

Confine authentication, online repositories, screens, view models, Firebase and
Google sign-in dependencies, Google Services configuration, and network permissions
to the online source set. Offline startup and gameplay must need no credentials,
backend, or network. Inspect the merged offline manifest and dependency graph to
verify this; hiding online buttons is insufficient.

Keep online release variants disabled initially. Use an online debug APK for direct
installation by testers. Preserve offline upload-key verification when flavor task
names change, and update release commands to select the offline variant explicitly.
Do not reuse production Firebase configuration blindly: configure the separate test
app identity and an appropriate test backend before enabling authentication.

Current evidence: `AppNavigation.kt` exposes offline routes and a debug-only online
preview. `app/build.gradle.kts` still declares Firebase/sign-in dependencies globally and
`app/src/main/AndroidManifest.xml` declares network permissions. Existing auth and
online code remains under `app/src/main`; it has not been validated for this effort.
The offline production baseline on `main` still needs its release checks.

## Then restore private multiplayer

Reuse the native board and pure `game-engine` rules. Review the existing Android
online implementation and [earlier multiplayer plan](../plans/007-online-multiplayer-plan.md)
before restoring sign-in, room creation/joining, waiting rooms, and online games.
Validate authorization, synchronized turns, reconnects, and error handling using
a deliberately configured test backend. Do not treat the old plan's checkboxes as
proof of present functionality.

Build and verify both variants; confirm the offline build works without internet
and install both apps together. Test a complete multiplayer game across two devices
before calling the online APK playable. Device review remains with the user unless
requested. No publication or backend deployment is part of this preparation.

## Carrying shared improvements

Keep online-specific work on `feat/online-testing`. Transfer reviewed, self-contained
shared fixes to `main` selectively; do not merge unfinished online features into the
release baseline. Any build-isolation change needed for offline production must also
be applied and verified on `main` before its release.

The user's decision here supersedes the older sequencing requirement in
[plan 009](../plans/009-offline-android-redesign-goal.md) to finish offline publication
before resuming online development. Its offline release requirements still apply.
