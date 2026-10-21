package cc.phasium.wings.client;

import cc.phasium.wings.PhasiumWings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Phasium live pack (Pokémon variants, menu images, ...) as an always-on resource pack that is
 * downloaded from the Phasium website, so new content does not need a new modpack version.
 * The download happens once per game start, right before the first resource load; when the server
 * is not reachable the last downloaded pack is used.
 */
public final class LivePack implements RepositorySource {
    private static final String BASE = "https://mc.phasium.cc/";
    private static final String NAME = "phasium-live";
    private static boolean checked = false;

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("phasium");
    }

    private static Path zip() {
        return dir().resolve(NAME + ".zip");
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        update();
        Path zip = zip();
        if (!Files.isRegularFile(zip)) return;
        PackLocationInfo info = new PackLocationInfo(PhasiumWings.MOD_ID + "/" + NAME, Component.literal("Phasium Live-Paket"),
                PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new FilePackResources.FileResourcesSupplier(zip), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) onLoad.accept(pack);
    }

    /** Fetches the current sha1 and downloads the zip only when it changed. Never throws. */
    private static synchronized void update() {
        if (checked) return;
        checked = true;
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
            String remote = http.send(HttpRequest.newBuilder(URI.create(BASE + NAME + ".sha1")).timeout(Duration.ofSeconds(4)).build(),
                    HttpResponse.BodyHandlers.ofString()).body().trim().toLowerCase();
            if (!remote.matches("[0-9a-f]{40}")) return;
            Path zip = zip();
            if (Files.isRegularFile(zip) && remote.equals(sha1(zip))) return;
            Files.createDirectories(dir());
            Path tmp = dir().resolve(NAME + ".zip.download");
            http.send(HttpRequest.newBuilder(URI.create(BASE + NAME + ".zip")).timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofFile(tmp));
            if (!remote.equals(sha1(tmp))) {
                Files.deleteIfExists(tmp);
                PhasiumWings.LOG.warn("[Live-Paket] Download beschädigt, nutze die alte Version");
                return;
            }
            Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
            PhasiumWings.LOG.info("[Live-Paket] Neue Version geladen ({})", remote);
        } catch (Exception e) {
            PhasiumWings.LOG.warn("[Live-Paket] Konnte nicht prüfen, nutze die vorhandene Version: {}", e.toString());
        }
    }

    private static String sha1(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(Files.readAllBytes(file));
        return HexFormat.of().formatHex(md.digest());
    }
}
