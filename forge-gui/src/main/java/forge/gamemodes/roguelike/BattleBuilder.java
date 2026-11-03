package forge.gamemodes.roguelike;

import forge.LobbyPlayer;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.IPaperCard;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link BattleSpec} into the rules and players Forge needs to start a match.
 * This is the roguelike version of what DuelScene.enter() does for Adventure, without any Adventure state.
 */
public final class BattleBuilder {

    /** The output: hand these to HostedMatch.startMatch(). */
    public static final class Battle {
        public final GameRules rules;
        public final Set<GameType> variants;
        public final RegisteredPlayer human;
        public final RegisteredPlayer enemy;
        public final List<RegisteredPlayer> players;

        Battle(GameRules rules, Set<GameType> variants, RegisteredPlayer human, RegisteredPlayer enemy) {
            this.rules = rules;
            this.variants = variants;
            this.human = human;
            this.enemy = enemy;
            this.players = List.of(enemy, human);
        }
    }

    private BattleBuilder() { }

    public static Battle build(BattleSpec spec, LobbyPlayer humanLobby, LobbyPlayer aiLobby) {
        RoguelikeContent.loadCustomCards();
        Set<GameType> variants = EnumSet.of(GameType.Adventure);
        FastRules fast = spec.fast;

        // Player
        Deck playerDeck = new Deck(spec.playerDeck, "RoguelikePlayerDeck");
        List<IPaperCard> playerBattlefield = new ArrayList<>(takeStartingLands(playerDeck.getMain(), fast.playerStartingLands));
        playerBattlefield.addAll(RoguelikeContent.cards(spec.playerBattlefield));
        List<IPaperCard> playerCommand = new ArrayList<>(RoguelikeContent.cards(spec.playerCommandZone));
        if (fast.turnClock) {
            playerCommand.add(RoguelikeContent.card(RoguelikeContent.SUDDEN_DEATH));
        }

        RegisteredPlayer human = new RegisteredPlayer(playerDeck);
        human.setPlayer(humanLobby);
        human.setTeamNumber(0);
        human.setStartingLife(Math.max(1, spec.playerLife));
        human.setStartingHand(fast.playerStartingHand);
        human.addExtraCardsOnBattlefield(playerBattlefield);
        human.addExtraCardsInCommandZone(playerCommand);

        // Enemy
        EnemySpec e = spec.enemy;
        RegisteredPlayer enemy = new RegisteredPlayer(RoguelikeContent.loadDeck(e.deck));
        enemy.setPlayer(aiLobby);
        enemy.setTeamNumber(1);
        enemy.setStartingLife(Math.max(1, e.life));
        enemy.setStartingHand(e.startingHand);
        enemy.addExtraCardsOnBattlefield(new ArrayList<>(RoguelikeContent.cards(e.startBattlefield)));
        List<IPaperCard> enemyCommand = new ArrayList<>(RoguelikeContent.cards(e.commandZone));
        if (fast.turnClock) {
            enemyCommand.add(RoguelikeContent.card(RoguelikeContent.SUDDEN_DEATH));
        }
        enemy.addExtraCardsInCommandZone(enemyCommand);
        if (e.schemes != null && e.schemes.length > 0) {
            enemy.setSchemes(RoguelikeContent.cards(e.schemes));
        }

        GameRules rules = new GameRules(GameType.Adventure);
        rules.setAppliedVariants(variants);
        rules.setGamesPerMatch(1);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);
        rules.setPlayForAnte(false);
        rules.setAllowCheatShuffle(fast.aiCheatShuffle);

        return new Battle(rules, variants, human, enemy);
    }

    /**
     * Remove up to {@code count} basic lands from the pool and return them. Each pick takes the basic type
     * picked least so far, breaking ties by the most copies left, so a two-color deck gets one of each color.
     */
    static List<PaperCard> takeStartingLands(CardPool main, int count) {
        List<PaperCard> taken = new ArrayList<>();
        Map<String, Integer> takenCount = new HashMap<>();
        for (int i = 0; i < count; i++) {
            PaperCard best = null;
            int bestTaken = Integer.MAX_VALUE;
            int bestLeft = -1;
            for (Map.Entry<PaperCard, Integer> entry : main) {
                PaperCard pc = entry.getKey();
                if (!pc.getRules().getType().isBasicLand()) {
                    continue;
                }
                int t = takenCount.getOrDefault(pc.getName(), 0);
                int left = main.countAll(c -> c.getName().equals(pc.getName()));
                if (t < bestTaken || (t == bestTaken && left > bestLeft)) {
                    best = pc;
                    bestTaken = t;
                    bestLeft = left;
                }
            }
            if (best == null) {
                break;
            }
            main.remove(best, 1);
            taken.add(best);
            takenCount.merge(best.getName(), 1, Integer::sum);
        }
        return taken;
    }
}
