package cc.phasium.wings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server side: reads the wings from config/phasium_wings/wings. On the first start the folder is filled with the wings
 * that come with the mod. A new wing is just a new &lt;key&gt;.json + &lt;key&gt;.png in that folder and /fluegelreload.
 * The optional file order.txt lists the keys in the order they should appear; wings not listed come after it.
 */
public final class WingRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** The wings that come with the mod: key, German name, style, spread, lift, feather colour, sparkle colours. */
    private record Seed(String key, String name, String style, float spread, float lift, String feathers, String[] sparks) {}

    private static final Seed[] SEEDS = {
            new Seed("monarch", "Monarchflügel", "butterfly", 20, 0, null, new String[0]),
            new Seed("phasiumnacht", "Phasium Nachtflügel", "butterfly", 20, 0, null, new String[]{"#BEA0FF", "#FFAAD2", "#FFC86E", "#FFFFFF"}),
            new Seed("papinella", "Papinella Flügel", "butterfly", 20, 0, null, new String[0]),
            new Seed("fee2", "Feenflügel", "butterfly", 20, 0, null, new String[0]),
            new Seed("drache", "Drachenflügel", "feather", 20, 6, null, new String[0]),
            new Seed("feuerdrache", "Feuerdrachenflügel", "feather", 20, 6, null, new String[0]),
            new Seed("engel6", "Engelsflügel", "feather", 22, 0, "#FFFFFF", new String[0]),
            new Seed("engel6gold", "Goldene Engelsflügel", "feather", 22, 0, "#FFD27A", new String[0]),
            new Seed("engel6dunkel", "Dunkle Engelsflügel", "feather", 22, 0, "#3A2E48", new String[0]),
            new Seed("booster", "Booster Flügel", "butterfly", 20, 0, null, new String[0]),
            new Seed("boosterengel6", "Booster Engelsflügel", "feather", 22, 0, "#FFB4E6", new String[0]),
            new Seed("booster_galaxie", "Booster Galaxieflügel", "butterfly", 20, 0, null, new String[0]),
            new Seed("joyengel", "Joy Engelsflügel", "feather", 22, 0, "#FFB4D8", new String[0]),
            new Seed("eisengel", "Eisengelsflügel", "feather", 22, 0, "#BFE8FF", new String[]{"#BFE8FF", "#8FD3FF", "#FFFFFF"}),
            new Seed("smaragdfalter", "Smaragdfalterflügel", "butterfly", 20, 0, null, new String[]{"#7DFFB0", "#3FE08A", "#E6FFF0"}),
    };

    private WingRegistry() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("phasium_wings").resolve("wings");
    }

    /** Loads (and on the first start creates) the wing folder; returns how many wings were loaded. */
    public static int load() {
        Path dir = dir();
        try {
            if (!Files.isDirectory(dir)) seed(dir);
            List<String> order = new ArrayList<>();
            Path orderFile = dir.resolve("order.txt");
            if (Files.exists(orderFile)) for (String l : Files.readAllLines(orderFile)) if (!l.isBlank()) order.add(l.trim());
            List<String> keys = new ArrayList<>();
            try (var files = Files.list(dir)) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).sorted().forEach(keys::add);
            }
            keys.sort((a, b) -> Integer.compare(order.contains(a) ? order.indexOf(a) : 10_000, order.contains(b) ? order.indexOf(b) : 10_000));
            List<WingType> types = new ArrayList<>();
            for (String key : keys) {
                Path png = dir.resolve(key + ".png");
                if (!Files.exists(png)) { PhasiumWings.LOG.warn("[Flügel] {} hat kein Bild ({}.png), übersprungen", key, key); continue; }
                try {
                    JsonObject json = JsonParser.parseString(Files.readString(dir.resolve(key + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
                    types.add(WingType.create(key, json, Files.readAllBytes(png)));
                } catch (Exception e) {
                    PhasiumWings.LOG.warn("[Flügel] {} konnte nicht gelesen werden: {}", key, e.getMessage());
                }
            }
            WingType.replaceAll(types);
            return types.size();
        } catch (Exception e) {
            PhasiumWings.LOG.error("[Flügel] Ordner {} konnte nicht gelesen werden", dir, e);
            return 0;
        }
    }

    /** First start: writes the wings that ship with the mod into the folder. */
    private static void seed(Path dir) throws Exception {
        Files.createDirectories(dir);
        List<String> order = new ArrayList<>();
        for (Seed s : SEEDS) {
            JsonObject geometry;
            byte[] png;
            try (InputStream g = WingRegistry.class.getResourceAsStream("/assets/phasium_wings/planes/" + s.key() + ".json");
                 InputStream p = WingRegistry.class.getResourceAsStream("/assets/phasium_wings/textures/entity/wings_" + s.key() + ".png")) {
                if (g == null || p == null) continue;
                geometry = JsonParser.parseReader(new InputStreamReader(g, StandardCharsets.UTF_8)).getAsJsonObject();
                png = p.readAllBytes();
            }
            JsonObject json = new JsonObject();
            json.addProperty("name", s.name());
            json.addProperty("style", s.style());
            json.addProperty("spread", s.spread());
            json.addProperty("lift", s.lift());
            if (s.feathers() != null) json.addProperty("feathers", s.feathers());
            if (s.sparks().length > 0) json.add("sparks", GSON.toJsonTree(s.sparks()));
            json.add("geometry", geometry);
            Files.writeString(dir.resolve(s.key() + ".json"), GSON.toJson(json), StandardCharsets.UTF_8);
            Files.write(dir.resolve(s.key() + ".png"), png);
            order.add(s.key());
        }
        Files.write(dir.resolve("order.txt"), order);
    }
}
