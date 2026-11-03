package forge.view;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.Match;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.roguelike.BattleBuilder;
import forge.gamemodes.roguelike.BattleResult;
import forge.gamemodes.roguelike.BattleSpec;
import forge.gamemodes.roguelike.EnemySpec;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.gamemodes.roguelike.RunController;
import forge.gamemodes.roguelike.RunState;
import forge.model.FModel;
import forge.player.GamePlayerUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Balance harness for the roguelike mode, with an AI standing in for the player.
 *
 * <p>{@code forge rlsim [gamesPerEnemy] [playerLife] [enemyId]} plays every enemy against the champion's starting deck.
 * <p>{@code forge rlsim run [runs]} plays whole runs, taking the first card offered after each win.
 */
public final class RoguelikeSim {
    private static final String CHAMPION = "lyra";
    private static final String ACT = "act1_short";

    private RoguelikeSim() { }

    public static void run(String[] args) {
        FModel.initialize(null, null);
        RoguelikeContent.loadCustomCards();
        if (args.length > 1 && "run".equals(args[1])) {
            simulateRuns(args.length > 2 ? Integer.parseInt(args[2]) : 3);
        } else {
            simulateEnemies(args);
        }
    }

    private static void simulateEnemies(String[] args) {
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int life = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        String only = args.length > 3 ? args[3] : null;
        List<EnemySpec> enemies = RoguelikeContent.loadEnemies();
        System.out.println("[rlsim] " + enemies.size() + " enemies, " + games + " games each, player life " + life);
        for (EnemySpec e : enemies) {
            if (only != null && !only.equals(e.id)) {
                continue;
            }
            int wins = 0, turns = 0, lifeLost = 0, draws = 0;
            for (int i = 0; i < games; i++) {
                BattleSpec spec = RunController.newRun(CHAMPION, ACT, i, false).nextBattle();
                spec.playerLife = life;
                spec.enemy = e;
                Outcome o = play(spec);
                System.out.printf("[rlsim] %-20s game %d: %s, turn %d, player life %d -> %d, enemy life %d, champion cast: %s%s%n",
                        e.id, i + 1, o.draw ? "DRAW" : (o.result.won ? "WIN" : "LOSS"), o.result.turns, life,
                        o.result.playerLifeEnd, o.enemyLife, o.championCast ? "yes" : "no", o.phaseTwo ? ", reached phase two" : "");
                if (o.draw) draws++;
                if (o.result.won) wins++;
                turns += o.result.turns;
                lifeLost += life - o.result.playerLifeEnd;
            }
            System.out.printf("[rlsim] %-20s SUMMARY: %d/%d wins, %d draws, avg %.1f turns, avg life lost %.1f%n",
                    e.id, wins, games, draws, turns / (double) games, lifeLost / (double) games);
        }
    }

    private static void simulateRuns(int runs) {
        int won = 0;
        for (int r = 0; r < runs; r++) {
            RunController rc = RunController.newRun(CHAMPION, ACT, 1000 + r, false);
            while (!rc.isOver()) {
                if (rc.state().status == RunState.Status.REWARD) {
                    rc.chooseReward(rc.state().rewardOptions.get(0));
                    continue;
                }
                rc.applyResult(play(rc.nextBattle()).result);
            }
            if (rc.state().status == RunState.Status.WON) won++;
            System.out.println("[rlsim] run " + (r + 1) + ": " + rc.state().status + ", deck " + rc.state().deck.size() + " cards");
            for (String line : rc.state().log) {
                System.out.println("[rlsim]    " + line);
            }
        }
        System.out.println("[rlsim] RUNS SUMMARY: " + won + "/" + runs + " won");
    }

    private static final class Outcome {
        BattleResult result;
        boolean draw;
        boolean phaseTwo;
        int enemyLife;
        boolean championCast;
    }

    private static Outcome play(BattleSpec spec) {
        EnemySpec e = spec.enemy;
        LobbyPlayer standIn = GamePlayerUtil.createAiPlayer("Player", "Default");
        LobbyPlayer ai = GamePlayerUtil.createAiPlayer(e.displayName(), e.ai == null ? "" : e.ai);
        BattleBuilder.Battle b = BattleBuilder.build(spec, standIn, ai);
        Match match = new Match(b.rules, b.players, "Roguelike " + e.id);
        Game g = match.createGame();
        g.setNoGUIUser();
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> match.startGame(g), 120, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            System.out.println("[rlsim]   timeout, counted as draw");
            g.setGameOver(GameEndReason.Draw);
        } catch (Exception ex) {
            ex.printStackTrace();
            g.setGameOver(GameEndReason.Draw);
        }
        Player human = null, enemy = null;
        for (Player p : g.getRegisteredPlayers()) {
            if (p.getLobbyPlayer() == standIn) human = p; else enemy = p;
        }
        Outcome o = new Outcome();
        o.draw = g.getOutcome() == null || g.getOutcome().isDraw();
        boolean won = !o.draw && g.getOutcome().getWinningLobbyPlayer() == standIn;
        o.result = new BattleResult(e.id, won, human == null ? 0 : Math.max(0, human.getLife()), g.getPhaseHandler().getTurn());
        o.enemyLife = enemy == null ? 0 : enemy.getLife();
        if (human != null && !human.getCommanders().isEmpty()) {
            // The tracked card object stops at the stack once cast, so anything but Command means it was cast.
            o.championCast = human.getCommanders().get(0).getZone().getZoneType() != ZoneType.Command;
        }
        o.phaseTwo = enemy != null && enemy.getCardsIn(ZoneType.Exile).anyMatch(c -> c.getName().endsWith("Phase One"));
        return o;
    }
}
