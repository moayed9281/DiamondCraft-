package dev.diamondstrength;

/** The "can this action happen?" rules. No Minecraft code in here, so they can be tested on their own. */
final class Rules {
    private Rules() {}

    /** True only if the player really has at least `amount` (and amount is 1 or more). */
    static boolean canWithdraw(int have, int amount) {
        return amount >= 1 && have >= 1 && amount <= have;
    }

    /** Deposit needs room for more strength AND the full diamond cost. */
    static boolean canDeposit(int deposits, int maxDeposits, int diamonds, int cost) {
        return deposits < maxDeposits && diamonds >= cost;
    }

    /** A bottle only works (and is only used up) while strength is below the max. */
    static boolean canDrinkBottle(int deposits, int maxDeposits) {
        return deposits < maxDeposits;
    }

    /** Hearts only exist at MAX strength: a heart item works only while strength is full and hearts are below the cap. */
    static boolean canUseHeart(int deposits, int maxDeposits, int hearts, int maxHearts) {
        return deposits >= maxDeposits && hearts < maxHearts;
    }

    enum Reward { NONE, STRENGTH, HEART, ABILITY }

    /**
     * What one player kill is worth. Always exactly ONE step of the stage you are in:
     * strength (not full yet) -> heart (strength full) -> ability upgrade (hearts full). Teammates give nothing.
     */
    static Reward killReward(boolean teamKill, int deposits, int maxDeposits, int hearts, int maxHearts,
                             int abilityLevel, int maxAbilityLevel) {
        if (teamKill) return Reward.NONE;
        if (deposits < maxDeposits) return Reward.STRENGTH;
        if (hearts < maxHearts) return Reward.HEART;
        if (abilityLevel < maxAbilityLevel) return Reward.ABILITY;
        return Reward.NONE;
    }

    /** Strength after a kill reward (never above the max). */
    static int strengthAfterKill(int deposits, int maxDeposits, int unitsPerKill) {
        return Math.min(maxDeposits, deposits + unitsPerKill);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
