package forge.gamemodes.roguelike;

/** A playable champion, loaded from res/roguelike/champions/*.json. */
public class ChampionSpec {
    public String id;
    public String name;
    public String description = "";
    /** The champion card. It starts in the command zone and is cast like a commander. */
    public String champion;
    /** Champion power cards by level (index 0 = level 1). Each sits in the command zone. */
    public String[] powers = new String[0];
    /** Deck file relative to res/roguelike/. Does not include the champion. */
    public String startingDeck;
    /** Reward pool file relative to res/roguelike/. */
    public String rewardPool;
    public int maxHp = 30;
}
