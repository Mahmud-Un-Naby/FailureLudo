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

1. **Backend foundation (current implementation)**
   - Standalone `online-server` JVM module depends on the existing engine.
   - Authenticated HTTP create/join/start/roll/move/read endpoints, including guests.
   - Cryptographic server dice; engine validation for pawn and home-entry selection.
   - Atomic Firestore room + request receipt writes, expected revisions, idempotent retries.
   - Versioned snapshots and separate collections from the old client-written preview.
   - Tests for authority, concurrency, retry, serialization and HTTP boundaries;
     emulator checks for persistence and access rules; container packaging.
2. **Android migration and release entry**
   - Create/reuse anonymous identity automatically; remove mandatory auth-screen step.
   - Replace legacy room/move writes and local online dice with the HTTP command API.
   - Observe member-readable confirmed snapshots; persist room/request IDs for recovery.
   - Handle lost responses, stale revisions, token expiry and listener failures explicitly.
   - Share tabletop layout, pawn/dice animation and sound/settings with offline play.
   - Include online navigation in release; configure backend URL without debug-only gating.
   - Disable legacy client-write collections as part of coordinated migration.
3. **Complete multiplayer lifecycle and operations**
   - Specify and implement waiting-room leave/host transfer, resignation, disconnect
     grace periods, deadlines and abandoned-room cleanup, with regression coverage.
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

Room snapshots carry protocol and rules versions. Unsupported stored versions fail
closed. The server accepts human free-for-all and four-seat team games through the
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

Next increment is Android migration and release entry (step 2), with the outstanding
emulator/container checks completed before deployment. Android still uses the retained
online preview; adding a backend module alone does not enable authoritative online play
in the app. No cloud resources, backend/rules deployments or publication were performed.
