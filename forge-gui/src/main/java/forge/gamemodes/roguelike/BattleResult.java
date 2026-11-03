package forge.gamemodes.roguelike;

/** What a fight hands back to the run. */
public class BattleResult {
    public final String enemyId;
    public final boolean won;
    public final int playerLifeEnd;
    public final int turns;

    public BattleResult(String enemyId, boolean won, int playerLifeEnd, int turns) {
        this.enemyId = enemyId;
        this.won = won;
        this.playerLifeEnd = playerLifeEnd;
        this.turns = turns;
    }

    @Override
    public String toString() {
        return (won ? "Won" : "Lost") + " vs " + enemyId + " in " + turns + " turns, life left " + playerLifeEnd;
    }
}
