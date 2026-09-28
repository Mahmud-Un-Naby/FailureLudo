# Failure Ludo online server

Kotlin/JVM HTTP service intended for Cloud Run, using the existing `game-engine`.
This is the first increment of [plan 011](../plans/011-authoritative-online-release-plan.md).
Android has not yet switched from its legacy client-written online preview to this API.
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

The Firebase CLI and Java 21+ (for the current emulator) are required. Emulator tests refuse non-loopback hosts;
ordinary unit tests never run integration tests or access a backend.

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

Start/roll use the same command endpoint:

```json
{"requestId":"unique-request-id-0003","type":"START","expectedRevision":1}
{"requestId":"unique-request-id-0004","type":"ROLL","expectedRevision":2}
```

Move a pawn using the engine's player ID (1–4) and pawn ID (0–3):

```json
{"requestId":"unique-request-id-0005","type":"MOVE","expectedRevision":3,"playerId":1,"pieceId":0,"deferHomeEntry":false}
```

`deferHomeEntry: true` chooses continued circulation when legal. The acting player
always comes from the token and current turn. In team mode the engine determines
whether a teammate's pawn can be moved. No endpoint accepts a dice value.

A write response contains `room`, `acceptedRevision`, and `duplicate`. Read the current
snapshot with `GET /v1/rooms/ABCDEFGH`. Only members can read it. Snapshot fields include
`protocolVersion`, `rulesVersion`, `revision`, `members`, `status`, `game`, and
`lastAction`. The game includes every engine field needed for exact restoration.
The room revision covers lobby changes and individual roll/move commands, not just turns.

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

Existing `rooms`/`moves` collections and their preview rules remain unchanged until
Android migration. They do not grant access to the new authoritative collections.
The new namespace rules are covered by emulator tests but must be deployed separately
with authorization before a real client can use Firestore snapshot subscriptions.

This increment has no leave/resign/timeout policy, matchmaking, permanent action archive,
rate limiting, cleanup or Android transport. Recent game events are limited to 32 for
presentation. These gaps and two-device/release validation are tracked in plan 011.
Receipts must not be deleted while their room can still accept commands. Do not roll
out incompatible rules under the same rules version; migration is a separate release task.
