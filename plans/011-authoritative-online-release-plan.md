# Plan 011 — Server-controlled online Android release

## Decision and scope — 28 September 2026

Build one Android release with offline and online play. Online uses a Kotlin/JVM
service on Cloud Run, Firebase anonymous identity, and Firestore persistence/live
updates. Reuse `game-engine` on both Android and the server. No managed VM is needed.
Offline play remains local, with its existing saves, bots, undo/redo and history.
Google sign-in is optional; guest entry should be automatic when entering online.
Continue on `feat/online-testing`. Web/parity work remains paused. No cloud deployment
or production publication is authorized by this plan.

The server owns membership, turn order, dice, legal moves, winners and state revisions.
Clients send intentions, never dice outcomes or replacement game state. A confirmed
snapshot is the source of truth; reconnect reads that snapshot. Client animation can
start promptly but must not permanently advance an unconfirmed game.

## Delivery increments

1. **Backend foundation (implemented; local integration/container checks passed)**
   - Standalone `online-server` JVM module depends on the existing engine.
   - Authenticated HTTP create/join/start/roll/move/read endpoints, including guests.
   - Cryptographic server dice; engine validation for pawn and home-entry selection.
   - Atomic Firestore room + request receipt writes, expected revisions, idempotent retries.
   - Versioned snapshots and separate collections from the old client-written preview.
   - Tests for authority, concurrency, retry, serialization and HTTP boundaries;
     emulator checks for persistence and access rules; container packaging.
2. **Android migration and release entry (implemented; device checks open)**
   - Create/reuse anonymous identity automatically; remove mandatory auth-screen step.
   - Replace legacy room/move writes and local online dice with the HTTP command API.
   - Observe member-readable confirmed snapshots; persist room/request IDs for recovery.
   - Handle lost responses, stale revisions, token expiry and listener failures explicitly.
   - Share tabletop layout, pawn/dice animation and sound/settings with offline play.
   - Include online navigation in release; configure backend URL without debug-only gating.
   - Disable legacy client-write collections as part of coordinated migration.
3. **Complete multiplayer lifecycle and operations**
   - Waiting-room leave/host transfer and active-game resignation/team handoff are
     implemented, along with per-action bot assistance and AFK forfeiture. Implement
     abandoned-room cleanup and production operations with regression coverage.
   - Add durable action history/replay and rules-version rollout/migration policy.
   - Set quotas/rate limits, abuse controls for guests, receipt retention, monitoring,
     least-privilege service identity, dependency/container maintenance and cost limits.
   - Do not expire request receipts while their room can still accept commands.
4. **Release verification and authorized deployment**
   - Validate Firebase providers, IAM and Firestore rules against a deliberate test backend.
   - Two-device complete games: guests, concurrent joins, lost responses, reconnect,
     process death, team play, invalid commands and upgrades.
   - Build/test release Android, verify offline startup and play with no internet, and
     complete device visual/audio and release checks.
   - Deploy backend/rules only with explicit authorization; publish only after approval.

## Command and persistence contract

`Authorization: Bearer <Firebase ID token>` establishes identity, including anonymous
users. Tokens are verified by Firebase Admin; the request cannot choose its UID.
A request ID is scoped to that UID across all writes and must be reused on retries
with exactly the same command. Its atomic receipt prevents duplicate moves or rerolls.
Mutating existing games requires the confirmed revision; join checks current room
capacity inside the transaction without exposing the room to nonmembers first.

Room snapshots carry protocol and rules versions. Known v1/v2 snapshots upgrade to v3
without retroactive timers; unsupported version pairs fail closed. The server accepts human free-for-all and four-seat team games through the
existing engine. Recent UI events are bounded; they are not a permanent game archive.
Each accepted operation updates one room revision. A duplicate returns the latest
snapshot plus the original accepted revision, so clients must apply revisions monotonically.
A no-legal-move roll advances in the same commit; the rolled value remains in lastAction.

Cloud Run process memory is never authoritative. New data lives in
`authoritativeRooms` and `authoritativeRequests`. Client reads require membership;
client writes are denied. Existing `rooms` preview data is not silently migrated.
The Admin SDK bypasses Firestore rules, so the controller enforces all permissions.

## References

- [Current release handoff](../docs/online-testing.md)
- [Rule contract](../docs/game-rules-live.md)
- [Firebase Admin setup](https://firebase.google.com/docs/admin/setup)
- [Verify Firebase ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens)
- [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Cloud Run container contract](https://cloud.google.com/run/docs/container-contract)

## Validation and handoff

Backend foundation implemented in `online-server`: HTTP commands, verified Firebase
identity, shared-engine validation, cryptographic dice, Firestore transactions and
request receipts, versioned snapshots, restricted new collections, and container recipe.

Verified on 28 September 2026:
- 19 backend controller/HTTP/codec tests passed, including full free-for-all and team
  games with snapshot round trips, concurrent actions and lost-response retries.
- Existing engine suite: 73 tests passing (Gradle reported the suite up to date).
- `:online-server:installDist` and Android `:app:compileDebugKotlin` passed.
- Packaged server started on Java 17 with dummy local-emulator credentials: health
  returned 200 and an unauthenticated room request returned 401; process shut down.
- Documentation links and whitespace checks passed.

Open verification: Firestore integration tests compile but did not run because the
139 MB emulator runtime download repeatedly timed out. Docker build could not fetch
its base image: Docker Hub was unreachable and the public ECR mirror connection reset.
These are unresolved checks, not passing results. Run the commands in the server README
once network access is reliable. Real Firebase token verification and two-device play
also remain unverified; HTTP unit tests inject a test identity verifier.

## Android migration results — 28 September 2026

Implemented increment 2:
- Shared `online-protocol` model/codec with the backend; no Firebase Admin dependency
  is included in Android. Engine rules and offline persistence were not changed.
- Automatic anonymous identity, HTTPS command transport, refresh-on-401, and room
  snapshot subscription using member-only `authoritativeRooms` reads.
- Durable active-room and pending-request recovery in an atomic no-backup file. Pending
  writes survive timeouts, process death and invalid responses with the same request ID.
  Definitive rejections clear them; 401/408/429/5xx and network errors retain them.
- Revisions apply monotonically, pending actions block new input, and cached Firestore
  snapshots do not enable moves. Live-listener failures show reconnection/retry controls.
- Tabletop board/corner dice, tested pawn paths and capture effects, sound/volume/speed,
  reduced-motion and haptic preferences. Reconnect gaps snap to confirmed state rather
  than animating invented moves. Explicit event counts support the bounded server log.
- Team and free-for-all lobby; eight-character codes; active room resume; transactional
  waiting-room leave, host transfer and closed empty rooms. Receipt retries after leaving
  cannot reveal subsequent room state or new members.
- Online navigation in release. Configure the public service origin using
  `-PonlineApiUrl=https://YOUR-SERVICE.run.app`; unset configuration displays an online
  unavailable message without initiating guest authentication. No deployed URL was invented.
- Removed Android legacy move-relay code. Checked-in rules close legacy rooms/moves to
  clients; deploy rules/backend/app together only after authorization. Data is preserved.

Verification: 122 Android unit tests passed, including eight new recovery tests. Debug
and signed/minified release APKs built, with Play upload-certificate verification and
release vital lint passing. All 23 backend tests passed, including host-transfer and
receipt privacy regressions. Device/visual/audio tests were not run.

Artifacts: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`. Both currently have an empty service
URL and therefore offer offline play plus the online unavailable screen. A configured,
authorized backend and real two-device verification are required for online play.

Remaining: emulator integration and container checks from the previous handoff are still
open (no complete cached runtime was available), as are real Firebase identity validation,
live reconnect/process-death scenarios on devices, release/device review and deployment.
Waiting-room leave is complete; active-game resignation/disconnect deadlines, abuse/rate
limits, cleanup and durable online history are the next implementation increment.
No backend/rules deployment or publication was performed.

## Emulator and container verification — 29 September 2026

The runtime download and container-image network blockers above are resolved.
Firestore emulator 1.21.0 (138,093,843 bytes) is stored in the default Firebase cache;
its size and SHA-256 matched the installed Firebase CLI metadata.

The first real integration run exposed a test setup bug: `NoCredentials` selected
an SDK channel using the default public endpoint rather than the configured emulator.
The tests now pin both endpoints to numeric loopback and use SDK emulator-only owner
credentials, without loading application default credentials. The demo-project guard
remains in place. Separate unsigned guest REST requests continue to test client rules.
Backend storage exceptions retain their original causes for internal diagnosis; HTTP
responses retain only the safe error code/message, covered by a new regression test.

Verified:

- Three Firestore integration tests passed: concurrent joins/rolls across independent
  controllers, durable receipt recovery and request/revision conflicts; member reads
  versus outsider/unauthenticated reads and forbidden client/legacy writes; leave/host
  transfer with immediate rule-level access revocation and private receipt retries.
- All 24 server unit tests passed. `:online-server:installDist` succeeded.
- `docker build -t failure-ludo-online:local online-server` succeeded using the existing
  `eclipse-temurin:17-jre` Dockerfile. The resulting image started with dummy local-only
  credentials, respected custom `PORT=8090`, returned health 200 and unauthenticated
  room-read 401, ran as UID 10001 and stopped on SIGTERM (exit 143, no forced kill).
- A separate container check rejected emulator configuration when `K_SERVICE` was set.
  Temporary smoke-test containers were removed. The local image remains available.

These checks do not validate real Firebase token verification, a deployed service or
complete Android multiplayer. No Android code changed and APK/device checks were not
repeated. No backend, rules or app deployment/publication occurred.

Next implementation: active-game resignation/disconnect deadlines, abuse/rate limits,
cleanup and durable online history. Real identity, two-device reconnect/process-death,
offline-without-internet and release/device checks remain open before rollout.


## Resignation and team control — 29 September 2026

Implemented the next lifecycle increment. The user chose teammate continuation when
one player resigns from a team game. `RESIGN` is authenticated, revision-checked and
persisted atomically with its idempotent receipt. Members retain read access to watch,
but resignation permanently removes command authority; joining again does not undo it.

- Team: the remaining teammate controls both colors on their existing turns. A pending
  dice roll/pawn choice, consecutive-six count, paired pawns and shared-dice unlock state
  are preserved. Before unlock, each color's turn still moves only its own pawns. When
  both teammates resign, the opposing team wins.
- Free-for-all: the pure engine forfeiture transition makes the resigned seat inactive.
  Its pawns stop occupying the board. The next active player gets a fresh turn when
  needed; an unaffected current player keeps their dice and receives recalculated legal
  moves. The last remaining player wins. Existing offline flows never call forfeiture.
- Android: an explicit confirmation explains the consequence and is tied to the displayed
  revision. The same request survives ambiguous failures/process death. Resigned players
  can watch or start a new room after confirmation. Turn controls and animation highlight
  follow the controlled color, including a teammate's color. Back/Lobby still preserves
  a playing seat; connection loss alone does not resign.
- Snapshot protocol 2 / rules `2026-09-29` carries `members[].resigned`. The decoder accepts
  legacy protocol 1 / rules `2026-09-23` with no resigned members; subsequent writes use
  v2. Local journal upgrades retain pending request IDs. Other version pairs fail closed.
  Backend/app rollout must be coordinated, with older server revisions removed from
  room traffic; older apps cannot read v2. No live data migration or deployment was run.

Validation: 79 engine tests, 33 server unit tests, four Firestore emulator integration
tests and 124 Android unit tests passed. Coverage includes pending-turn handoff, rejected
commands from resigned players, no early team-dice unlock, competing resignations,
receipt recovery after further play, snapshot round trips and saved-journal upgrades.
Both debug and signed/minified release APKs built; upload-key verification and release
vital lint passed. The packaged server distribution also built. The combined verification
finished successfully in Gradle, then Firebase CLI timed out during shutdown; a focused
emulator rerun passed and shut down cleanly. Device/visual/audio and real two-device
testing were not run. Both APKs still have an empty backend URL, so live online play
is unavailable until an authorized service is configured/deployed. The existing local
Docker image predates this increment; rebuild it from the updated distribution before use.

Next: disconnect grace periods/turn deadlines, abandoned-room cleanup, abuse/rate limits
and durable online history. Real Firebase identity and release/device checks remain open.
No backend/rules deployment or app publication occurred.

## Ten-second actions, AFK assistance and avatar countdown — 29 September 2026

User decisions: allow 10 seconds separately for each roll and pawn choice, including
bonus rolls; use a bot only for each missed action; keep a two-minute continuous AFK
limit. Add a visible countdown over the player profile, similar in function to Ludo Club.

Implemented:
- New rooms persist a 10-second action policy and two-minute AFK policy. The server
  evaluates time inside each transaction attempt and refuses client timestamps, dice
  and target identities. Each accepted roll/move resets the next action window.
- `CHECK_TIMEOUT` performs one server bot action using the existing engine/heuristic
  selector after an action deadline. The human gets the next action window. AFK begins
  at the start of the first missed action window; bot actions do not reset it. At two
  minutes the seat forfeits using the existing FFA/team handoff rules, even if another
  player currently has time left. Lost responses reuse the same request receipt.
- A human roll/move or explicit `RETURN` ("I'm back") clears AFK before the two-minute
  deadline. Returning during one's own controlled turn preserves dice/pawns and gives
  a fresh 10-second window; returning during another turn does not extend that timer.
- Android schedules checks from server-time samples and elapsed real time (including
  device sleep), recovers pending intentions first and backs off after failed checks.
  There is no continuously running backend timer: if all apps close, checks resume when
  someone returns. No unobserved bot turns are replayed to catch up.
- Online guest profile initials have a shrinking countdown ring. It resets for each
  action, turns amber/red near expiry, follows the active color during team takeover,
  exposes remaining seconds to accessibility and uses discrete updates with reduced
  motion. Text shows action/AFK status and bot assistance. Offline callers retain the
  default layout without online avatars/timers.
- Protocol 3 / rules `2026-09-29-afk` persists deadlines and member AFK start times.
  Known v1/v2 snapshots remain readable and untimed; journals retain pending request IDs.
  Older app/server versions reject v3, so rollout must be coordinated.

Verification: 49 server unit tests, five Firestore integration tests and 128 Android
tests passed (182 total). Coverage includes exact boundaries, fresh bonus-roll timers,
human return, no early forfeiture, AFK expiry between turns, team takeover, concurrent
checks, bot receipt recovery after restart, codec upgrades and countdown synchronization.
The new Android recovery test initially had a JUnit return-type error; it was corrected
and the complete final verification passed. Debug and signed/minified release APKs
built; upload-key verification and release vital lint passed. The server distribution
built and the emulator shut down cleanly. Engine code was unchanged in this increment.
Device/visual/audio, real Firebase authentication and two-device tests were not run.

Artifacts: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`. Both still have no backend URL, so live
online play is unavailable until an authorized backend is configured/deployed. Rebuild
the older local Docker image from the updated distribution before using it.

Remaining: abandoned-room cleanup, rate/abuse limits, durable online history, production
operations and real-device/release checks. No backend/rules deployment or publication.

## Request limits and abandoned rooms — 2 October 2026

Implemented on `feat/online-testing`:

- Firestore-backed per-identity HTTP quota: 120 authenticated requests per 60-second
  window, including reads and rejected commands. Room creation has a separate quota
  of 10 accepted new rooms per hour, charged atomically with the room and receipt.
  Receipt recovery bypasses the creation quota. HTTP 429 carries `Retry-After`; Android
  preserves its pending request ID and displays the server's wait message.
- Private rate-counter documents are denied to clients. Quota state survives service
  restarts and is shared across instances. Limits do not address anonymous-account
  churn, direct snapshot-read costs or unauthenticated traffic; edge protection and
  cost monitoring remain deployment work.
- Server-only room metadata sets a 24-hour inactivity deadline for waiting/playing
  rooms. Revision-changing commands renew it; reads, retries and repeated member JOINs
  do not. Legacy documents acquire metadata on the next accepted write. Finished
  results do not expire.
- `online-server --cleanup` previews at most 100 due rooms; `--cleanup --apply` closes
  a bounded batch. Every closure rechecks current state in a transaction and retains
  membership, the last game snapshot and all receipts. It creates no winner and does
  not change the two-minute AFK rule. This is logical closure, not data deletion.
- Android handles closed waiting rooms, shows closure during a game, disables moves
  and offers a new room in the lobby. Offline behavior and snapshot protocol remain
  unchanged. Both service and maintenance-job deployment guards reject emulator env.

No backend/rules deployment, maintenance schedule, TTL policy or publication was
performed. Remaining work: durable online history and retention/deletion policy,
production operations, real Firebase authentication, two-device play and device/release
checks. Existing APKs still need an authorized backend URL for live online play.

Verification: 50 server unit tests, nine Firestore integration tests and 128 Android
unit tests passed (187 total). Emulator coverage includes cross-instance quota races,
window boundaries, quota-safe receipt recovery, bounded dry runs, renewed-activity
races, retained closed snapshots/receipts and finished/legacy-room preservation.
The final server distribution, debug APK and signed/minified release APK built; release
vital lint passed. CLI checks rejected invalid arguments and emulator configuration in
both deployed service/job environments. Documentation links and staged whitespace
checks passed. No device review, real Firebase authentication or two-device tests ran.
The local Docker image was not rebuilt in this increment.
