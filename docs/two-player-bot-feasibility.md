# Two-player bot feasibility

Assessment: 2 October 2026. Research branch: `research/two-player-bot`,
created from `feat/online-testing`. No gameplay changes or deployment.

## Recommendation

Build a separate two-player policy/value bot that runs locally. Train on a
workstation or server, then package the resulting model with Android. Start exact
numerical solving with small endgames; do not start by enumerating the entire game.
A full dense value table is impractical for the APK and a poor first experiment
even on a shared server. Hosting does not remove the cost of computing the values.

These are combinatorial budgets, not measured training times or a proof of the
minimum storage needed by every possible compressed representation.

## What a state and its value mean

Assume two active colors, four pawns each, free-for-all rules, fair independent
dice, no online timers/resignation, and a fixed pair of colors. Use the current
[rule contract](game-rules-live.md), including captures, locked pairs, safe cells,
bonus rolls, consecutive-six forfeits, and optional home-entry deferral.

Define V(s) as the probability that player A eventually wins under optimal play
by both players. Against a particular opponent policy the values can differ.
This is not simply expected pawn progress. A pre-roll state needs the board,
player to act, and consecutive-six streak (0, 1, 2). A post-roll decision also
needs the die result. Dice outcomes are averaged; A maximizes the continuation
value and B minimizes it. Bonus rolls can retain the same player, so do not
blindly alternate maximization/minimization after every move.

Captures and circulation create cycles: this is a stochastic game graph, not a
one-pass backward calculation. Solve cyclic components with convergence/error
checks. Define nontermination explicitly (e.g. eventual-win payoff, where never
winning has payoff zero); do not label a simulation turn cap a proven draw.
Finite-precision solutions need error bounds before being called optimal or exact.
The standard chance/max/min recurrence is described in the University of Basel's
[stochastic games lecture](https://ai.dmi.unibas.ch/_files/teaching/fs23/ai/slides/ai43.pdf).

For optimal play, same-color pawn IDs can be exchanged. A non-safe triple is
always one pair plus one single; exchanging which identical pawn is the single
does not create a different strategic position. Preserve that distinction when
mapping a canonical action back to an actual pawn ID. This reduction is not
automatically valid for evaluating an ID-dependent opponent policy.

Do not count event logs, display dice, move counters, player names, or arbitrary
pair-key strings as strategic dimensions. Team unlock history is irrelevant in
two-player free-for-all. The stored GameState is not the minimal solver state.

## State count

Each pawn has 59 locations: base + 52 track cells + 5 home cells + finished.

| Representation | Count |
| --- | ---: |
| Eight labeled pawns, position assignments only: 59^8 | 146,830,437,604,321 |
| One color's four interchangeable pawns: C(62,4) | 557,845 |
| Remove four pawns together on each of 44 non-safe cells | 557,801 |
| Two-color board assignments: 557,801^2 | 311,141,955,601 |
| Pre-roll table: board × 2 players × 3 six-streak values | 1,866,851,733,606 |

The first row ignores pair identity in labeled states; it is a position count,
not a complete labeled GameState count. The last row is a useful dense allocation
bound after pawn-identity reduction, **not the exact reachable-state total**.
It still includes impossible opponent co-occupancies, unreachable histories, and
redundant terminal/turn/streak combinations. Obtaining an exact reachable count
requires additional legality/reachability analysis or exhaustive traversal.
No such traversal has been run.

This budget is for one fixed color matchup, not every setup. There are six
unordered color pairs. Rotations reduce these to adjacent and opposite geometry;
player orientation/turn must still be handled correctly. Do not assume reflection
symmetry, since movement is directional. Further validated symmetries may reduce
storage, but no unproven symmetry savings are included here.

Store pre-roll values only and generate dice/action successors on demand; there
is no need to multiply storage blindly by six dice outcomes and every UI phase.

## Storage and computation

For the 1.867 trillion-slot dense table (decimal units):

| Value format | One table |
| --- | ---: |
| 8-bit quantized value | 1.87 TB |
| 16-bit value | 3.73 TB |
| 32-bit float | 7.47 TB |
| 64-bit float | 14.93 TB |

These exclude indexing, transitions, policy, and solver workspace. A solver with
two float32 buffers alone would need 14.93 TB. Quantization can change close move
rankings; it does not preserve an exact optimal policy automatically. Even a
hypothetical 100-fold state reduction leaves about 74.7 GB of float32 values.

At hypothetical rates of one million / ten million / one hundred million
**complete state backups per second**, one dense sweep takes 21.6 days / 2.16
days / 5.19 hours. These are arithmetic scenarios, not benchmark results. Each
backup must consider six die outcomes and legal actions (up to four pawn choices,
potentially with enter/defer alternatives), and convergence may require many
sweeps. Precomputed edges would add substantial storage. Do not infer a cloud
budget or completion date without benchmarking a compact transition engine.

## Practical exact-solver experiments

Use endgames with at most k unfinished pawns per player. Base pawns count as
unfinished; the other pawns are already finished. Captures return a pawn to base
but never increase the unfinished count, so these subsets are closed under play.
For k <= 3, one side has C(58+k,k) placements; for k=4 apply the stack-cap correction.

| Maximum unfinished per player | Dense pre-roll slots | Float32 values |
| --- | ---: | ---: |
| 1 | 20,886 | 83.5 KB |
| 2 | 18,797,400 | 75.2 MB |
| 3 | 7,771,680,600 | 31.1 GB |
| 4 | 1,866,851,733,606 | 7.47 TB |

Begin with 1-vs-1 endgames and validate transitions against `game-engine`, then
measure a 2-vs-2 endgame solver. The latter is a plausible workstation experiment
and an optional packaged table; solving speed and convergence remain unmeasured.
Three unfinished per side already calls for substantially more resources.

## APK versus shared server

The app already has `BotPolicyEngine`, a model scorer, and a heuristic fallback
in [BotPolicyEngine.kt](../app/src/main/kotlin/com/failureludo/ai/BotPolicyEngine.kt).
The current JSON model is 3,661,370 bytes and has 174,337 parameters: approximately
0.697 MB of raw float32 parameters, excluding parsing/object/activation overhead.
This demonstrates a small inference representation, not the strength of a future
two-player bot. Current training is imitation-based, not a solved value function.

Train a separate two-player value/policy model with self-play and outcome targets.
Use it directly or with bounded expectiminimax/chance-aware Monte Carlo search.
Select it only for two active colors; retain the existing bot for other modes and
as a fallback. The scorer already exposes defer-home-entry candidates while the
heuristic always enters; explicitly align training and evaluation action spaces.

Training resources and serving resources are separate decisions. A large training
machine can produce a small model that requires no server while playing. Binary
weights can avoid JSON overhead; quantization is another option after strength
validation. Google's [LiteRT quantization documentation](https://ai.google.dev/edge/litert/models/post_training_quantization)
describes float16/int8 options and the need to check accuracy after conversion.
The existing Kotlin inference path means a new runtime is not mandatory.

A shared server becomes useful for a deliberately larger model, deeper search,
or a large endgame database. It introduces latency, operating cost, and availability
dependence. Keep a local bot so offline play remains available. No hosting or
deployment is needed for this assessment.

## Proposed experiment and evidence gates

1. Implement canonical state/action encoding and verify engine parity, including
   pair versus single moves, captures, barriers, bonus turns, and home deferral.
2. Solve 1-vs-1 endgames to a documented tolerance; record residual/error checks,
   nontermination handling, state counts, memory, and backups per second.
3. Scale to 2-vs-2 endgames if measured costs fit available hardware. Train a
   separate full-game two-player model; compare learned values to solved endgames.
4. Evaluate against the existing heuristic across seeds, both starting seats,
   adjacent/opposite color matchups, and the same legal action space. Report win
   confidence intervals and unfinished games separately.
5. Measure model size, peak RAM, and decision p95 on target Android hardware.
   A provisional search budget of 100–300 ms per decision is a target, not a
   measured capability. Decide on server inference only from these results.

Reproduce arithmetic with `python3 ai-training/two_player_state_budget.py`.
This assessment does not implement/train a new bot or establish device performance.
