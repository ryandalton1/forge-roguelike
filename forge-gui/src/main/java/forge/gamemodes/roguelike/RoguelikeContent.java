package forge.gamemodes.roguelike;

import com.google.gson.Gson;
import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Loads roguelike content from res/roguelike/: custom card scripts, enemies and decks.
 * Plain Java with no libGDX, so a different front end can reuse it.
 */
public final class RoguelikeContent {
    public static final String ROOT = ForgeConstants.RES_DIR + "roguelike" + File.separator;
    /** Every roguelike-only card is named with this prefix so it can't collide with a real card. */
    public static final String CARD_PREFIX = "RL ";
    public static final String SUDDEN_DEATH = "RL Sudden Death";

    private static boolean customCardsLoaded = false;
    private static final Gson GSON = new Gson();

    private RoguelikeContent() { }

    /** Register res/roguelike/custom_cards/*.txt with the card database. Safe to call more than once. */
    public static synchronized void loadCustomCards() {
        if (customCardsLoaded) {
            return;
        }
        customCardsLoaded = true;
        File[] files = new File(ROOT + "custom_cards").listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            System.err.println("[roguelike] no custom_cards folder at " + ROOT);
            return;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        CardRules.Reader reader = new CardRules.Reader();
        for (File f : files) {
            reader.reset();
            List<String> lines = FileUtil.readAllLines(f, true);
            CardRules rules = reader.readCard(lines, f.getName().replace(".txt", ""));
            rules.setCustom();
            PaperCard card = new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special);
            CardDb db = rules.isVariant() ? StaticData.instance().getVariantCards() : StaticData.instance().getCommonCards();
            if (db.getCard(rules.getName()) == null) {
                db.addCard(card);
            }
        }
    }

    /** Find a real or custom card by name, in the main card db or the variant db (schemes, avatars). */
    public static PaperCard card(String name) {
        PaperCard c = StaticData.instance().getCommonCards().getCard(name);
        if (c == null) {
            c = StaticData.instance().getVariantCards().getCard(name);
        }
        if (c == null) {
            throw new IllegalArgumentException("[roguelike] unknown card: " + name);
        }
        return c;
    }

    public static List<PaperCard> cards(Iterable<String> names) {
        List<PaperCard> out = new ArrayList<>();
        for (String n : names) {
            out.add(card(n));
        }
        return out;
    }

    public static List<PaperCard> cards(String[] names) {
        return names == null ? new ArrayList<>() : cards(Arrays.asList(names));
    }

    public static Deck loadDeck(String relativePath) {
        File f = new File(ROOT + relativePath);
        Deck d = DeckSerializer.fromFile(f);
        if (d == null) {
            throw new IllegalArgumentException("[roguelike] can't read deck " + f);
        }
        return d;
    }

    /** All enemies in res/roguelike/enemies, sorted by tier then name. */
    public static List<EnemySpec> loadEnemies() {
        List<EnemySpec> out = new ArrayList<>();
        File[] files = new File(ROOT + "enemies").listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            try {
                EnemySpec e = GSON.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), EnemySpec.class);
                if (e.id == null) {
                    e.id = f.getName().replace(".json", "");
                }
                out.add(e);
            } catch (IOException | RuntimeException ex) {
                System.err.println("[roguelike] bad enemy file " + f + ": " + ex.getMessage());
            }
        }
        out.sort(Comparator.comparingInt((EnemySpec e) -> tierOrder(e.tier)).thenComparing(EnemySpec::displayName));
        return out;
    }

    private static int tierOrder(String tier) {
        if ("elite".equals(tier)) return 1;
        if ("boss".equals(tier)) return 2;
        return 0;
    }
}
