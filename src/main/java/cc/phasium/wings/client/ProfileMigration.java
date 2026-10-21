package cc.phasium.wings.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A new modpack version is a new launcher profile, so the Xaero map/waypoints and the key bindings of the old
 * profile were lost. On the first start of a profile this copies them over from the most recently played older
 * Phasium profile next to it (same launcher folder). Runs as preLaunch, before Minecraft and DefaultOptions read
 * options.txt. Only copies, never moves or deletes; runs once per profile (marker in config/phasium).
 */
public final class ProfileMigration implements PreLaunchEntrypoint {
    private static final Logger LOG = LoggerFactory.getLogger("phasium_wings");

    @Override
    public void onPreLaunch() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        try {
            run(FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize());
        } catch (Exception e) {
            LOG.warn("[Phasium] Profile migration failed", e);
        }
    }

    static void run(Path game) throws IOException {
        Path marker = game.resolve("config").resolve("phasium").resolve("profil-uebernahme.txt");
        if (Files.exists(marker)) return;
        // new profile = no Phasium map yet; the launcher (Modrinth App) may already have written an options.txt
        boolean firstStart = !Files.exists(game.resolve("options.txt")) || !hasPhasiumMap(game);
        Path old = findOldProfile(game);
        StringBuilder done = new StringBuilder();
        if (old != null) {
            int maps = copyXaero(old, game);
            if (maps > 0) done.append(maps).append(" Kartenordner");
            if (firstStart && writeOptions(old, game)) done.append(done.length() > 0 ? ", " : "").append("Einstellungen und Tastenbelegung");
            if (firstStart && copyMinimapSettings(old, game)) done.append(done.length() > 0 ? ", " : "").append("Minimap-Einstellungen");
        }
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, old == null ? "kein altes Profil gefunden\n"
                : "uebernommen aus " + old + ": " + (done.length() > 0 ? done : "nichts noetig") + "\n", StandardCharsets.UTF_8);
        if (old != null) LOG.info("[Phasium] Profile migration from {}: {}", old, done.length() > 0 ? done : "nothing needed");
    }

    // --- finding the old profile --------------------------------------------------------------------------------

    /** Sibling profiles (Modrinth/CurseForge: profiles/<name>; Prism/MultiMC: instances/<name>/.minecraft) that
     * contain a Phasium mod, newest options.txt first. */
    static Path findOldProfile(Path game) throws IOException {
        Path parent = game.getParent();
        if (parent == null) return null;
        boolean nested = game.getFileName().toString().equalsIgnoreCase(".minecraft")
                || game.getFileName().toString().equalsIgnoreCase("minecraft");
        Path root = nested ? parent.getParent() : parent;
        if (root == null) return null;
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> s = Files.list(root)) {
            s.filter(Files::isDirectory).forEach(p -> candidates.add(nested ? p.resolve(game.getFileName().toString()) : p));
        }
        Path best = null;
        FileTime bestTime = null;
        for (Path p : candidates) {
            Path c = p.toAbsolutePath().normalize();
            if (c.equals(game) || !Files.isDirectory(c) || !isPhasium(c)) continue;
            Path opts = c.resolve("options.txt");
            if (!Files.exists(opts)) continue;
            FileTime t = Files.getLastModifiedTime(opts);
            if (bestTime == null || t.compareTo(bestTime) > 0) {
                best = c;
                bestTime = t;
            }
        }
        return best;
    }

    static boolean hasPhasiumMap(Path profile) throws IOException {
        for (String part : new String[]{"minimap", "world-map"}) {
            Path dir = profile.resolve("xaero").resolve(part);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                if (s.anyMatch(p -> p.getFileName().toString().toLowerCase().contains("phasium"))) return true;
            }
        }
        return false;
    }

    static boolean isPhasium(Path profile) throws IOException {
        Path mods = profile.resolve("mods");
        if (!Files.isDirectory(mods)) return false;
        try (Stream<Path> s = Files.list(mods)) {
            return s.map(p -> p.getFileName().toString().toLowerCase())
                    .anyMatch(n -> n.startsWith("phasium-wings") || n.startsWith("phasium-assets"));
        }
    }

    // --- Xaero map and waypoints ----------------------------------------------------------------------------------

    /** Copies every world/server folder of xaero/minimap and xaero/world-map that the new profile does not have. */
    static int copyXaero(Path from, Path to) throws IOException {
        int n = 0;
        for (String part : new String[]{"minimap", "world-map"}) {
            Path src = from.resolve("xaero").resolve(part);
            if (!Files.isDirectory(src)) continue;
            Path dst = to.resolve("xaero").resolve(part);
            try (Stream<Path> s = Files.list(src)) {
                for (Path world : (Iterable<Path>) s::iterator) {
                    String name = world.getFileName().toString();
                    if (!Files.isDirectory(world) || name.equals("backup") || Files.exists(dst.resolve(name))) continue;
                    copyTree(world, dst.resolve(name));
                    n++;
                }
            }
        }
        return n;
    }

    /** First start only: the player's own Xaero settings (minimap size/zoom/display in config/xaero, HUD position in
     * config/xaerohud.txt) replace the pack defaults. */
    static boolean copyMinimapSettings(Path from, Path to) throws IOException {
        boolean any = false;
        Path cfg = from.resolve("config").resolve("xaero");
        if (Files.isDirectory(cfg)) {
            copyTree(cfg, to.resolve("config").resolve("xaero"), true);
            any = true;
        }
        Path hud = from.resolve("config").resolve("xaerohud.txt");
        if (Files.isRegularFile(hud)) {
            Files.createDirectories(to.resolve("config"));
            Files.copy(hud, to.resolve("config").resolve("xaerohud.txt"), StandardCopyOption.REPLACE_EXISTING);
            any = true;
        }
        return any;
    }

    static void copyTree(Path src, Path dst) throws IOException {
        copyTree(src, dst, false);
    }

    static void copyTree(Path src, Path dst, boolean replace) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = dst.resolve(src.relativize(file).toString());
                if (replace) Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                else if (!Files.exists(target)) Files.copy(file, target);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // --- key bindings ---------------------------------------------------------------------------------------------

    /** Resource pack lists stay at the pack defaults, so packs that are new in this version are switched on. */
    private static final java.util.Set<String> KEEP_DEFAULT = java.util.Set.of("resourcePacks", "incompatibleResourcePacks");

    /** First start only: options.txt = the pack defaults (DefaultOptions files) with all settings of the old
     * profile on top (key bindings, GUI scale, mouse, sound, video, chat, ...), except the resource pack lists.
     * DefaultOptions then sees an existing options.txt and leaves it alone. */
    static boolean writeOptions(Path from, Path to) throws IOException {
        Map<String, String> old = read(from.resolve("options.txt"));
        if (old.isEmpty()) return false;
        Path defaults = to.resolve("config").resolve("defaultoptions");
        Map<String, String> opts = read(defaults.resolve("options.txt"));
        if (opts.isEmpty()) return false;            // no pack defaults: let Minecraft create its own file
        opts.putAll(read(defaults.resolve("keybindings.txt")));
        opts.putAll(read(to.resolve("options.txt")));     // whatever the launcher already wrote
        old.forEach((k, v) -> { if (!KEEP_DEFAULT.contains(k)) opts.put(k, v); });
        StringBuilder sb = new StringBuilder();
        opts.forEach((k, v) -> sb.append(k).append(':').append(v).append('\n'));
        Files.writeString(to.resolve("options.txt"), sb.toString(), StandardCharsets.UTF_8);
        return true;
    }

    static Map<String, String> read(Path file) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        if (!Files.exists(file)) return m;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int i = line.indexOf(':');
            if (i > 0) m.put(line.substring(0, i), line.substring(i + 1));
        }
        return m;
    }
}
