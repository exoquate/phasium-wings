package cc.phasium.wings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /npcskin &lt;npcs&gt; link &lt;url&gt; [slim|breit] and /npcskin &lt;npcs&gt; spieler &lt;name&gt;: sets the skin of Cobblemon NPCs
 * from a link or from a Minecraft account. The skin lives in the NPC's own data (NPCPlayerTexture), so changing it
 * never needs a modpack update. The image is downloaded in the background and checked before it is used.
 */
public final class NpcSkinCommand {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int MAX_BYTES = 100_000;

    private record Skin(byte[] png, boolean slim) {}

    private NpcSkinCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("npcskin").requires(src -> src.hasPermission(2))
                .then(Commands.argument("npc", EntityArgument.entities())
                        .then(Commands.literal("link")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(ctx -> fromLink(ctx, false))
                                        .then(Commands.argument("arme", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("slim", "breit"), b))
                                                .executes(ctx -> fromLink(ctx, StringArgumentType.getString(ctx, "arme").equals("slim"))))))
                        .then(Commands.literal("spieler")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(NpcSkinCommand::fromPlayer)))));
    }

    private static int fromLink(CommandContext<CommandSourceStack> ctx, boolean slim) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> npcs = EntityArgument.getEntities(ctx, "npc");
        String url = StringArgumentType.getString(ctx, "url");
        ctx.getSource().sendSuccess(() -> Component.literal("§7Lade den Skin..."), false);
        apply(ctx.getSource(), npcs, download(url).thenApply(png -> new Skin(png, slim)));
        return 1;
    }

    private static int fromPlayer(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> npcs = EntityArgument.getEntities(ctx, "npc");
        String name = StringArgumentType.getString(ctx, "name");
        ctx.getSource().sendSuccess(() -> Component.literal("§7Lade den Skin von " + name + "..."), false);
        // Mojang: name -> uuid -> profile with the base64 textures entry (skin url and the slim flag)
        CompletableFuture<Skin> skin = get("https://api.mojang.com/users/profiles/minecraft/" + name)
                .thenCompose(body -> get("https://sessionserver.mojang.com/session/minecraft/profile/"
                        + JsonParser.parseString(new String(body)).getAsJsonObject().get("id").getAsString()))
                .thenCompose(body -> {
                    JsonObject profile = JsonParser.parseString(new String(body)).getAsJsonObject();
                    String value = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                    JsonObject skinInfo = JsonParser.parseString(new String(Base64.getDecoder().decode(value))).getAsJsonObject()
                            .getAsJsonObject("textures").getAsJsonObject("SKIN");
                    boolean slim = skinInfo.has("metadata") && skinInfo.getAsJsonObject("metadata").get("model").getAsString().equals("slim");
                    return download(skinInfo.get("url").getAsString()).thenApply(png -> new Skin(png, slim));
                });
        apply(ctx.getSource(), npcs, skin);
        return 1;
    }

    private static CompletableFuture<byte[]> get(String url) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", "Phasium").build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApply(res -> {
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            return res.body();
        });
    }

    /** Downloads a skin and checks it is a PNG of 64x64 (or old 64x32) pixels and not too big. */
    private static CompletableFuture<byte[]> download(String url) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("kein gültiger Link"));
        }
        return get(url).thenApply(png -> {
            if (png.length > MAX_BYTES) throw new IllegalStateException("Bild zu groß");
            try {
                var img = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
                if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) {
                    throw new IllegalStateException("kein Skin Bild (64x64 PNG)");
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Bild nicht lesbar");
            }
            return png;
        });
    }

    private static void apply(CommandSourceStack src, Collection<? extends Entity> npcs, CompletableFuture<Skin> skin) {
        skin.whenComplete((s, err) -> src.getServer().execute(() -> {
            if (err != null) {
                Throwable cause = err.getCause() != null ? err.getCause() : err;
                src.sendFailure(Component.literal("Skin konnte nicht geladen werden: " + cause.getMessage()));
                return;
            }
            int n = 0;
            for (Entity e : npcs) {
                if (!e.isAlive() || !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("cobblemon:npc")) continue;
                // the same as /data merge: write the texture and the arm model into the NPC's data
                CompoundTag patch = new CompoundTag();
                CompoundTag texture = new CompoundTag();
                texture.putString("Model", s.slim() ? "SLIM" : "DEFAULT");
                texture.put("Texture", new ByteArrayTag(s.png()));
                patch.put("NPCPlayerTexture", texture);
                ListTag aspects = new ListTag();
                aspects.add(StringTag.valueOf(s.slim() ? "model-slim" : "model-default"));
                patch.put("AppliedAspects", aspects);
                CompoundTag data = e.saveWithoutId(new CompoundTag());
                data.merge(patch);
                e.load(data);
                n++;
            }
            int count = n;
            if (count == 0) src.sendFailure(Component.literal("Kein Cobblemon NPC ausgewählt."));
            else src.sendSuccess(() -> Component.literal("§dSkin bei " + count + " NPC gesetzt (" + (s.slim() ? "schmale" : "breite") + " Arme)."), true);
        }));
    }
}
