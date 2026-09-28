# 010 — Fresh offline Android design plan

Status: user-directed board, corner dice, pawn, and capture refinements implemented; original “faaah” capture audio restored; device review pending.
Scope: native Android only, on `feat/offline-improvements`.
Goal: [perfect, release, and play the offline app first](009-offline-android-redesign-goal.md).

## 1. Start from the desired experience

Treat this as a new game presentation. The current layouts, board renderer, pawn drawings,
colors, dice effects, sounds, and screen composition are replaceable. Existing component APIs
and file organization must not dictate the new design. Reuse a piece of presentation code only
if it serves the chosen design well.

Keep the game's identity in its rules and play: local friends, bots, teams, pairs, captures,
and meaningful choices. Preserve the rule contract in [game-rules-live.md](../docs/game-rules-live.md),
saves, history/replay, and undo/redo. A new rendering and animation system can sit above that
foundation. This is not a game-rule rewrite.

The first deliverable should be a convincing game-screen prototype, not a reskin of every screen.

## 2. Recommended direction: modern tactile tabletop

A straight overhead board with sculpted pieces and subtle material depth. The board should
feel like a carefully made physical object, while the interface stays light and readable.
Depth comes from controlled highlights, contact shadows, bevels, and movement. All track cells
remain equally legible and easy to touch.

| Direction | Character | Tradeoff |
| --- | --- | --- |
| **Modern tactile tabletop — recommended** | Ivory board, saturated enamel pawns, ink-colored surround, precise markings, soft shadows | Requires careful material and motion consistency |
| Playful arcade | Bright flat surfaces, rounded chunky pieces, springy motion, expressive effects | More energetic; effects need restraint during long games |
| Quiet tournament | Very clean board, flat tokens, minimal effects, compact information | Excellent clarity, less of a physical toy feeling |

The recommendation is a proposal, not an approved requirement. Build one representative
screen for the chosen direction before expanding. If uncertainty remains, compare two static
compositions of that screen; do not build three complete themes.

### Proposed art direction

- Deep ink/teal playing surface behind a light ceramic-like board.
- Distinct red, blue, amber, and green pawn families; tune all four together rather than
  copying the old palette. Use light/dark text variants according to the background.
- One consistent light source, soft contact shadows, restrained surface gradients.
- A small, consistent symbol for each player, repeated on pawn, player panel, and home area.
- Clear typography with a compact display treatment for the title and highly readable labels.
- A custom mark and icon family; emoji are not the primary branding or interface artwork.
- Decorative texture stays subtle and away from track markings and small text.

## 3. New gameplay composition

The board is the visual center. Players should immediately understand who is playing,
whose turn it is, the die result, and what they can interact with.

### Portrait phone

1. Slim header: match mode and a menu for secondary controls.
2. Compact upper player rail, aligned with the upper home areas.
3. Large square board with a modest frame.
4. Compact lower player rail, aligned with the lower home areas.
5. One short status line; dice live in the four corner player panels (September 24 refinement).

Player panels show a name, color/symbol, human/bot identity, and finished-pawn count.
The active panel gains a clear turn marker. Inactive players remain readable. Team mode
shows the teammate relationship explicitly. Empty seats look intentionally unused.

Each player has a fixed corner die, and only the active player can roll. Its accent and adjacent name identify its owner.
A die result remains attributed to the player who rolled it until the next turn presentation
is ready. No previous result should appear to belong to the next player.

### Landscape and larger screens

Put the board beside the player/status/control area when that preserves a larger usable board.
Size from both available width and height. Keep safe-area insets and system text scaling in mind.
Use extra space for breathing room and legibility, not extra decoration or a stretched board.
The camera stays stable during a match; do not rotate the board on every turn.

Undo, redo, rules, replay tools, and settings remain accessible without competing with the die.
Final placement should follow prototype testing, especially one-handed use and pass-and-play.

## 4. Board and pawn design

### Board

- Draw a clean, exact track with deliberate line weights and spacing.
- Give home areas large simple shapes, four well-spaced pawn docks, and a player symbol.
- Clearly distinguish colored entry cells, safe cells, home lanes, and the finish area.
- Use a consistent safe-cell symbol and small direction marks where they clarify the route.
- Make the center a designed finish destination, with completed-pawn progress shown in player panels.
- Keep decoration subordinate to legal cell boundaries and pawn readability.
- Preserve the agreed topology and clockwise route regardless of visual treatment.

### Pawns

Design one recognizable silhouette: a rounded head, tapered body, stable base, and contact shadow.
Create a consistent set of states: resting, selectable, pressed, moving, captured, and finished.
Selectable pawns pulse gently, with a small base highlight. Idle pawns stay still; reduced motion keeps only the steady highlight.

Pairs must visibly read as linked. A pair plus an independent single must remain distinguishable.
Mixed-color teammate pairs need both identities visible. Crowded safe squares use a compact,
readable grouping and an expanded chooser when needed; never shrink pieces until they are useless.

Touch selection uses forgiving cell/nearest-piece resolution. Ambiguous touches open a chooser
with actual pawn/group previews and player names. Do not blindly enlarge overlapping hit circles.
Provide accessible action labels and an equivalent way to choose legal moves.

Keep manual destination counting unless the user changes that gameplay preference. Home-path
choice cards can illustrate the two routes without automatically revealing calculated landing cells.

## 5. Motion: design one coordinated turn sequence

Motion communicates the move, then gets out of the way. These are starting timing ranges to
validate on a device, not fixed requirements.

| Moment | Proposed treatment | Initial timing |
| --- | --- | --- |
| Die roll | Brief lift, convincing tumble, contact, readable settled face | 450–650 ms total |
| Pawn step | Higher hop, small landing rebound, squash and moving shadow | 130 ms per cell |
| Landing | Small settle, one synchronized contact sound | 80–120 ms |
| Capture | Local impact/recoil, then reverse along the track to the original dock without hops | 160 ms hold, 80 ms per return cell; 440 ms overlapping impact effect |
| Finish | Pawn arrives, progress updates, localized celebration | 350–550 ms |
| Turn handoff | Active marker transfers after the move resolves | 120–180 ms |

Use a distinct roll event to trigger dice animation, including repeated equal results.
The final die face comes from the engine. Animation must never select or modify the outcome.

### Dice appearance and roll choreography

Give the die a physical identity: rounded bevels, recessed dark pips, consistent face numbering,
and a soft contact shadow. A shallow tray carries the active player's accent color while the
face remains easy to read. Keep the touch area comfortably larger than the artwork, targeting
at least 48 dp. This is proposed art direction, pending the gameplay prototype.

Recommended standard roll: approximately 600 ms, with these prototype timings:

| Phase | Timeline after an accepted roll | Treatment |
| --- | --- | --- |
| Lift | 0–80 ms | Die rises slightly; shadow softens and separates |
| Tumble | 80–380 ms | A few rotations with coherent visible faces and a short rattle |
| Impact and bounce | 380–500 ms | Contact with the tray, one diminishing rebound, synchronized contact sound |
| Settle | 500–600 ms | Small final wobble, stable result, crisp contact shadow |
| Result hold | Until selection or automatic handoff | Final pips remain readable and attributed to the player who rolled |

On initial touch, show immediate press feedback. A canceled gesture restores the ready state.
The accepted roll starts the sequence once; rapid taps cannot create additional rolls.

- Prototype a lightweight projected cube with coherent faces and lighting. Consider a dedicated
  3D die only if the chosen appearance needs it; a full 3D board is not required.
- Author several equally short tumble paths, with variation independent of the result. Finish
  in an orientation that exposes the engine's chosen value; do not settle on the wrong face
  and then replace its pips.
- Keep pre-landing motion, sound, and duration from revealing the outcome early. Extra-roll
  feedback follows the settled result and actual rules, including no-move and third-six cases.
- Keep the animation inside the tray so it never obscures pawns or changes the layout.
- Distinguish ready, rolling, and settled states. Use a quiet ready marker rather than a perpetual bounce.
- Synchronize a single main haptic contact with landing instead of vibrating throughout the roll.
- Preserve the result long enough to read before automatic no-move/forfeit handoff. Explain the
  reason in the status area. Clear or attribute the previous result when changing players.
- Trigger every roll from its event identity, including consecutive equal values. Animation
  presents the result; it does not determine probability or change the game outcome.
- Offer a quick roll of roughly 300–350 ms and a reduced-motion fade/settle transition.
  Timing choices do not alter rules or skip player decisions.
- Announce only the settled value and next action to accessibility services, not transient faces.

First dice deliverable: an on-device study showing all six results, repeated results, bot rolls,
standard/quick/reduced motion, and sound on/off. Check rapid taps, undo/replay, interruptions,
no-move rolls, third-six forfeits, and a long sequence of rolls for fatigue. Judge touch response,
readability, and audiovisual synchronization before integrating the final die into a full game.

Pawns travel continuously between coordinates. A tied pair moves as a group. Capture effects
happen at contact, and sound follows the visible action. Long returns can accelerate so they
do not make the player wait through an entire track again.

One presentation timeline coordinates dice, movement, sound, effects, and turn handoff.
Input, bot actions, and win navigation respect that timeline. Prevent repeated taps from submitting
multiple actions. Cancel stale effects after undo, restart, replay seek, or screen exit.
On background/resume, reconcile to the saved authoritative state; do not replay a backlog of effects.
Reduced-motion mode uses short transitions and clear static state changes.

## 6. Sound and haptics

Create a cohesive small sound pack for the new materials and mood. Choose or produce new assets
on their merits; there is no requirement to retain the existing pack.

- Dice: a short tumble with a definite settling contact.
- Pawn: a light, non-fatiguing tap, with restrained variation on repeated steps.
- Capture: a compact, satisfying impact; avoid a much louder surprise sound.
- Finish: a short ascending resolution.
- Extra roll and skipped turn: distinct, quiet cues.
- Victory: one brief musical resolution rather than a long blocking sequence.
- Interface: subtle confirmation only where useful.

Audio timing follows the visible sequence. Limit overlapping sounds and avoid clipping.
Keep sound effects and haptics independently controllable, with persistent volume settings.
Use short, purposeful haptics for roll contact, capture, and victory. Music is optional and
lower priority than getting effects right; start with it off if added.
Ship all assets locally. Validate on a phone speaker, headphones, muted audio, and disabled haptics.
Use assets with clear redistribution rights; record their sources or creation provenance.

### Concrete sound sourcing plan

Use real recordings for physical contacts and original synthesized tones for short musical/UI
cues. No third-party recordings have been selected or downloaded. The first prototype bundles original synthesized audition sounds; see [audio sources](../design/audio/README.md).

| Sound family | Proposed source and preparation |
| --- | --- |
| Dice tumble and landing | CC0 recordings of actual dice on a suitable surface; separate tumble and contact for precise timing |
| Pawn steps and capture | A consistent family of dry wood/plastic contacts, with a few subtle variants |
| Finish, extra roll, interface, victory | Original synthesized cues sharing one tonal palette; compare compatible CC0 alternatives if needed |

Start with [Freesound](https://freesound.org/help/faq/), filtering specifically for CC0 and checking
each asset page. Its catalog contains several different licenses, and original-file downloads
normally require an account. If acquisition needs user access, provide a shortlist or choose
another verified source. The installed game will never need that account or a sound-library API.

[CC0](https://creativecommons.org/publicdomain/zero/1.0/) allows copying, modification, and
commercial distribution without copyright attribution requirements. Retain source/creator
metadata anyway. CC-BY is an alternative when its attribution requirements are handled;
noncommercial-only assets are not the default for a potentially commercial release.

[Mixkit's sound-effects license](https://mixkit.co/license/modal/sfxFree/) allows commercial
video-game use but prohibits redistribution with source files or as standalone stock. It is
therefore not the default for audio files committed to this repository. Any selected license
must support both the app release and our actual asset-distribution workflow.

For original synthesized cues, retain the generator and parameters. For sourced audio, record
the exact asset URL, creator, license/version, retrieval date, and edits in an audio manifest.
Library/licensing references above were checked on 2026-09-23; verify the chosen asset terms
when acquiring files.

Shortlist two or three candidates for dice, pawn, and capture sounds. Compare them at matched
perceived loudness alongside the animation. Select one coherent set, trim silence, balance
levels, and bundle the finished files locally. Physical materials should sound consistent with
the artwork. The user should hear the candidate pack before it becomes the final soundtrack.

## 7. Supporting offline screens

- **Home:** strong game identity, New Game, conditional Resume, and quieter History/Settings actions.
- **Setup:** convenient friends/bots presets, 2–4 seats, visible teams, names, colors/symbols,
  and an obvious summary before starting. Avoid exposing development notes to players.
- **Pause/settings:** sound, volume, haptics, reduced motion, rules, restart, and exit.
- **Results:** winner identity, team-aware celebration, understandable progress, Play Again,
  Change Players, and Home. Let the final move finish before presenting results.
- **History/replay:** match the new visual language; replay controls stay specific to replay mode.

No online buttons, accounts, room codes, or connection-dependent startup in the offline release.
A fresh installation must launch and play in airplane mode.

## 8. Build strategy and deliverables

Use native Android presentation tools as the starting point: Compose for screens and controls,
and a dedicated drawing/rendering layer for the board and pieces. Prototype the visual result
before settling the renderer structure. A 2.5D look can be drawn without a full 3D scene; adopt
3D only if an approved visual requirement actually needs it.

Exact board geometry, interactive pawns, and die faces should be scalable, deterministic artwork.
Raster art can support backgrounds, branding, or concepts; it must not determine playable geometry.
Build a small design specification covering palette, typography, spacing, materials, icons,
pawn states, timing, and sound cues. Keep these decisions independent of existing UI components.

### Phase A — Visual prototype

- [ ] Choose the art direction using a full gameplay composition.
- [ ] Deliver portrait and landscape compositions, including a crowded midgame state.
- [ ] Show pawn/stack states and a die study at real phone size.
- [ ] Review the result with the user before expanding the style across the app.

### Phase B — Playable presentation slice

- [x] Build the new board, player panels, pawns, die, and touch selection.
- [ ] Demonstrate one complete roll → selection → move → handoff sequence.
- [ ] Include a capture, a pair move, a home-path choice, and a finish sequence.
- [ ] Synchronize sound/haptics and provide mute/reduced-motion behavior.
- [x] Make the slice playable against the existing rules, not just an animation demo.

### Phase C — Complete offline app

- [ ] Apply the design to home, setup, settings, results, and replay/history.
- [ ] Preserve save/resume, undo/redo, teams, bots, and user settings.
- [x] Isolate offline startup/navigation from network services and hide online entry points.
- [ ] Remove superseded offline presentation and assets once their replacements are verified.

### Phase D — Device polish and release

- [ ] Complete the [Play Console API 36 requirement](009-offline-android-redesign-goal.md#google-play-target-api-requirement--release-blocker): target/compile SDK 36 and AGP 8.10.1 are applied; finish release validation and publish a compliant production update before the user-reported 1 November 2026 deadline.

- [ ] Play complete games on a compact phone and a larger screen; validate landscape and large text.
- [ ] Check crowded stacks, mixed-color pairs, repeated die values, rapid taps, interruptions,
  undo during transitions, process recreation, and game-over sequencing.
- [ ] Check airplane-mode fresh launch, resume, bots, local history, and all bundled assets.
- [ ] Measure frame pacing on a representative lower-end device, targeting smooth 60 fps during
  normal play; keep event effects within the same frame budget and avoid wasteful idle animation.
- [ ] Run relevant engine/interaction regression checks and build the Android release candidate.
- [ ] Prepare release assets/versioning, release through the user's chosen channel, then play it.
- [ ] Incorporate real-play feedback before revisiting online development.

## 9. Completion criteria

The redesign is ready when the board and pieces look deliberate at actual phone size, every
turn is understandable, selection is reliable, motion and sound feel coordinated, and a complete
game remains comfortable to play. Offline launch/resume, settings, bots, teams, and replay must
work reliably. The user should be happy to install, release, and play this version.

Do not broaden this effort into online work, theme stores, cosmetic economies, or multiplayer
architecture. Choose one strong visual direction and finish it.

## Implementation handoff — first prototype

Implemented in `ui/tabletop/`, wired into native offline gameplay:

- A separate board renderer with ceramic-style track cells, colored home courts, safe markers,
  a center medallion, sculpted pawns, player symbols, and pair links.
- Portrait player rails and a stable dice tray; a side-by-side board/control layout in landscape.
- A projected cube with fixed numbered faces, several rotations, landing/settling, and a roll
  event identity so equal consecutive results still animate.
- A roll presentation gate before bot selection, single-move assistance, or no-move advance.
- Interpolated pawn hops, capture-return paths, and contact sounds at movement steps.
- A persistent reduced-motion preference and original, reproducible prototype audio.
- New offline home screen; active navigation starts at Home and excludes online/auth routes.
- Existing engine, save/resume, setup, history/replay, undo/redo, and stack/home-choice logic retained.

The old setup and results screens remain for this first slice. Final art review, a complete
motion/stack/accessibility pass, final audio selection, and extended device play remain open.
The existing online source and dependencies are retained, but online features are not exposed
through this branch's active navigation. No online UI work was performed.

Next: install and review the prototype at actual phone size, then refine the board/dice feel
before extending the design to setup, results, and history. This is not a release candidate.

Validation: debug APK builds; 66 Android unit tests and 73 engine tests pass. An Android 16
emulator UI smoke test passed in airplane mode through setup, a settled dice roll, landscape
rotation, and feedback settings. [Screenshots and build handoff](../design/prototypes/README.md).
Full-game and physical-device validation remain open.

## September 24 user refinement — implementation and review

The user's review replaces the single-die tray and mostly white courtyard direction above.
Keep the sculpted pawn silhouette and make the board feel fuller and more prominent,
using Ludo King/Ludo Club as composition references. Native offline scope and rules remain unchanged.

### Usage-conscious implementation order

These are rough shares of this task's effort, not percentages of the account usage allowance.

| Work | Estimated share |
| --- | ---: |
| Planning and focused inspection | 5% |
| Larger board and four corner dice | 23% |
| Richer home courtyard artwork | 12% |
| Larger pawns and legal-move pulse | 12% |
| Bouncy normal movement | 10% |
| Capture impact and backward return | 23% |
| Exact echo “faaah” asset integration | 5% |
| Combined verification and APK handoff | 10% |

Implementation combines layout/artwork/pawns first, then movement/capture. Reuse existing
Compose rendering, touch geometry, route helpers, and sound playback. Keep checks batched;
the user handles visual/device/listening review. No generated bitmap assets or emulator
screenshot iteration is required for this pass.

### Implemented

- Four stable corner seats, each with a 48 dp die; only the current player's roll action is enabled.
  Roll ownership persists through animation. Unrolled dice have blank faces, while past rolls
  stay on the correct player's die. Empty seats keep their position.
- Removed the separate large dice tray and thick clipped board frame; outer horizontal padding
  is 4 dp. Portrait measures the player rows first and gives remaining space to the square board.
  Landscape puts the board between two columns of corner seats.
- Stronger colored courtyard interiors with an engraved rosette, inset borders, and lighter docks;
  light track cells and safe markers stay legible.
- Existing pawn artwork is 28% larger without changing tap geometry. Only legal pawns pulse
  (1.0–1.1 scale); a small base highlight replaces the bubble rings. Reduced motion is steady.
- Normal steps take 130 ms with a higher hop, small rebound, and landing squash. Pair links
  interpolate with the pieces.
- Capture starts at contact: local burst/echo rings and victim recoil, then an 80 ms-per-cell
  backward return to the color's entry and the specific pawn dock. Return movement has no bounce.
  Input remains gated through the sequence; subsequent audio waits for actual completion.
- Restored the previous default “faaah” recording from the existing `sfx_capture.ogg` at impact,
  through an offline feedback override. The other tabletop sounds remain in use. The repository
  also retains `fahhh_kcgaxfs.mp3`; commit `2674a77` identifies the shipped capture sound as
  “tut tut faah”. No new recording was downloaded or synthesized. User listening review remains.

### Validation and manual handoff

- Passed: debug APK build and all 143 unit tests (70 app, 73 engine; engine checks reused unchanged Gradle results).
- Combined command: `./gradlew :app:testDebugUnitTest :game-engine:test :app:assembleDebug --offline --console=plain`.
- New regression cases exercise real captures for all four colors, reverse routing across index zero,
  exact pawn docks, waiting for attacker contact, ordinary moves, and no animations on restore/undo.
- Device/visual/audio checks are delegated to the user. Check portrait and landscape board size,
  corner dice ownership, crowded stack selection, pulse visibility, hop strength, short/long captures,
  mute/reduced motion, and resume/undo/replay after a capture.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Existing prototype screenshots show the
  previous design and were intentionally not regenerated. This is a review build, not a release.

## September 24 follow-up — garden presentation and FLN review

- Colored rose/lilac/mint board substrate now fills the gaps beneath white playing cells.
  Entry stars and home-lane dots retain player colors on the white faces.
- Legal pawns pulse to 1.20× with a soft white/player-color light bloom; reduced motion
  keeps the steady highlight. Capture returns take 35 ms per cell (previously 80 ms).
- Corner name cards and 48 dp dice trays are separate surfaces with a 6 dp gap. Only
  the current presentation player has a visible die; other trays remain empty.
- Route choices, stack choices, exit and feedback prompts use rounded light surfaces.
  Illegal circulation keeps the existing engine legality check and now appears greyed out,
  including its miniature route illustration, with a reason. It cannot be tapped.
- Offline pages share orchid/rose controls and native, scalable botanical background art:
  plum/lilac during play and on Home, pale lilac/rose/peach in setup/history/results.
  Home includes a disabled empty-history action; results scroll within safe insets.
- FLN 1.0 already encodes selected pawn ownership, home-entry deferral and roll-only moves.
  Pairs, captures and permanent team-sharing unlock reconstruct through the current engine.
  No format bump is needed. New documents identify rules as `2026.09.23` rather than the
  ambiguous monthly `2026.09` label; existing files remain readable. Older histories are
  replayed under current rules, not a historical engine, and may fail if they depended on
  superseded behavior. No historical rules emulator is introduced.
- Fixed replay accepting an incorrect roll-only reason. Regression tests run up to 1,200
  deterministic rolls in each game mode through recorder, FLN serialization/parser and
  replay, comparing complete final states; a separate test rejects a mislabeled forfeit.
- Validation: Android debug APK and unit tests pass. Visual/device/audio testing is explicitly
  left to the user; no emulator or screenshot generation was performed for this pass.


## Tester feedback — simpler setup (September 25)

New game now opens Quick game: two local people in diagonal red/yellow seats by default,
with optional three/four-player presets and a single Start game action. Quick presets use
fresh names, colors and person-only seats regardless of saved custom settings.
Custom game contains the existing seat combinations, teams, names, explicit Person/Computer
choices and an optional color section. Seats are arranged like board corners; adjacent-seat
play remains available. Names and colors no longer require a two-step wizard.

The experimental AI selection and technical bot-policy labels are hidden while AI development
is paused. Regular computer opponents remain available in Custom game. New games and Play
again use the regular heuristic policy, including when previous preferences selected experimental
AI; existing saves and bot implementation remain recoverable. Blank custom names get defaults.
This supersedes earlier setup recommendations that exposed bot-policy selection.

Validation: all 75 app unit tests pass, including quick-setup/legacy-preference coverage.
The debug APK builds and updated offline navigation instrumentation tests compile; device tests
were not run. Device visual review remains necessary for compact screens, landscape and large text.


## Tester feedback — less reading before play (September 25)

- Keep English for now; Bangla support is deferred. Use “Single” and “Team”.
- New game opens directly to player count and a four-corner preview. More options
  opens custom controls on the same page, carrying over the chosen player count;
  Quick setup returns to fresh person-only presets. Team setup labels matching pairs.
  Short labels, player symbols and wrapping controls replace longer explanations.
- Custom colors update player cards, offer 48 dp selectable targets with spoken color
  names and checkmarks, and include the exact default colors. Single seat choices
  survive switching to Team and back.
- Home has a shorter Resume action with player names, and empty history is supporting
  text. Buttons can grow with text size.
- Validation: app unit tests, debug APK and instrumentation compilation are checked
  for this change. Added device regression coverage for mode switching and default
  color selection; device execution and visual review remain pending.


## September 25 follow-up — pre-game usability

The user approved interface previews, then narrowed implementation to pages before
play. This supersedes the proposed gameplay/status, exit-dialog, and victory changes:
the board, gameplay controls, exit prompt, and victory screen remain unchanged.

- Home always exposes Saved games, including on a fresh installation, and offers
  optional Rules and Settings dialogs. Home settings use the existing persisted
  feedback preferences and tabletop sound preview without changing the board UI.
- New game offers Pass & play or Vs computer with 2–4 total players. Computer
  presets start with one human and one to three regular computer opponents; More
  options carries the preset into the existing custom setup controls.
- Saved games has an empty-state Import action, player-name titles, a single Replay
  action for finished games, and Export/Delete in each record's menu. Delete requires
  confirmation identifying the selected game; Cancel and dismiss preserve it.
- Verification: app unit tests, debug APK, and instrumentation compilation are run
  for this change. Added computer-preset tests and pre-game UI regression coverage
  for empty-history import access and delete cancellation/confirmation. Device
  execution and visual review remain with the user.


## September 27 follow-up — visual stack move choices

- Stack move options now show tappable pawn samples in the customized player colors,
  using the board renderer and identity symbols instead of default color names/dots.
- Single moves show one pawn; locked pairs show both pawns, including mixed team colors.
  Spoken option descriptions use player names. Selection behavior is preserved.
- Validation: app unit tests and debug APK build pass. Regression assertions cover
  teammate single choices and same-color/mixed-color pair previews. Device visual
  and accessibility review remain with the user.

## September 27 follow-up — simpler route prompt

- Route choices now ask “Which way?” with “Enter finish / Toward the center” and
  “Go around / One more lap”. Larger matching diagrams show the customized pawn,
  center, shared track and directional arrows. Text keeps a fixed dark color for
  readability with arbitrary pawn colors; the options scroll on short screens.
- Illegal circulation stays disabled and grey with a short “Blocked” label and
  the existing full reason available to screen readers. Move rules are unchanged.
- Validation: Android compilation and debug APK build passed. Device visual and
  accessibility review remain pending.


## September 28 follow-up — advanced sound choices

- Home and in-game Settings now share an Advanced settings page with per-category lists,
  previews, and reset. Choices persist independently of new-game setup and apply to all games.
- Each current sound is an ordinary catalog option marked as the category default; playback
  keeps the same resources, gain, pitch variation, and gameplay event timing. No audio assets
  were replaced or added, and unused audition assets remain recoverable.
- Stable sound/category IDs are stored in the existing local preferences. Missing, obsolete,
  or wrong-category selections fall back to the category default. Playback preloads selected
  assets and loads preview options on demand rather than preloading the whole future catalog.
- Adding options requires a bundled resource and catalog entry; the picker and persistence
  discover entries automatically. See [sound extension guidance](../design/audio/README.md).
- Validation: all 84 app unit tests passed; debug APK build and UI-test compilation passed via
  `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --console=plain`.
  Device execution and listening review remain with the user. Review Settings → Advanced
  settings from home and gameplay, previews with mute/volume, reset, and persistence after restart.


## September 28 follow-up — recorded wooden sound alternatives

- Added Wooden tumble (new dice default) and Light wooden roll from Wuzzy's CC0 wooden-die
  recordings, plus optional Wooden tap for pawn movement and Wooden double knock for captures
  from Aidan_Walker's CC0 board-piece recordings. All original options and stable IDs remain.
- Trimmed/filtered real recordings retain the existing cue timing; dice/pawn mixer gains are
  approximately matched to previous RMS levels. Four app WAVs total 73,822 bytes. Original source
  recordings, exact URLs, licenses, checksums, and repeatable preparation are retained under
  [audio sources](../design/audio/README.md).
- New/default preferences and Reset sound choices use Wooden tumble. Existing saved original
  dice choices are preserved; choose Wooden tumble in Advanced settings to switch. Other
  categories keep their existing defaults.
- Validation: all 87 app unit tests passed; debug APK build and UI-test compilation passed.
  WAV checks passed for mono 22,050 Hz / 16-bit PCM, duration, endpoint fades, and peak headroom.
  Device UI execution and subjective phone/headphone listening are still for user review.


## September 28 follow-up — snake hiss and capture skull

- Added Snake hiss as an optional capture sound in Advanced settings. The default remains faaah;
  existing saved selections are preserved. The 600 ms cue is prepared from Reitanna's CC0 hiss
  preview; source, license, exact download URL, hash, and preparation script are retained in
  [audio documentation](../design/audio/README.md).
- A native Canvas skull with crossbones pops up, rises, and fades over the collision square on
  the existing 440 ms capture-effect clock. It shares capture impact timing with the selected
  sound and echo rings, is disabled by reduced motion, and does not participate in pawn taps.
  Capture rules, return paths, animation cancellation, and input gating are unchanged.
- Validation: all 89 app unit tests (including capture sequencing, saved sound choices, and
  defaults) passed; debug APK build passed. Hiss WAV format, duration, fades, and headroom checked.
  Device visual/listening checks remain with the user: select Snake hiss, capture a pawn or pair,
  and review the skull timing plus muted/reduced-motion behavior.


## September 28 follow-up — adjustable pawn speeds

- Home and in-game Settings share separate Forward speed and Backward speed sliders,
  from 0.5× to 4× in 0.25× increments, plus Reset pawn speeds. Backward means the
  captured pawn's return to base. Both default to 1×, retaining the existing 130 ms
  forward / 35 ms return steps until tester feedback establishes new defaults.
- Preferences persist locally across sessions and games; each slider saves on release.
  Missing values use defaults; non-finite values fall back and out-of-range values clamp.
  Speed settings are presentation preferences, independent of rules, saves, and history.
- Each move snapshots its speeds. Collision landing uses forward speed; capture returns
  use backward speed. Reduced motion still overrides step timing and disables the sliders
  while retaining their values. Reset affects only the two speed preferences.
- Existing completion-based input locks, bot turn handoff, replay autoplay, and sound
  sequencing remain in use. Dice, bot thinking delays, capture effects/hold, and final
  settling delay retain their timing; these controls adjust pawn travel only. Actual
  animation durations are subject to display frame rate, especially fast return steps.
- Validation: all 101 app unit tests passed, debug APK built, and UI tests compiled via
  `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline --console=plain`.
  Coverage includes preference round trips, independent speeds, invalid values, capture
  paths/contact timing, reduced motion, and the full speed range. UI tests are compiled,
  not device-executed. The existing unrelated TabletopGameLayout edit is preserved.
- Device review remains with the user: adjust each speed from home and during a game,
  restart to check persistence, try long captures and pairs at both extremes, review
  bot/replay sequencing and sound, then reset. Collect forward/backward values separately
  with tester feedback before choosing new defaults.


## Follow-up — slow pawn movement at default and maximum speed

- User reports that both the default and maximum slider settings feel slower than the
  previous game. Phone lag has not been reproduced locally; changing defaults alone
  is not treated as proof of a fix. The earlier supplied APK was a debug build, which
  also makes comparisons with an optimized published release inconclusive.
- Replaced the per-square snap/animate loop with one continuous clock for forward
  travel and one for capture return. Cell and hop progress derive from the phase's
  position, so missed frames catch up instead of accumulating waits at every square.
  Captures still wait for attacker contact and the impact hold; cleanup, turn gating,
  replay completion, and rules remain in their existing paths.
- Both default multipliers are now 2×. The sliders span 1×–6× in 0.5× increments;
  they no longer offer speeds below the original timing. Existing valid saved values
  survive; old values below 1× clamp to 1×. Use Reset pawn speeds to adopt 2× on an
  installation with saved preferences. Reduced motion completes each whole phase
  with minimal animation rather than waiting a frame for every cell.
- Validation: 111 app unit tests passed; debug APK build and UI-test compilation
  passed. Tests run the actual animation function with deterministic 16, 33, and
  80 ms frame clocks, verify long-path completion and contact boundaries, and compare
  2×/6× to the previous per-cell runner. These are timing regressions, not device FPS
  measurements. Device UI execution and performance review remain with the user.
- Normal optimized release APK built with `:app:assembleRelease --offline`; release
  lint and upload-key validation passed. APK signature and non-debuggable manifest
  verified. Artifact: `app/build/outputs/apk/release/app-release.apk`. Nothing published;
  app version remains 1.0.9 / code 13, so a new Play upload still needs version review.
