package forge.gamemodes.roguelike;

/** The sequence of encounter tiers in an act. The map comes later; for now an act is a straight line. */
public class ActSpec {
    public String id;
    public String name;
    /** One tier per floor: normal, elite or boss. */
    public String[] floors = new String[0];
}
