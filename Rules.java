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

    /** A heart item only works after max strength was reached and while below the heart cap. */
    static boolean canUseHeart(boolean heartUnlocked, int hearts, int maxHearts) {
        return heartUnlocked && hearts < maxHearts;
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
