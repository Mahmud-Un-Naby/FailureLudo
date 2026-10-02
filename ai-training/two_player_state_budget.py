"""Print dense state-table budgets, NOT exact reachable-state counts.

Run from any directory with Python 3; no third-party dependencies.
Assumptions and exclusions: docs/two-player-bot-feasibility.md.
"""

from math import comb


def placements(max_unfinished: int) -> int:
    # 58 unfinished locations; Finished pads the four-pawn multiset.
    count = comb(58 + max_unfinished, max_unfinished)
    # Four friendly pawns on any of 44 non-safe track cells are illegal.
    return count - (44 if max_unfinished == 4 else 0)


def main() -> None:
    print("One fixed pair of colors; interchangeable same-color pawns")
    print("Dense bounds include unreachable and redundant terminal slots.")
    print(f"Labeled position assignments: {59 ** 8:,}")
    print(f"Unrestricted placements per color: {comb(62, 4):,}")
    count = placements(4)
    print(f"Placements per color after stack cap: {count:,}")
    print(f"Board assignment bound: {count ** 2:,}")
    # Pre-roll state: board + player to act + consecutive-six streak 0/1/2.
    slots = count ** 2 * 2 * 3
    print(f"Pre-roll slots: {slots:,}")
    print("Storage uses decimal MB/GB/TB; excludes indices and solver workspace.")
    for width in (1, 2, 4, 8):
        print(f"  {width} bytes/value: {slots * width / 10**12:.6f} TB")
    print("Hypothetical complete Bellman backups/sec (NOT measured):")
    for rate in (10**6, 10**7, 10**8):
        print(f"  {rate:,}: {slots / rate / 86400:.6f} days/sweep")
    print("Endgames: at most k unfinished pawns per player, including base pawns")
    for k in range(1, 5):
        endgame_slots = 6 * placements(k) ** 2
        print(f"  k={k}: {endgame_slots:,} slots; "
              f"{endgame_slots * 4 / 10**6:.6f} MB at float32")


if __name__ == "__main__":
    main()
