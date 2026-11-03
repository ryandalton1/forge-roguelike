package forge.adventure.roguelike;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.scene.UIScene;
import forge.adventure.util.Controls;
import forge.gamemodes.roguelike.BattleResult;
import forge.gamemodes.roguelike.BattleSpec;
import forge.gamemodes.roguelike.EnemySpec;
import forge.gamemodes.roguelike.RoguelikeContent;

import java.util.List;

/**
 * Playtest screen: pick an enemy, play one fast fight, see the result. HP carries over between fights
 * (the run rule) until you press Reset HP.
 */
public class RoguelikeTestScene extends UIScene {
    public static final String PLAYER_DECK = "decks/test_player_wu_flyers.dck";
    public static final int MAX_HP = 30;

    private static RoguelikeTestScene object;
    private final TextraLabel status;
    private final Table enemyTable = new Table();
    private int runHp = MAX_HP;
    private String lastResult = "";

    public static RoguelikeTestScene instance() {
        if (object == null) {
            object = new RoguelikeTestScene();
        }
        return object;
    }

    private RoguelikeTestScene() {
        super(Forge.isLandscapeMode() ? "ui/roguelike_test.json" : "ui/roguelike_test_portrait.json");
        status = ui.findActor("status");
        ScrollPane pane = ui.findActor("enemies");
        pane.setActor(enemyTable);
        ui.onButtonPress("back", () -> Forge.switchToLast());
        ui.onButtonPress("resetHp", () -> {
            runHp = MAX_HP;
            lastResult = "HP reset.";
            refresh();
        });
    }

    @Override
    public void enter() {
        RoguelikeContent.loadCustomCards();
        refresh();
        super.enter();
    }

    private void refresh() {
        status.setText("[%95]Run HP: " + runHp + "/" + MAX_HP + (lastResult.isEmpty() ? "" : "   Last fight: " + lastResult));
        enemyTable.clear();
        List<EnemySpec> enemies = RoguelikeContent.loadEnemies();
        if (enemies.isEmpty()) {
            enemyTable.add(Controls.newTextraLabel("No enemies found in " + RoguelikeContent.ROOT + "enemies"));
            return;
        }
        for (EnemySpec e : enemies) {
            // Textra treats [square brackets] as markup, so the tier is written without them.
            String label = e.tier.toUpperCase() + ": " + e.displayName() + "  (life " + e.life + ")";
            enemyTable.add(Controls.newTextButton(label, () -> fight(e))).width(220).height(24).pad(2);
            TextraLabel intro = Controls.newTextraLabel("[%75]" + (e.intro == null ? "" : e.intro));
            intro.setWrap(true);
            enemyTable.add(intro).width(230).left().pad(2);
            enemyTable.row();
        }
    }

    private void fight(EnemySpec enemy) {
        if (runHp <= 0) {
            lastResult = "You're dead. Press Reset HP.";
            refresh();
            return;
        }
        BattleSpec spec = new BattleSpec();
        spec.playerDeck = RoguelikeContent.loadDeck(PLAYER_DECK);
        spec.playerLife = runHp;
        spec.enemy = enemy;
        RunDuelScene.instance().start(spec, this::onResult);
    }

    private void onResult(BattleResult result) {
        // A lost fight ends the run, even if it was lost by conceding with life left.
        runHp = result.won ? Math.max(0, Math.min(MAX_HP, result.playerLifeEnd)) : 0;
        lastResult = result.toString() + (runHp == 0 ? "  RUN OVER" : "");
        System.out.println("[roguelike] " + lastResult);
        refresh();
    }
}
