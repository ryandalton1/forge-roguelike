package forge.gamemodes.roguelike;

/**
 * One roguelike enemy, loaded from res/roguelike/enemies/*.json.
 * Every "cheat" is a card name: either a real card or a custom script from res/roguelike/custom_cards.
 */
public class EnemySpec {
    public String id;
    public String name;
    /** normal | elite | boss */
    public String tier = "normal";
    /** AI profile name (Default, Reckless, Cautious, Experimental, Hard, Hardest) or empty for the user's setting. */
    public String ai = "";
    public int life = 10;
    public int startingHand = 5;
    /** Deck file relative to res/roguelike/, e.g. "decks/goblins_small.dck". */
    public String deck;
    /** Cards that start on the battlefield (lands, creatures, artifacts...). */
    public String[] startBattlefield = new String[0];
    /** Cards that start in the command zone: emblems, boss effects, phase cards. */
    public String[] commandZone = new String[0];
    /** Scheme cards. One is set in motion at the start of each of the enemy's precombat main phases. */
    public String[] schemes = new String[0];
    /** Text shown before the fight. */
    public String intro = "";

    public String displayName() {
        return name != null ? name : id;
    }
}
