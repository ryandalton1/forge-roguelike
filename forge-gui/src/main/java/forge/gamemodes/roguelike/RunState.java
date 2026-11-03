package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/** Everything about a run in progress. Saved as JSON after every step, so it only holds plain data. */
public class RunState {
    public enum Status { FIGHT, REWARD, WON, LOST }

    public String championId;
    public String actId;
    public long seed;
    public int floor = 0;
    public int hp;
    public int maxHp;
    /** The deck as card names, one entry per copy. Does not include the champion. */
    public List<String> deck = new ArrayList<>();
    /** Enemy for the current floor. */
    public String enemyId;
    /** Enemies already fought this run, so they don't repeat. */
    public List<String> enemiesSeen = new ArrayList<>();
    /** Card choices offered after the last win. */
    public List<String> rewardOptions = new ArrayList<>();
    public Status status = Status.FIGHT;
    /** One line per finished fight, for the run summary. */
    public List<String> log = new ArrayList<>();
}
