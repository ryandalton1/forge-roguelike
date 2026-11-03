package forge.adventure.roguelike;

import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.scene.UIScene;
import forge.adventure.util.Controls;
import forge.card.CardRules;
import forge.gamemodes.roguelike.BattleSpec;
import forge.gamemodes.roguelike.EnemySpec;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.gamemodes.roguelike.RunController;
import forge.gamemodes.roguelike.RunState;
import forge.item.PaperCard;

import java.util.Map;
import java.util.TreeMap;

/**
 * The roguelike run screen: start a run, fight the next enemy, pick a card after each win, and see how the run
 * went. Practice fights below the run let you replay any single enemy without touching the run.
 */
public class RoguelikeTestScene extends UIScene {
    private static final String CHAMPION = "lyra";
    private static final String ACT = "act1_short";

    private static RoguelikeTestScene object;
    private final TextraLabel status;
    private final Table content = new Table();
    private final ScrollPane pane;
    private RunController run;
    private String practiceResult = "";

    public static RoguelikeTestScene instance() {
        if (object == null) {
            object = new RoguelikeTestScene();
        }
        return object;
    }

    private RoguelikeTestScene() {
        super(Forge.isLandscapeMode() ? "ui/roguelike_test.json" : "ui/roguelike_test_portrait.json");
        status = ui.findActor("status");
        pane = ui.findActor("enemies");
        pane.setActor(content);
        content.top();
        ui.onButtonPress("back", () -> Forge.switchToLast());
        ui.onButtonPress("newRun", () -> {
            run = RunController.newRun(CHAMPION, ACT, System.currentTimeMillis());
            refresh();
        });
    }

    @Override
    public void enter() {
        RoguelikeContent.loadCustomCards();
        if (run == null) {
            run = RunController.loadSaved();
        }
        refresh();
        super.enter();
    }

    private void refresh() {
        content.clear();
        if (run == null) {
            status.setText("[%95]No run in progress. Press New Run to start one as Lyra.");
        } else {
            RunState s = run.state();
            String head = run.champion().name + "   HP " + s.hp + "/" + s.maxHp + "   Deck " + s.deck.size()
                    + " cards   Floor " + Math.min(s.floor + 1, run.act().floors.length) + "/" + run.act().floors.length;
            switch (s.status) {
                case FIGHT -> {
                    status.setText("[%95]" + head);
                    showFight();
                }
                case REWARD -> {
                    status.setText("[%95]Victory! Choose a card to add to your deck.   " + head);
                    showRewards();
                }
                case WON -> {
                    status.setText("[%95]RUN WON!   " + head);
                    showLog();
                }
                case LOST -> {
                    status.setText("[%95]Run over.   " + head);
                    showLog();
                }
            }
        }
        showPractice();
        pane.layout();
        pane.setScrollY(0);
    }

    private void showFight() {
        EnemySpec e = run.currentEnemy();
        addHeading("Next fight");
        content.add(Controls.newTextButton("Fight: " + e.displayName() + "  (" + e.tier.toUpperCase() + ", life " + e.life + ")",
                () -> RunDuelScene.instance().start(run.nextBattle(), result -> {
                    run.applyResult(result);
                    refresh();
                }))).width(220).height(26).pad(2);
        content.add(wrapped(e.intro)).width(230).left().pad(2);
        content.row();
        showLog();
        showDeck();
    }

    private void showRewards() {
        addHeading("Rewards");
        for (String name : run.state().rewardOptions) {
            content.add(Controls.newTextButton(name, () -> {
                run.chooseReward(name);
                refresh();
            })).width(220).height(26).pad(2);
            content.add(wrapped(describe(name))).width(230).left().pad(2);
            content.row();
        }
        content.add(Controls.newTextButton("Skip", () -> {
            run.chooseReward(null);
            refresh();
        })).width(220).height(22).pad(2);
        content.row();
        showDeck();
    }

    private void showLog() {
        if (run.state().log.isEmpty()) {
            return;
        }
        addHeading("This run");
        for (String line : run.state().log) {
            content.add(wrapped(line)).colspan(2).width(450).left().pad(1);
            content.row();
        }
    }

    private void showDeck() {
        addHeading("Your deck");
        Map<String, Integer> counts = new TreeMap<>();
        for (String n : run.state().deck) {
            counts.merge(n, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append(",  ");
            sb.append(e.getValue()).append(" ").append(e.getKey());
        }
        content.add(wrapped(sb.toString())).colspan(2).width(450).left().pad(1);
        content.row();
    }

    private void showPractice() {
        addHeading("Practice fights (starting deck at 30 HP, doesn't affect the run)"
                + (practiceResult.isEmpty() ? "" : "   Last: " + practiceResult));
        for (EnemySpec e : RoguelikeContent.loadEnemies()) {
            content.add(Controls.newTextButton(e.tier.toUpperCase() + ": " + e.displayName(), () -> {
                BattleSpec spec = RunController.newRun(CHAMPION, ACT, 0, false).nextBattle();
                spec.enemy = e;
                RunDuelScene.instance().start(spec, result -> {
                    practiceResult = result.toString();
                    refresh();
                });
            })).width(220).height(22).pad(1);
            content.add(wrapped(e.intro)).width(230).left().pad(1);
            content.row();
        }
    }

    private void addHeading(String text) {
        content.add(Controls.newTextraLabel("[%90]" + clean(text))).colspan(2).left().padTop(6).padBottom(2);
        content.row();
    }

    private TextraLabel wrapped(String text) {
        TextraLabel l = Controls.newTextraLabel("[%75]" + clean(text == null ? "" : text));
        l.setWrap(true);
        l.setAlignment(Align.left);
        return l;
    }

    /** Mana cost, type, P/T and rules text for a reward card. */
    private static String describe(String name) {
        PaperCard pc = RoguelikeContent.card(name);
        CardRules r = pc.getRules();
        String pt = r.getType().isCreature() ? "  " + r.getPower() + "/" + r.getToughness() : "";
        return r.getManaCost().getSimpleString() + "  " + r.getType() + pt + ".  " + r.getOracleText().replace("\\n", " ");
    }

    /** Textra reads [..] and {..} as markup, so show them as plain brackets. */
    private static String clean(String s) {
        return s.replace('[', '(').replace(']', ')').replace('{', '(').replace('}', ')');
    }
}
