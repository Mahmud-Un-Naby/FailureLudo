# Online Android release preparation

## Current release direction — 28 September 2026

The user now wants online development treated as release work, with online and offline
play in the release app. This supersedes the private-testing-only, offline-only release,
mandatory separate product-flavor, and disabled-online-release requirements recorded
below. Continue on `feat/online-testing`; production publication and backend deployment
still require explicit authorization. Web/parity work remains paused.

Offline startup and play remain independent of internet, sign-in and backend availability.
Online entry is included in debug and release navigation and automatically creates/reuses
an anonymous Firebase identity. There is no mandatory sign-in screen or Google account.

## Android migration handoff — 28 September 2026

The app now sends create/join/start/leave/roll/move commands to the server-controlled
Kotlin backend. `online-protocol` shares the room model and snapshot codec with Android;
Android no longer writes move logs or chooses online dice. Confirmed snapshots drive
the shared tabletop board, corner dice, movement/capture animation and feedback settings.
Both free-for-all and four-player team rooms are supported. Offline saves, bots,
undo/redo and history remain local; durable online history is still outstanding.
Resignation, per-action bot assistance and AFK forfeiture are implemented below.

Pending commands and the active room are saved atomically in app-private no-backup
storage, bound to Firebase UID and service origin. Retrying preserves the request ID,
including after process death. Older HTTP responses cannot replace newer confirmed
snapshots. Token refresh, stale revisions, live-listener reconnection and waiting-room
host transfer are handled explicitly. Returning from an active game keeps the seat for
resume. New timed rooms continue their action/AFK timers while the player is away.

Build with `-PonlineApiUrl=https://YOUR-SERVICE.run.app` after an authorized backend
configuration/deployment. The URL must be an HTTPS origin. The current APKs were built
without a service URL: online entry explains that online play is unavailable, and offline
play remains accessible. These APKs do not yet provide live online gameplay.

Follow [plan 011](../plans/011-authoritative-online-release-plan.md) and the
[server setup guide](../online-server/README.md) for configuration and remaining work.
The checked-in Firestore rules now deny legacy `rooms`/`moves` client access; deploy them
as a coordinated migration with the new backend/app. Legacy data remains intact.
Nothing has been deployed. On 29 September 2026, all three local Firestore integration
checks and 24 server unit tests passed. The server container built and passed local
startup, non-root execution, unauthenticated rejection and SIGTERM shutdown checks.
The emulator runtime is now cached for reuse. Real Firebase authentication, two-device
play and device visual/audio review remain unverified. See plan 011 for exact results
and the remaining lifecycle/operations work.

## Resignation and team handoff — 29 September 2026

Online games now offer an explicit Resign confirmation. In team games the remaining
teammate controls both colors on the existing turns and keeps any pending dice/move;
if both resign, their team loses. In free-for-all the resigned player's pawns leave
play and the last remaining player wins. Resigned players may watch or start a new
room; they cannot regain control by rejoining. Returning to the lobby still preserves
an active seat, and disconnecting does not automatically resign.

The server persists resignation atomically with its request receipt. Android keeps an
unconfirmed resignation across retries/process death and blocks starting another room
until it is confirmed. Snapshot protocol 2 / rules `2026-09-29` includes resignation;
v1 stored snapshots and local journals upgrade without losing pending requests. Older
clients fail closed on v2, so backend/app rollout must be coordinated. See plan 011
for validation and remaining release work.

Validation: 79 engine, 33 server, four emulator integration and 124 Android tests passed.
Debug and signed/minified release APKs built with upload-key verification and vital lint.
Both builds still have no service URL; live online play and device review remain open.

## Action timer, bot assistance and AFK — 29 September 2026

New rooms allow 10 seconds per roll and a separate 10 seconds per pawn choice, including
bonus rolls. A missed action is performed once by the server bot, then the human gets
the next action window. Two minutes of continued AFK forfeits the seat, with teammate
handoff as above. A human action or "I'm back" clears AFK before that limit. Existing
v1/v2 rooms remain untimed; protocol 3 / rules `2026-09-29-afk` persists the new policy.

The online guest avatar shows a shrinking countdown ring, changing to amber/red near
expiry. Text also shows remaining action/AFK time. Timers use server time and the device's
elapsed real time; reduced motion uses discrete ring updates. Guest profiles currently
use initials. The server alone confirms bot actions and forfeits, with durable retries.

An open Android online session requests deadline checks. If everyone closes the app,
there is no running background game loop: overdue checks happen when someone returns.
Abandoned-room cleanup, rate limits, durable history and real two-device/release review
remain open. No cloud deployment or publication is included.

Verified: 49 server tests, five Firestore emulator integration tests and 128 Android
tests passed. Debug and signed/minified release APKs built with upload-key verification
and vital lint. The emulator shut down cleanly. Device visual/audio review is still
needed, and neither APK has a live service URL configured.

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
