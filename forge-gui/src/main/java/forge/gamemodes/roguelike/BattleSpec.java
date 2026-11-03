package forge.gamemodes.roguelike;

import forge.deck.Deck;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything needed to start one roguelike fight. Front ends (the libGDX scene now, maybe Godot later)
 * build one of these and hand it to {@link BattleBuilder}.
 */
public class BattleSpec {
    public Deck playerDeck;
    /** The player's current run HP. Fights start at this value with no cap. */
    public int playerLife = 30;
    /** Champion card name, cast from the command zone like a commander. Null for no champion. */
    public String champion;
    /** Extra cards the player starts with on the battlefield (relics etc.), on top of the starting lands. */
    public final List<String> playerBattlefield = new ArrayList<>();
    /** Extra cards the player starts with in the command zone (relics, potions...). */
    public final List<String> playerCommandZone = new ArrayList<>();
    public EnemySpec enemy;
    public FastRules fast = FastRules.defaults();
}
