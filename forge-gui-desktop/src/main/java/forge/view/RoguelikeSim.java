package forge.view;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.Match;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.roguelike.BattleBuilder;
import forge.gamemodes.roguelike.BattleSpec;
import forge.gamemodes.roguelike.EnemySpec;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.model.FModel;
import forge.player.GamePlayerUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Balance harness for the roguelike mode: plays every enemy in res/roguelike/enemies against the test player
 * deck, with an AI standing in for the player, and prints one line per game.
 *
 * <p>Usage: {@code forge rlsim [gamesPerEnemy] [playerLife] [enemyId]}
 */
public final class RoguelikeSim {
    private static final String PLAYER_DECK = "decks/test_player_wu_flyers.dck";

    private RoguelikeSim() { }

    public static void run(String[] args) {
        FModel.initialize(null, null);
        int games = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int life = args.length > 2 ? Integer.parseInt(args[2]) : 30;
        String only = args.length > 3 ? args[3] : null;

        RoguelikeContent.loadCustomCards();
        List<EnemySpec> enemies = RoguelikeContent.loadEnemies();
        System.out.println("[rlsim] " + enemies.size() + " enemies, " + games + " games each, player life " + life);
        for (EnemySpec e : enemies) {
            if (only != null && !only.equals(e.id)) {
                continue;
            }
            int wins = 0, turns = 0, lifeLost = 0, draws = 0;
            for (int i = 0; i < games; i++) {
                BattleSpec spec = new BattleSpec();
                spec.playerDeck = RoguelikeContent.loadDeck(PLAYER_DECK);
                spec.playerLife = life;
                spec.enemy = e;
                LobbyPlayer stand_in = GamePlayerUtil.createAiPlayer("Player", "Default");
                LobbyPlayer ai = GamePlayerUtil.createAiPlayer(e.displayName(), e.ai == null ? "" : e.ai);
                BattleBuilder.Battle b = BattleBuilder.build(spec, stand_in, ai);
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
                    if (p.getLobbyPlayer() == stand_in) human = p; else enemy = p;
                }
                boolean won = g.getOutcome() != null && !g.getOutcome().isDraw()
                        && g.getOutcome().getWinningLobbyPlayer() == stand_in;
                boolean draw = g.getOutcome() == null || g.getOutcome().isDraw();
                int t = g.getPhaseHandler().getTurn();
                int lifeEnd = human == null ? 0 : human.getLife();
                boolean phaseTwo = enemy != null && enemy.getCardsIn(ZoneType.Exile).anyMatch(c -> c.getName().endsWith("Phase One"));
                System.out.printf("[rlsim] %-20s game %d: %s, turn %d, player life %d -> %d, enemy life %d%s%n",
                        e.id, i + 1, draw ? "DRAW" : (won ? "WIN" : "LOSS"), t, life, lifeEnd,
                        enemy == null ? 0 : enemy.getLife(), phaseTwo ? ", reached phase two" : "");
                if (draw) draws++;
                if (won) wins++;
                turns += t;
                lifeLost += life - Math.max(0, lifeEnd);
            }
            System.out.printf("[rlsim] %-20s SUMMARY: %d/%d wins, %d draws, avg %.1f turns, avg life lost %.1f%n",
                    e.id, wins, games, draws, turns / (double) games, lifeLost / (double) games);
        }
    }
}
