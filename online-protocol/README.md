# Online protocol

Shared Kotlin/JVM room snapshots for Android and the authoritative server. Depends on
`game-engine`; no Android or Firebase Admin APIs. `RoomCodec` uses the JSON API already
available on Android; the JVM server supplies its own runtime implementation.

Snapshot fields carry protocol and rules versions. Unknown versions fail closed. Keep
new optional wire fields backward compatible, and define a migration before changing
rules or required fields. Codec round-trip and full-game tests run in `online-server`;
Android recovery tests exercise the same codec when saving/restoring pending sessions.
