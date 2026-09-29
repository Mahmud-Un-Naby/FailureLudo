# FailureLudo Live Rules

Last updated: 2026-09-29

This document defines the agreed game rules. Implementation differences must be
resolved against these rules; engine behavior does not override an explicit rule decision.

## Engine Implementations

- `game-engine/src/main/kotlin/com/failureludo/engine/GameEngine.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/GameRules.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/Board.kt`
- `game-engine/src/main/kotlin/com/failureludo/engine/PlayerColor.kt`
- `ludo-web/src/engine/gameEngine.ts`
- `ludo-web/src/engine/gameRules.ts`

## Rule Decisions and Implementation Status

- The unlock, no-move six, third-consecutive-six, pair-membership, capture-order, and immediate team-sharing rules below were clarified on 2026-09-23.
- The Kotlin and TypeScript engines implement the agreed unlock behavior, consecutive-six counting, persistent pair membership, capture-before-bonding order, three-friendly-pawn limit, and immediate team-sharing unlock.
- Android and web local/online play offer home-entry choices; circulating routes are checked against barriers and the stack limit before being allowed.
- Shared regression examples are in `game-engine/src/test/resources/live-rule-scenarios.json` and run against both engines.
- The home-entry boundary and choice prompt are confirmed below: landing on the branch cell stays on the main track; moving into the home path offers human players a choice to enter or keep circulating.
- The maximum of three friendly pawns per non-safe shared-track cell is enforced; safe squares and lettered home queues are exempt.

## Board and Seats

Numbering reference: [numbered board (PNG)](numbered-ludo-board.png) or [SVG](numbered-ludo-board.svg).
Numbered cells `0..51` belong to the shared main track. Only the lettered cells `R1..R5`, `B1..B5`, `Y1..Y5`, and `G1..G5` belong to the corresponding home queues.

- Main track has 52 cells (`0..51`).
- Each home queue has 5 steps (`1..5`), then the piece is `Finished`. The queues begin at `R1`, `B1`, `Y1`, and `G1`.
- Board-entry squares (from `HomeBase`):
  - RED: `0`
  - BLUE: `13`
  - YELLOW: `26`
  - GREEN: `39`
- Last shared-track cells before each color can turn into its home queue (these cells are not part of the queue and do not unlock pairs):
  - RED: `50`
  - BLUE: `11`
  - YELLOW: `24`
  - GREEN: `37`
- Safe squares (cannot be captured): `0, 8, 13, 21, 26, 34, 39, 47`.

## Game Setup

- Allowed player count: 2 to 4 active colors.
- Team mode requires exactly 4 active colors.
- All 4 player seats always exist internally; non-selected colors are marked inactive.
- Starting turn is the first color in `activeColors`.

## Turn Flow

1. Current player rolls.
2. If movable pieces exist: phase becomes piece selection.
3. If no movable pieces: phase becomes no-move, then auto-advance by caller. This ends the turn even when the roll is `6`; no extra roll is granted.
4. After move:
   - Win check
   - Extra-roll check
   - Otherwise next player

## Dice and Entry Rules

- Dice is `1..6`.
- A piece leaves `HomeBase` only on `6`.
- If a player rolls three consecutive sixes in the same turn, only the third roll is canceled: no pawn moves for that roll, no extra roll is granted, and play advances to the next player.
- All moves and captures completed earlier in that turn remain in effect; nothing is rolled back.
- A non-six roll breaks the consecutive-six streak, including when a capture grants another roll. For example, a capture on `2` followed by `6, 6` does not trigger the penalty.

## Movement Rules

- On main track, movement is clockwise by increasing index with wraparound.
- Landing exactly on the numbered shared-track cell before a color's home queue leaves the pawn on the main track; no home-entry prompt or unlocking occurs for that move.
- Entering home-queue step 1 requires one additional movement step after that shared-track cell. When a human-selected move would enter the home path, the player chooses whether to enter or keep circulating, as described below.
- Home column must be exact:
  - landing exactly beyond step 5 -> `Finished`
  - overshoot is invalid

## Doubled / Triple Pawn Rules

- Friendly-pawn limit:
  - A non-safe shared-track cell may contain at most three friendly pawns. This includes numbered cells `50`, `11`, `24`, and `37`.
  - In free-for-all mode, friendly means the same color. In team mode, both teammates' colors count together toward the limit.
  - Any move that would leave more than three friendly pawns on its destination cell is illegal. Count both members of an arriving pair; a pair cannot split to fit the limit.
  - Opponent pawns do not count toward this friendly-pawn limit.
  - Safe squares and lettered home queues (`R1..R5`, `B1..B5`, `Y1..Y5`, `G1..G5`) are exempt; pawns there remain independent singles.
  - The player must choose another legal move if one exists. If no legal move exists, the turn ends without an extra roll, even on a `6`.
  - Examples on a non-safe shared-track cell: a single arriving at two friendly pawns is allowed (three total); a single arriving at three is illegal (four total); a pair arriving at one is allowed (three total); a pair arriving at two or three is illegal (four or five total).

- Stacking model:
  - Except where unlock behavior applies, 2 same-color pawns on one main-track cell are treated as a tied double.
  - Except where unlock behavior applies, 3 same-color pawns on one main-track cell are treated as `double + top single`.
  - An existing pair keeps its two members until it unlocks or is captured; arrival order does not replace either member. This applies to same-color and mixed-color teammate pairs.
  - When a single lands on an existing friendly pair, the arriving single is the independent top single.
  - When a pair lands on an existing friendly single, the arriving pair remains tied and the existing single is the independent top single.
  - "Top single" identifies the independent pawn in a triple, not necessarily the most recently moved pawn.
- Locked double movement:
  - Locked doubles cannot move as separate pawns.
  - Only even dice can move a locked double.
  - Effective movement distance for the locked double is half the die value.
  - Odd dice means that locked pair has no legal move for that roll.
- Triple stack behavior on non-safe shared-track cells (including `50`, `11`, `24`, and `37`):
  - The top single remains independently movable.
  - The locked pair remains constrained to tied-double rules.
  - When a tap lands on a stack where both options are legal, the UI asks the player to choose **Move single** or **Move pair**.
- Unlock behavior:
  - A same-color pair unlocks when it lands on a safe square or enters its own lettered home queue. It does not unlock on `50`, `11`, `24`, or `37`; those are ordinary shared-track cells.
  - A same-color pair may enter its home queue together. The move uses the tied-double rule (even dice, half-distance); both pawns arrive at the same home-queue cell and become independent singles there.
  - For example, a tied red pair on `50` rolling `2` may enter `R1` together, then untie. Rolling `1` cannot move that pair.
  - Pawns in a home queue are always independent singles, even when they share a cell. A mixed-color teammate pair unlocks only on safe squares, as described below.
  - Unlocking removes tied movement, double capture protection, and barrier behavior. The pawns are independent singles for movement and capture rules, and opponents may pass them.
  - An unlocked stack provides no double-based protection to a top single.
  - Safe-square protection still applies independently: pawns on safe squares cannot be captured.
- Team-mode mixed doubles:
  - In Team mode, except on safe squares, two same-team pawns of different colors on the same main-track cell are also treated as a tied double.
  - Same tied-double movement rules apply (even-only rolls, half-distance movement).
  - While such a mixed-color pair remains tied, it cannot enter a home path and continues on the shared main track.
  - This enforces that a tied mixed pair follows the non-entering route until untied (safe-square unlock).
- Barrier behavior:
  - Enemy single pawns cannot jump over an opponent locked double stack. Unlocked stacks do not create barriers.
  - Enemy single pawns can land exactly on that barrier cell.
  - Double stacks can jump over other double stacks.
- Capture typing:
  - Single captures only singles.
  - Double captures only doubles.
  - In triple stacks, an enemy double captures only the double component.
  - The top single on own locked double is protected (virtual safe) from enemy single capture. This double-based protection ends when the double unlocks.
  - Enemy single on your double is not protected from your captures.
  - Special immediate pair capture:
    - If a player's single is already on top of an enemy double, and another single from the same side lands on that same cell, that arriving move immediately captures the enemy double. The two friendly singles then bond into a pair.
    - This reinforcement capture is an explicit exception to the ordinary rule that an arriving single captures only singles.
    - In Team mode, "same side" includes both teammate colors.

## Optional Home-Entry Deferral (Human Players)

- When a human-selected pawn on the main track would enter the home path (`HomeColumn` or direct `Finished`) for the current dice roll, a choice prompt appears before the move is applied. Landing exactly on the numbered shared-track cell before the queue does not trigger this prompt.
- The player can choose:
  - **Enter Finish**: use normal home-path behavior.
  - **Keep Circulating**: defer home entry for this move and stay on main track.
- On **Keep Circulating**, the pawn still belongs to the same player and moves to the normal wrapped main-track destination (no reset to starting square). Singles use the full die value; tied pairs use half of an even die value.
- This lets the pawn complete additional circulation before attempting home entry again.
- Bots do not use this prompt in the current implementation session.

Example: a red single pawn starts on cell `49`, with no blocking pawns along either route.

| Roll | Prompt? | Enter Finish | Keep Circulating |
| --- | --- | --- | --- |
| `1` | No | Moves directly to main-track cell `50`; no choice is needed. | Not offered. |
| `2` | Yes | `49 → 50 → R1` | `49 → 50 → 51` |
| `3` | Yes | `49 → 50 → R1 → R2` | `49 → 50 → 51 → 0` |

The same step counting and choice rule applies to each color's own home queue. A human selecting a same-color tied pair that would enter its queue receives the same choice; choosing to enter moves both pawns into the queue together, where they untie.

## Capture Rules

- Capture applies only when landing on main track.
- Landing on a safe square never captures.
- Resolve captures before forming any new friendly pair. Ordinary capture eligibility uses the moving unit's type before arrival (single or tied pair), subject to safe-square protection, top-single protection, and the special immediate pair-capture exception above.
- Eligible captured opponent pieces on the landing square are sent to `HomeBase`; then remaining friendly singles bond where the stacking rules require it. Existing pairs retain their members.
- Example: a red single lands on a cell containing another red single and a capturable blue single. Blue is captured first; the two remaining red pawns then form a pair.
- Capturing grants an extra roll.

## Extra Roll Rules

After a legal move, provided the game has not ended, an extra roll is granted when:

- the dice roll is `6`, or
- the move captures at least one opponent piece.

A `6` with no legal move ends the turn without an extra roll. A third consecutive
`6` is canceled before movement and also ends the turn without an extra roll.

## Turn Order Direction (Dice Passing)

- Pawn path remains as currently implemented.
- Turn passing is right-hand direction from the current player seat.
- Implementation detail: next player index moves backward through seat list and skips inactive seats.

## Winning Conditions

### Free-for-all

- First active player with all 4 pieces finished wins.

### Team mode

- Team 0: RED + YELLOW
- Team 1: BLUE + GREEN
- A team wins when both active members have all pieces finished.

## Team Dice Sharing Unlock (TEAM mode)

- Before unlock, each teammate can only use their own rolled dice with their own color pieces.
- Unlock condition is historical and permanent for that team:
  - each teammate must have rolled a `6` and used it to bring at least one of their own pawns out of `HomeBase` at least once.
  - rolling a `6` without bringing a pawn out does not satisfy the condition.
  - this condition remains satisfied even if one or both of those pawns are later captured.
- Unlock timing:
  - sharing unlocks immediately when the board-entry move satisfies the condition for both teammates.
  - the qualifying roll is used to bring that player's own pawn out; shared control is available for the very next roll, including bonus rolls within the same extended turn.
  - no turn advancement or full round of dice passing is required.
  - example: Red has already brought a pawn out. Yellow rolls `6` and brings its first pawn to `26`. Sharing unlocks on that move, so Yellow may use the following bonus roll to move either Red's or Yellow's pawns.
- After unlock, either teammate can use their rolled dice to move either teammate's pieces, including taking teammate pawns out of `HomeBase`.

## Online Resignation (Android/server, 29 September 2026)

- A player may explicitly resign from an active online game on any turn. The server
  confirms it against the observed room revision; resignation cannot be undone.
- Free-for-all: the resigned seat becomes inactive and its pawns leave play. If that
  was the current turn, the next active seat starts with a fresh roll. Otherwise the
  current player keeps their roll and legal moves are recalculated without the removed
  barriers. The last remaining player wins immediately.
- Team mode: the remaining teammate controls both colors on their existing turns.
  All pawns, turn order, already-rolled dice, consecutive-six counts, pair membership
  and shared-dice unlock state remain intact. Before unlock, each color's turn still
  moves only that color's pawns; controlling both seats does not grant early sharing.
  When both teammates resign, the opposing team wins immediately.
- Resigned players may watch or start another room, but cannot regain control by
  rejoining the old room. Room membership is retained for watching and receipt recovery.
- Returning to the lobby or losing connection does not explicitly resign. New online
  games continue their action and AFK timers as described below. Offline gameplay and
  the paused web client do not expose these online lifecycle controls.

## Online Action Timers and AFK (Android/server, 29 September 2026)

- New rooms give 10 seconds for each dice roll and a separate 10 seconds for each pawn
  choice. Every bonus roll starts another action window. The server owns these times.
- After a missed action, the server bot performs that one roll or pawn move using the
  shared engine and existing heuristic move selector. The next action again gives the
  human 10 seconds; bot coverage does not permanently convert the seat into a bot.
- AFK time starts at the beginning of the first missed action window. If the player
  remains AFK for two minutes, they forfeit their seat. Bot actions, reads, reconnects
  and retries do not reset this time. AFK expiry also applies between that player's turns.
- A successful human roll/move clears AFK status. The explicit "I'm back" action clears
  it before the two-minute deadline, including during another player's turn. Returning
  during one's own turn preserves the dice/pawns and starts a fresh 10-second window;
  returning during someone else's turn does not extend that player's timer.
- AFK forfeiture follows explicit resignation: free-for-all removes the forfeited seat;
  a remaining teammate takes over both colors, and losing both teammates loses the team.
- The server checks deadlines transactionally. Android requests automatic checks and
  shows only confirmed bot/forfeit results. If every app closes, no background game loop
  runs: overdue checks are resolved on return, without replaying unobserved bot turns.
- Existing v1/v2 rooms remain untimed during upgrade. New v3 rooms persist the timing
  policy, action deadline and AFK start times. The online profile avatar displays a
  decreasing ring for the current action, respecting reduced-motion settings.

## Bot Piece Selection Priority

When bot must choose among movable pieces:

1. capture move
2. finishing move
3. most advanced active piece
4. otherwise first movable piece

## Notes for Future Rule Changes

- Update this file in the same PR whenever rules are changed in engine code.
- If UI text/hints depend on rules, update those strings and flows in `:app` accordingly.
