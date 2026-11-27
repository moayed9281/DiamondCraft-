DiamondCraft-mod (Fabric 1.21.11) - flat layout: every file lives in the repo root.
Build: GitHub Actions (see build.yml - it must be placed at .github/workflows/build.yml)
Commands: /deposit, /ability info, /ability use. Key: R (rebindable).

/withdraw strength -> turns 0.5 strength into a bottle. /withdraw heart -> turns 1 Diamond Heart into an item (right-click to use it again).
/withdrawal works as an alias.

/withdraw heart [amount] and /withdraw strength [amount] (e.g. /withdraw heart 2). Diamond Hearts show cyan on the heart bar.

TEAMS: /team add <player> (they type /team accept), /team leave, /team list. Max 4 per team.
Teammates can still kill each other, but the killer gets NO heart / ability reward. The victim still loses a heart/strength.
Hearts and kill rewards only work while you are at FULL strength right now. /withdraw strength is blocked while you hold hearts.

KILL REWARDS (one player kill = one step of the stage you are in; teammates give nothing):
  strength not full  -> +1.0 strength (3 kills = max)
  strength full      -> +1 Diamond Heart (max 5)
  hearts full        -> +1 ability level (unlocks at level 1, max 3)
