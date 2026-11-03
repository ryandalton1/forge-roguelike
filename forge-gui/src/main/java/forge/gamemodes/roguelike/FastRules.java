package forge.gamemodes.roguelike;

/** The rule tweaks that keep a roguelike fight to a few minutes. */
public class FastRules {
    /** Basic lands moved from the player's library onto the battlefield before the game starts. */
    public int playerStartingLands = 2;
    public int playerStartingHand = 5;
    /** Give both players the "RL Sudden Death" turn clock. */
    public boolean turnClock = true;
    /** Let the enemy AI stack its library against mana screw/flood (AI profile option CHEAT_WITH_MANA_ON_SHUFFLE). */
    public boolean aiCheatShuffle = true;

    public static FastRules defaults() {
        return new FastRules();
    }
}
