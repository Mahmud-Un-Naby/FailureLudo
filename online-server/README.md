# Failure Ludo online server

Kotlin/JVM HTTP service intended for Cloud Run, using the existing `game-engine`.
This implements the backend and Android command protocol in
[plan 011](../plans/011-authoritative-online-release-plan.md). Android uses this API
and shares its snapshot model/codec through `online-protocol`.
No backend or rules have been deployed by this change.

## Build and verify

```sh
./gradlew -PserverOnly=true :online-server:test :online-server:installDist :game-engine:test
```

`serverOnly` excludes Android configuration, so an Android SDK is not needed. Java
17 or later is required. The distribution is `build/install/online-server/`.
Unit tests use an in-memory transactional fake only in test sources; the executable
always uses Firestore. HTTP tests bind loopback and inject a test token verifier.

Real Firestore transaction and security-rule tests run against an isolated demo project:

```sh
firebase emulators:exec --only firestore --project demo-failure-ludo-server \
  --config firebase.server-test.json \
  './gradlew -PserverOnly=true :online-server:integrationTest'
```

The Firebase CLI and Java 21+ (for the current emulator) are required. Emulator tests
refuse non-loopback hosts, pin both SDK endpoints to numeric loopback,
and use SDK emulator-only administrator credentials instead of application credentials.
Separate unsigned guest requests exercise the client rules. Ordinary unit tests never
run integration tests or access a backend.

## Run and package

Runtime configuration:

| Variable | Purpose |
| --- | --- |
| `GOOGLE_CLOUD_PROJECT` | Required Firebase/Google Cloud project ID |
| `PORT` | HTTP listen port, default `8080`; binds `0.0.0.0` |
| Application Default Credentials | Workload service identity with required Firestore and Auth permissions |

Use Application Default Credentials for local authorized backend testing; in Cloud
Run, use its attached service account. No credential is embedded in the distribution
or image. Emulator identity/storage environment variables are rejected on Cloud Run.

```sh
online-server/build/install/online-server/bin/online-server
# From repository root, after installDist:
docker build -t failure-ludo-online:local online-server
```

The image runs as an unprivileged numeric user. Its allowlisted Docker context includes
only the built distribution. A container build does not deploy the service.

Before deployment, configure anonymous Firebase Auth, the service account, Firestore
rules, request/instance limits and a deliberate target project. Cloud Run transport
must allow mobile requests to reach the service; Firebase ID tokens are verified by
the application, not used as Cloud Run IAM tokens. TLS is terminated by Cloud Run.
Do not expose this HTTP listener directly on an unencrypted public host.

## API v1

All endpoints except `GET /healthz` require `Authorization: Bearer <Firebase ID token>`.
The Admin SDK verifies the token and checks revocation/disabled accounts. Anonymous
Firebase users are supported; the client must never supply a UID in the body.
POST requests use `application/json`, with a 4 KiB limit; unknown fields are rejected.

Create a room, `POST /v1/rooms`:

```json
{"requestId":"unique-request-id-0001","name":"Guest","maxPlayers":2,"mode":"FREE_FOR_ALL"}
```

`mode` is `FREE_FOR_ALL` (2–4 seats) or `TEAM` (four seats). A server-generated room
code has eight characters. The host gets red; subsequent joins fill vacant colors
in engine order. Names are 1–32 characters after trimming.

Join, `POST /v1/rooms/ABCDEFGH/commands`:

```json
{"requestId":"unique-request-id-0002","type":"JOIN","name":"Second guest"}
```

Start/roll/leave/resign use the same command endpoint:

```json
{"requestId":"unique-request-id-0003","type":"START","expectedRevision":1}
{"requestId":"unique-request-id-0004","type":"ROLL","expectedRevision":2}
```

`LEAVE` is accepted only while waiting and also requires `expectedRevision`. The host
transfers to the first remaining member; the last exit closes the room. The response
contains a sanitized closed-room acknowledgement with no members or game state, even
for retries. Old create/join receipts do not let a former member read later snapshots.

Resign from an active game, on any turn:

```json
{"requestId":"unique-request-id-0006","type":"RESIGN","expectedRevision":3}
```

The verified caller resigns their own membership permanently. In free-for-all their
seat/pawns leave play and the last remaining player wins. In team mode their teammate
controls both colors on the existing turns, including any pending pawn choice; normal
shared-dice unlock rules remain unchanged. If both teammates resign, their team loses.
`members[].resigned` records the decision. Resigned members retain read access to watch,
but cannot roll/move or regain control by joining again. Duplicate resignation requests
return the latest snapshot and original receipt; keep the same request ID after timeout.

Move a pawn using the engine's player ID (1–4) and pawn ID (0–3):

```json
{"requestId":"unique-request-id-0005","type":"MOVE","expectedRevision":3,"playerId":1,"pieceId":0,"deferHomeEntry":false}
```

`deferHomeEntry: true` chooses continued circulation when legal. The acting controller
comes from the token and current turn, including a remaining teammate controlling a
resigned teammate's color. In team mode the engine determines
whether a teammate's pawn can be moved. No endpoint accepts a dice value.

A write response contains `room`, `acceptedRevision`, and `duplicate`. Read the current
snapshot with `GET /v1/rooms/ABCDEFGH`. Only members can read it. Snapshot fields include
`protocolVersion` (3), `rulesVersion` (`2026-09-29-afk`), `revision`, `members`, `status`, `game`, and
`lastAction`. The game includes every engine field needed for exact restoration.
The room revision covers lobby changes and individual roll/move commands, not just turns.
`lastAction.eventCount` identifies the new events at the end of the bounded game event
log, so repeated moves still trigger feedback after earlier events have been trimmed.

New rooms persist `actionTimeoutMillis=10000`, `afkTimeoutMillis=120000`, and an
`actionDeadlineAtMillis` once play starts. Each accepted roll/move starts the next
10-second window, including bonus rolls. A member's `afkSinceMillis` records the
beginning of their first missed action window. Bot actions never reset this timer.

Any member may ask the server to resolve one due action:

```json
{"requestId":"unique-request-id-0007","type":"CHECK_TIMEOUT","expectedRevision":3}
```

The server checks its own clock inside the Firestore transaction. Before a deadline,
this returns `409 TIME_REMAINING`. At an action deadline it rolls or chooses a legal
pawn with the shared engine's heuristic bot (`BOT_ROLL` / `BOT_MOVE`). The next action
again allows the human 10 seconds. At two minutes of uninterrupted AFK it forfeits the
member (`TIMEOUT`), applying the same team handoff/FFA rules as resignation. An expired
AFK member is resolved first even if another player's action still has time remaining.
Late human commands return `409 TURN_EXPIRED`; old accepted receipts still replay safely.
The caller cannot supply a timestamp, dice value or target player for automatic actions.

Human rolls/moves clear their controller's AFK status. A returning player can also send:

```json
{"requestId":"unique-request-id-0008","type":"RETURN","expectedRevision":4}
```

This clears AFK before its two-minute expiry and gives a fresh action window only if
it is that player's controlled turn. It never changes dice, pawns or another player's
deadline. Already-active/forfeited players cannot repeatedly reset timers this way.
HTTP responses include `serverTimeMillis`; Android estimates elapsed time using a
monotonic clock that includes device sleep, independently of the phone's wall clock.
The countdown ring is a display aid; only the server confirms automatic transitions.

Deadline checks reuse the same durable command journal and receipts as moves. An online
Android session requests checks when due, backing off after network errors. There is no
in-memory server timer or continuously running VM. If all apps close, resolution waits
for someone to return. This increment does not deploy a scheduler or clean abandoned rooms.

Reuse the same request ID and payload after timeout, connection loss or HTTP 503.
The receipt and room commit atomically, so a lost response cannot cause a second roll
or move. Reusing an ID for a different command returns `409 REQUEST_ID_REUSED`.
A duplicate returns the latest snapshot and the original accepted revision; clients
must never replace a newer local confirmed snapshot with an older one.

A `409 STALE_REVISION` requires reloading the room before choosing a new action and
request ID. A `401` requires refreshing/reestablishing Firebase identity. Other errors
include `403 NOT_A_MEMBER`, `403 NOT_YOUR_TURN`, `409 WRONG_PHASE`, `409 ROOM_FULL`, and
`400 ILLEGAL_MOVE`/`ILLEGAL_ROUTE`. Error bodies contain `error` and a safe `message`.

## Persistence and limits of this increment

`authoritativeRooms/{code}` stores a JSON `snapshot`, membership array `memberUids`,
and `revision`. Clients may subscribe to member-readable room documents; all writes
are server-only. `authoritativeRequests/{hash}` contains private durable receipts.
No room state is kept in service memory. Request fingerprints and IDs are scoped to
the verified UID. A transaction callback reuses its candidate roll if Firestore retries.

Checked-in rules now deny client access to legacy `rooms`/`moves` collections. Data is
retained. Deploy the rules as part of the coordinated backend/app migration; older
preview clients (including the paused web client) will no longer write those rooms.
Emulator regression tests passed on 29 September 2026, including these denials,
concurrent commands, durable receipt retries and membership revocation after leaving.
The local container build and startup checks also passed; see plan 011 for scope.
Nothing has been deployed.

Android build configuration:

```sh
./gradlew :app:assembleDebug :app:assembleRelease -PonlineApiUrl=https://YOUR-SERVICE.run.app
```

Use the actual authorized service origin and the matching Firebase project. The property
accepts HTTPS origins only, with no path, credentials, query or fragment. Without it,
release and debug still show online entry but explain that online play is unavailable.
The app creates/reuses a guest session automatically and saves pending commands before
sending, using an atomic no-backup journal bound to UID and origin. It retries ambiguous
writes with the same request ID, refreshes an expired token once, and accepts only
monotonically newer confirmed state. No backend token or private credential is stored.

Matchmaking, permanent action archive, rate limiting and abandoned-room cleanup are not
implemented. Returning to the lobby keeps a seat but does not pause action/AFK timers.
After confirmed forfeiture, the Android lobby offers watching or starting a new room.
Recent game events are limited to 32 for presentation. Real authentication, two-device
play and device/release validation remain open. Receipts must not be deleted while
their room can still accept commands.

Snapshot v3 adds action/AFK deadlines. The new server/app can decode v1 snapshots with
rules `2026-09-23` and v2 with `2026-09-29`; existing matches remain untimed and retain
resignation state. Every subsequent write uses v3. Android retains pending request IDs
while upgrading its local journal. Unsupported version pairs fail closed. Older apps
and servers cannot read v3: coordinate backend/app rollout with no older server revision
receiving room traffic. No stored room or receipt is deleted by this change.
