package forge.gamemodes.roguelike;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The run's rules: which fight comes next, what a win or loss does, and which rewards are offered.
 * Plain Java with no UI, so any front end can drive it.
 */
public final class RunController {
    public static final int REWARD_CHOICES = 3;
    public static final String SAVE_FILE = ForgeConstants.USER_DIR + "roguelike" + File.separator + "run.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final RunState state;
    private final ChampionSpec champion;
    private final ActSpec act;
    /** False for simulated runs, so they never overwrite the player's saved run. */
    private boolean persistent = true;

    private RunController(RunState state) {
        this.state = state;
        this.champion = RoguelikeContent.loadChampion(state.championId);
        this.act = RoguelikeContent.loadAct(state.actId);
    }

    public static RunController newRun(String championId, String actId, long seed) {
        return newRun(championId, actId, seed, true);
    }

    public static RunController newRun(String championId, String actId, long seed, boolean persistent) {
        RunState s = new RunState();
        s.championId = championId;
        s.actId = actId;
        s.seed = seed;
        RunController rc = new RunController(s);
        rc.persistent = persistent;
        s.maxHp = rc.champion.maxHp;
        s.hp = s.maxHp;
        Deck starter = RoguelikeContent.loadDeck(rc.champion.startingDeck);
        for (Map.Entry<PaperCard, Integer> e : starter.getMain()) {
            for (int i = 0; i < e.getValue(); i++) {
                s.deck.add(e.getKey().getName());
            }
        }
        Collections.sort(s.deck);
        rc.pickEnemy();
        rc.save();
        return rc;
    }

    /** The saved run, or null if there is none or it has already ended. */
    public static RunController loadSaved() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) {
            return null;
        }
        try {
            RunState s = GSON.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), RunState.class);
            return s == null ? null : new RunController(s);
        } catch (IOException | RuntimeException e) {
            System.err.println("[roguelike] can't read saved run: " + e.getMessage());
            return null;
        }
    }

    public RunState state() {
        return state;
    }

    public ChampionSpec champion() {
        return champion;
    }

    public ActSpec act() {
        return act;
    }

    public EnemySpec currentEnemy() {
        return RoguelikeContent.loadEnemy(state.enemyId);
    }

    public String currentTier() {
        return state.floor < act.floors.length ? act.floors[state.floor] : "";
    }

    public boolean isOver() {
        return state.status == RunState.Status.WON || state.status == RunState.Status.LOST;
    }

    /** The fight for the current floor. */
    public BattleSpec nextBattle() {
        if (state.status != RunState.Status.FIGHT) {
            throw new IllegalStateException("No fight pending, run status is " + state.status);
        }
        BattleSpec spec = new BattleSpec();
        Deck deck = new Deck("Run deck");
        for (String name : state.deck) {
            deck.getMain().add(RoguelikeContent.card(name));
        }
        spec.playerDeck = deck;
        spec.playerLife = state.hp;
        spec.champion = champion.champion;
        if (champion.powers.length > 0) {
            spec.playerCommandZone.add(champion.powers[0]);
        }
        spec.enemy = currentEnemy();
        return spec;
    }

    /** Apply a finished fight. A win carries your life over and offers rewards; a loss ends the run. */
    public void applyResult(BattleResult result) {
        EnemySpec enemy = currentEnemy();
        state.log.add("Floor " + (state.floor + 1) + ": " + (result.won ? "beat " : "lost to ") + enemy.displayName()
                + " in " + result.turns + " turns, " + state.hp + " -> " + Math.max(0, result.playerLifeEnd) + " HP");
        state.enemiesSeen.add(enemy.id);
        if (!result.won) {
            state.hp = 0;
            state.status = RunState.Status.LOST;
            save();
            return;
        }
        state.hp = Math.max(1, Math.min(state.maxHp, result.playerLifeEnd));
        state.floor++;
        if (state.floor >= act.floors.length) {
            state.status = RunState.Status.WON;
        } else {
            state.rewardOptions = rollRewards();
            state.status = RunState.Status.REWARD;
        }
        save();
    }

    /** Take one of the offered cards, or pass null to skip. Then move to the next fight. */
    public void chooseReward(String cardName) {
        if (state.status != RunState.Status.REWARD) {
            throw new IllegalStateException("No reward pending");
        }
        if (cardName != null && state.rewardOptions.contains(cardName)) {
            state.deck.add(cardName);
            Collections.sort(state.deck);
        }
        state.rewardOptions = new ArrayList<>();
        state.status = RunState.Status.FIGHT;
        pickEnemy();
        save();
    }

    private Random rng(int salt) {
        return new Random(state.seed * 31 + state.floor * 1009L + salt);
    }

    private void pickEnemy() {
        String tier = currentTier();
        List<EnemySpec> options = new ArrayList<>();
        List<EnemySpec> fallback = new ArrayList<>();
        for (EnemySpec e : RoguelikeContent.loadEnemies()) {
            if (tier.equals(e.tier)) {
                fallback.add(e);
                if (!state.enemiesSeen.contains(e.id)) {
                    options.add(e);
                }
            }
        }
        if (options.isEmpty()) {
            options = fallback;
        }
        if (options.isEmpty()) {
            throw new IllegalStateException("[roguelike] no enemies of tier " + tier);
        }
        state.enemyId = options.get(rng(1).nextInt(options.size())).id;
    }

    private List<String> rollRewards() {
        List<String> pool = new ArrayList<>(RoguelikeContent.loadPool(champion.rewardPool));
        Collections.shuffle(pool, rng(2));
        return new ArrayList<>(pool.subList(0, Math.min(REWARD_CHOICES, pool.size())));
    }

    public void save() {
        if (!persistent) {
            return;
        }
        try {
            File f = new File(SAVE_FILE);
            f.getParentFile().mkdirs();
            Files.writeString(f.toPath(), GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[roguelike] can't save run: " + e.getMessage());
        }
    }

    /** Used by BattleBuilder to put the champion in the deck's commander section. */
    static void addChampion(Deck deck, String championName) {
        deck.getOrCreate(DeckSection.Commander).add(RoguelikeContent.card(championName));
    }
}
