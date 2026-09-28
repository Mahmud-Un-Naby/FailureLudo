# Plan 007 — Online Multiplayer

## Architecture update — 28 September 2026

[Plan 011](011-authoritative-online-release-plan.md) supersedes the client-authoritative
move relay below. Online will use the shared Kotlin engine on a Cloud Run backend,
server-generated dice, transactional commands and confirmed snapshots. This historical
plan remains context for retained Android UI and known gaps.

## Release scope update — 28 September 2026

Online Android development now targets the release app alongside offline play, per
the user's latest direction. The debug-only/private-testing limitation below describes
the current implementation, not the release target. See the
[current release handoff](../docs/online-testing.md) for compatibility gaps and scope.
Offline play remains independent of authentication/network access; Google sign-in is
not required for online guests. Publication and backend deployment still require
explicit authorization. Web/parity work remains paused. The synchronization,
authorization, and tabletop integration priorities below remain outstanding.

## Resumption handoff — 28 September 2026

The user explicitly resumed online Android work, then requested consolidation of the
branches. `main` now carries the offline improvements; continue online development on
`feat/online-testing` and retire `feat/offline-improvements`. Local startup, saves, and
offline play remain available without authentication.
The historical checkboxes below predate retained implementation and are not a current audit.

First increment: expose **Play online · Preview** in debug builds only, with optional
Google/guest authentication, create/join private rooms, waiting room, and the retained
online board. Firebase-backed ViewModels are destination-scoped. Online results use
online game state rather than the offline session's win screen. System back follows
room-leave/game-exit handling. Release navigation remains offline while the backend
and synchronization are prepared for real use.

Validation: `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` passed.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Two-device play, Firebase
provider configuration, deployed rules, and visual review still need verification.

Next implementation priorities before enabling online releases:
- Replace unrestricted authenticated room/move writes in `firestore.rules` with
  membership/host/turn ownership checks and emulator regression coverage.
- Fix optimistic move submission: failed writes currently advance local state without
  recovery; add ordered, idempotent confirmation/retry and reconnect handling.
- Validate joins/starts against fresh transactional room state and player counts.
- Handle room/move listener errors explicitly and provide recovery instead of stalled play.
- Define resignation/disconnect behavior; leaving an active game currently does not
  resolve its outstanding turns. Waiting-room leave remains best-effort.
- Carry the reviewed tabletop presentation into the online board.

Public matchmaking and web changes are outside this first Android increment. No Firebase
rules, hosting, or production release are deployed by restoring this development entry point.

## Decisions (do not re-debate these)
- **Backend:** Firebase (managed, no server to maintain)
- **Web frontend:** React, hosted on Firebase Hosting
- **Players:** Android app + web browser can play together in the same game
- **Matchmaking:** both room codes (friends) and random public matching
- **Auth:** Google Sign-In for registered users + Guest mode
- **Platform badges:** show Android/browser icon next to player name (easy, keep it)
- **Watch mode:** share the game URL → opens as spectator if all player slots taken
- **Game history:** deferred to a later plan

---

## Architecture

```
[ Android App ]          [ Web Browser (React) ]
      |                            |
      +-------- Firebase SDK ------+
                    |
     [ Firestore — game rooms & moves ]
     [ Firebase Auth — Google Sign-In ]
     [ Cloud Functions — matchmaking  ]
     [ Firebase Hosting — React app   ]
```

### Game room document (Firestore)
```json
{
  "roomCode": "XK92P",
  "status": "waiting | in_progress | finished",
  "players": [
    { "uid": "abc", "name": "Mahmud", "platform": "android", "color": "RED" }
  ],
  "spectators": ["uid1", "uid2"],
  "moves": [
    { "playerUid": "abc", "diceRoll": 4, "pawnId": "R1", "timestamp": 1234 }
  ],
  "currentTurn": "abc",
  "winner": null,
  "createdAt": timestamp,
  "updatedAt": timestamp
}
```

Both Android and web clients listen to this document in real time.
A move = append to `moves[]`. All clients apply moves to their local deterministic GameEngine.

### User profile document (Firestore)
```json
{
  "uid": "abc",
  "name": "Mahmud",
  "platform": "android",
  "isGuest": false,
  "createdAt": timestamp
}
```

### Guest vs Registered
| Feature | Guest | Registered |
|---|---|---|
| Play | Yes | Yes |
| Watch | Yes | Yes |
| Room codes | Yes | Yes |
| Random matchmaking | Yes | Yes |
| Username | Auto (Guest#4821) | Chosen / Google display name |
| Game history | Lost on session end | Saved (future plan) |
| Reconnect interrupted game | No | Yes |
| Stats / leaderboard | No | Yes (future) |

---

## Phase 1 — Firebase project setup
- [ ] Create Firebase project at console.firebase.google.com
- [ ] Enable Firestore database (start in test mode)
- [ ] Enable Firebase Auth → turn on Google Sign-In provider
- [ ] Enable Anonymous Auth (for guest mode)
- [ ] Register Android app in Firebase console → download `google-services.json`
- [ ] Place `google-services.json` in `app/`
- [ ] Add `google-services` plugin to `app/build.gradle.kts`
- [ ] Add Firebase BOM + Firestore + Auth dependencies to `app/build.gradle.kts`
- [ ] Verify app builds with Firebase dependencies

---

## Phase 2 — Android authentication
- [ ] Create `AuthRepository.kt` — wraps Firebase Auth (sign in with Google, sign in as guest, sign out, get current user)
- [ ] Create `UserProfileRepository.kt` — reads/writes user profile document in Firestore
- [ ] On first launch: show auth screen with "Continue with Google" and "Play as Guest"
- [ ] Guest sign-in: Firebase Anonymous Auth → auto-generate username `Guest#XXXX` → write profile doc
- [ ] Google sign-in: use GoogleSignIn + Firebase credential → write profile doc
- [ ] After auth: navigate to HomeScreen (existing)
- [ ] Show current username somewhere on HomeScreen
- [ ] "Sign in" nudge shown to guests (dismissible)

---

## Phase 3 — Android lobby screen
- [ ] Add "Play Online" button to HomeScreen → navigates to OnlineLobbyScreen
- [ ] Create `OnlineLobbyScreen.kt` with three options: Create Room / Join Room / Find Match
- [ ] **Create Room flow:**
  - [ ] Generate 6-char alphanumeric room code (client-side, check uniqueness in Firestore)
  - [ ] Write new game room document to Firestore
  - [ ] Navigate to WaitingRoomScreen showing the code + waiting for opponent
- [ ] **Join Room flow:**
  - [ ] Text field for room code entry
  - [ ] Look up room in Firestore → validate it exists and has an open slot
  - [ ] Add self to `players[]` in the room document
  - [ ] Navigate to WaitingRoomScreen
- [ ] **WaitingRoomScreen:**
  - [ ] Show room code prominently with a share button
  - [ ] Show connected players with platform badges
  - [ ] Listen to room document — when `status` changes to `in_progress`, navigate to game
  - [ ] Host can tap "Start Game" (minimum 2 players)
- [ ] Starting game: host sets `status = in_progress`, assigns colors to players

---

## Phase 4 — Android game sync (network layer)
- [ ] Create `OnlineGameRepository.kt` — listens to moves subcollection, writes moves
- [ ] Create `OnlineGameViewModel.kt` — bridges OnlineGameRepository with existing GameEngine
- [ ] Key design: GameEngine stays pure/local. ViewModel feeds opponent moves into it when Firestore listener fires.
- [ ] Local player move flow: player taps pawn → ViewModel validates turn is theirs → writes move to Firestore → does NOT apply locally yet → waits for Firestore confirmation → applies move
- [ ] Opponent move flow: Firestore listener fires with new move → ViewModel applies it to GameEngine → UI updates
- [ ] This ensures both clients always see the same sequence of moves
- [ ] Create `OnlineGameBoardScreen.kt` (wraps existing GameBoardScreen or reuses it via a flag)
- [ ] Show opponent names + platform badges on the board
- [ ] Disable controls when it is not the local player's turn
- [ ] Handle game end: when `winner` is set in Firestore → show WinScreen
- [ ] Handle disconnect: if a player's connection drops for >60s, mark them as disconnected in the room doc

---

## Phase 5 — React web app
- [x] Bootstrap: Vite + React + TypeScript (`ludo-web/`)
- [x] Add Firebase Web SDK: `npm install firebase`
- [ ] Register web app in Firebase console → get `appId` → add to `ludo-web/.env.local`
- [x] Create `firebase.ts` — initialize app, export `db` (Firestore) and `auth`
- [x] **Auth screens** (mirrors Android Phase 2):
  - [x] Google Sign-In button
  - [x] "Play as Guest" button
  - [x] Same anonymous auth + profile write logic
- [x] **Lobby screen** (mirrors Android Phase 3):
  - [x] Create Room / Join Room buttons
  - [x] WaitingRoom component with share link (copies spectator game URL)
- [x] **Game board** (new, no existing code to reuse):
  - [x] Draw board on SVG
  - [x] Replicate board layout: 15×15 grid, safe squares, home columns, center
  - [x] Pawn components with platform badges
  - [x] Firestore listener drives board state (same move-relay logic as Android)
  - [x] Disable controls when not local player's turn
- [x] **Spectator mode:**
  - [x] If user is not in room when opening game URL → set `isSpectator = true`
  - [x] `?spectate=1` query param also forces spectator mode (for share links)
  - [x] Spectators see the board updating but have no controls
  - [ ] Show spectator count on screen (deferred)
- [ ] Deploy to Firebase Hosting: `npx firebase-tools deploy --only hosting` (needs `firebase login` first)
- [ ] Connect custom domain in Firebase Hosting console

---

## Phase 6 — Random matchmaking (Cloud Function)
- [ ] Set up Firebase CLI: `npm install -g firebase-tools` → `firebase init functions`
- [ ] Write `matchmakingQueue` Cloud Function (Node.js/TypeScript):
  - [ ] Triggered when a document is written to `/matchmaking_queue/{uid}`
  - [ ] Queue doc: `{ uid, name, platform, timestamp, playerCount: 2|4 }`
  - [ ] Function checks queue for enough players of same `playerCount`
  - [ ] When match found: create game room, write room ID back to each queued player's doc, delete queue entries
- [ ] Android "Find Match" flow:
  - [ ] Write self to queue → listen to own queue doc for `matchedRoomId` field → navigate to WaitingRoom
  - [ ] Cancel button: delete own queue doc
- [ ] Web "Find Match" flow: same logic
- [ ] Timeout: if in queue >2 min with no match, show "No opponents found, try again"
- [ ] Deploy function: `firebase deploy --only functions`

---

## Phase 7 — Watch mode polish
- [ ] Every game screen shows a "Share" button → copies `yourdomain.com/game/{gameId}` to clipboard
- [ ] On Android: share intent (WhatsApp, SMS, etc.)
- [ ] Deep link on Android: opening the URL when app is installed → opens game in app as spectator
- [ ] Web: direct URL works already (from Phase 5)
- [ ] Show live spectator count in-game
- [ ] Spectator chat (optional, defer if complex)

---

## Phase 8 — Game history (deferred)
- See plan 005 for history notation work already done.
- Online game history will be covered in a future plan once Phases 1–7 are stable.

---

## Verification checkpoints
- After Phase 1: app builds and connects to Firebase without crash
- After Phase 2: can sign in as guest or Google, profile appears in Firestore console
- After Phase 3: two Android devices can join the same room by code, see each other in waiting room
- After Phase 4: full game plays end-to-end between two Android devices, moves sync correctly
- After Phase 5: Android player and browser player can play a full game together
- After Phase 6: two devices both tap "Find Match" and get paired automatically
- After Phase 7: opening the share link in a browser shows the live game as spectator
