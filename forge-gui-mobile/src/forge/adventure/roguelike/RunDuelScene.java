package forge.adventure.roguelike;

import com.badlogic.gdx.Gdx;
import forge.Adventure;
import forge.Forge;
import forge.LobbyPlayer;
import forge.adventure.scene.ForgeScene;
import forge.game.Game;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.roguelike.BattleBuilder;
import forge.gamemodes.roguelike.BattleResult;
import forge.gamemodes.roguelike.BattleSpec;
import forge.gui.FThreads;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.player.PlayerControllerHuman;
import forge.screens.FScreen;
import forge.screens.match.MatchController;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;
import forge.toolbox.FOptionPane;
import forge.trackable.TrackableCollection;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hosts one roguelike fight from a {@link BattleSpec}. Unlike DuelScene it does not touch any Adventure
 * state (AdventurePlayer, world, sprites), so it can run without an Adventure save loaded.
 */
public class RunDuelScene extends ForgeScene {
    private static RunDuelScene object;
    /** True from start() until the match result has been handed back. MatchController checks it. */
    private static boolean active = false;

    /**
     * Preferences we set for the fight, and restore afterwards. Auto-pass skips priority stops where you have
     * nothing to do, but it still stops when the enemy casts a spell, targets you or your permanents, or attacks,
     * so you can see what happened. Phase and resolve delays stay on so the enemy's turn is readable.
     */
    private static final Map<FPref, String> FIGHT_PREFS = new EnumMap<>(Map.of(
            FPref.YIELD_AUTO_PASS_NO_ACTIONS, "true",
            FPref.YIELD_AUTO_PASS_RESPECTS_INTERRUPTS, "true",
            FPref.YIELD_INTERRUPT_ON_OPPONENT_SPELL, "true",
            FPref.YIELD_INTERRUPT_ON_TARGETING, "true",
            FPref.YIELD_INTERRUPT_ON_ATTACKERS, "true",
            FPref.YIELD_SKIP_PHASE_DELAY, "false",
            FPref.YIELD_SKIP_RESOLVE_DELAY, "false"
    ));

    private BattleSpec spec;
    private Consumer<BattleResult> onEnd;
    private HostedMatch hostedMatch;
    private BattleBuilder.Battle battle;
    private final Map<FPref, String> savedPrefs = new EnumMap<>(FPref.class);
    private boolean ended;

    public static RunDuelScene instance() {
        if (object == null) {
            object = new RunDuelScene();
        }
        return object;
    }

    public static boolean isActive() {
        return active;
    }

    private RunDuelScene() { }

    /** Start a fight. {@code onEnd} runs on the UI thread after the match, once this scene has been left. */
    public void start(BattleSpec spec, Consumer<BattleResult> onEnd) {
        this.spec = spec;
        this.onEnd = onEnd;
        this.ended = false;
        active = true;
        Forge.switchScene(this);
    }

    @Override
    public FScreen getScreen() {
        return MatchController.getView();
    }

    @Override
    public void enter() {
        Adventure.getInstance().renderTransitionScreen = false;
        SoundSystem.instance.stopBackgroundMusic();
        LobbyPlayer human = GamePlayerUtil.getGuiPlayer();
        LobbyPlayer ai = GamePlayerUtil.createAiPlayer(spec.enemy.displayName(), spec.enemy.ai == null ? "" : spec.enemy.ai);
        battle = BattleBuilder.build(spec, human, ai);

        applyFastPrefs();

        Map<RegisteredPlayer, IGuiGame> guiMap = new HashMap<>();
        guiMap.put(battle.human, MatchController.instance);
        hostedMatch = MatchController.hostMatch();
        boolean boss = "boss".equals(spec.enemy.tier);
        hostedMatch.startMatch(battle.rules, battle.variants, battle.players, guiMap, boss ? MusicPlaylist.BOSS : MusicPlaylist.MATCH);
        MatchController.instance.setGameView(hostedMatch.getGameView());
        for (Player p : hostedMatch.getGame().getPlayers()) {
            if (p.getController() instanceof PlayerControllerHuman) {
                PlayerControllerHuman hc = (PlayerControllerHuman) p.getController();
                hc.setGui(MatchController.instance);
                MatchController.instance.setOriginalGameController(p.getView(), hc);
                MatchController.instance.openView(new TrackableCollection<>(p.getView()));
            }
        }
        super.enter();
        String intro = spec.enemy.intro;
        if (intro != null && !intro.isEmpty()) {
            FThreads.delayInEDT(500, () -> FOptionPane.showMessageDialog(intro, spec.enemy.displayName()));
        }
    }

    /** Called by MatchController / AdventureWinLose when the match is over (in place of DuelScene.GameEnd). */
    public void onMatchEnd() {
        if (ended) {
            return;
        }
        ended = true;
        BattleResult result = readResult();
        restorePrefs();
        active = false;
        Gdx.app.postRunnable(() -> {
            Forge.clearTransitionScreen();
            Forge.clearScreenStack();
            Forge.switchToLast();
            if (onEnd != null) {
                onEnd.accept(result);
            }
        });
    }

    @Override
    public boolean leave() {
        Adventure.getInstance().renderTransitionScreen = true;
        return super.leave();
    }

    private BattleResult readResult() {
        Game game = hostedMatch.getGame();
        boolean won = false;
        int life = 0;
        int turns = 0;
        try {
            for (Player p : game.getRegisteredPlayers()) {
                if (p.getController() instanceof PlayerControllerHuman) {
                    life = p.getLife();
                }
            }
            won = game.getOutcome() != null && game.getOutcome().isWinner(battle.human.getPlayer());
            turns = game.getPhaseHandler().getTurn();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return new BattleResult(spec.enemy.id, won, Math.max(0, life), turns);
    }

    private void applyFastPrefs() {
        savedPrefs.clear();
        for (Map.Entry<FPref, String> e : FIGHT_PREFS.entrySet()) {
            savedPrefs.put(e.getKey(), FModel.getPreferences().getPref(e.getKey()));
            FModel.getPreferences().setPref(e.getKey(), e.getValue());
        }
    }

    private void restorePrefs() {
        for (Map.Entry<FPref, String> e : savedPrefs.entrySet()) {
            FModel.getPreferences().setPref(e.getKey(), e.getValue());
        }
        savedPrefs.clear();
    }
}
