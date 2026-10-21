package cc.phasium.wings;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;


/**
 * Cosmetics without items, so nobody can hand them on: wings, hair, hats, belts and jackets, one piece per slot and
 * all slots at the same time (see {@link WingType#SLOTS}). A player picks one with /fluegelwahl &lt;key&gt; if they have
 * the permission phasium.fluegel.&lt;key&gt;; the piece goes into its slot. The choice is stored on the player and sent
 * to every client, which draws it (see the client package). The menus themselves are SkiesGUIs menus on the server
 * that run this command.
 */
public class PhasiumWings implements ModInitializer {
    public static final String MOD_ID = "phasium_wings";

    /** Old storage (wings only), still read once and moved into {@link #WORN}. */
    public static final AttachmentType<String> WING = AttachmentRegistry.<String>builder()
            .persistent(Codec.STRING).copyOnDeath().buildAndRegister(id("wing"));
    /** Worn cosmetics, slot -> key, saved with the player (or NPC) and kept on death. */
    public static final AttachmentType<java.util.Map<String, String>> WORN = AttachmentRegistry.<java.util.Map<String, String>>builder()
            .persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath().buildAndRegister(id("kosmetik"));

    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("phasium_wings");

    /** Feathers that some wings lose, and glowing sparkles some leave behind; the colour comes with each particle. */
    public static final net.minecraft.core.particles.ParticleType<net.minecraft.core.particles.ColorParticleOption> FEATHER =
            net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, id("feder"),
                    net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.complex(false,
                            net.minecraft.core.particles.ColorParticleOption::codec, net.minecraft.core.particles.ColorParticleOption::streamCodec));
    public static final net.minecraft.core.particles.ParticleType<net.minecraft.core.particles.ColorParticleOption> SPARK =
            net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE, id("funke"),
                    net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.complex(true,
                            net.minecraft.core.particles.ColorParticleOption::codec, net.minecraft.core.particles.ColorParticleOption::streamCodec));

    /** Players who get their wings sent once more shortly after joining (uuid -> server tick). */
    private static final java.util.Map<java.util.UUID, Integer> RESYNC = new java.util.concurrent.ConcurrentHashMap<>();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(WingSyncPayload.TYPE, WingSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WingDefPayload.TYPE, WingDefPayload.CODEC);

        // the wings come from config/phasium_wings/wings (filled with the built-in ones on the first start)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server ->
                LOG.info("[Flügel] {} Flügel geladen", WingRegistry.load()));

        // a joining player learns everyone's wings, and everyone learns theirs
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            sendDefinitions(joined);
            for (ServerPlayer other : PlayerLookup.all(server)) {
                if (other != joined) send(joined, other);
            }
            broadcast(server, joined);
            // The first sync can arrive before the client is fully in the world and get lost, which looked like the
            // wings were gone after logging in again. A second round two seconds later makes sure everyone has them.
            RESYNC.put(joined.getUUID(), server.getTickCount() + 40);
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (RESYNC.isEmpty()) return;
            int now = server.getTickCount();
            RESYNC.entrySet().removeIf(e -> {
                if (e.getValue() > now) return false;
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    // checked here and not on JOIN: right at JOIN LuckPerms did not always know the player's rank yet,
                    // and shiny players lost their wings on every login (2026-10-19)
                    dropIfNotAllowed(p);
                    for (ServerPlayer other : PlayerLookup.all(server)) if (other != p) send(p, other);
                    broadcast(server, p);
                }
                return true;
            });
        });

        // anyone (players and NPCs) sends their wings to a player who starts seeing them
        net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            migrate(entity);
            if (entity.hasAttached(WORN) && ServerPlayNetworking.canSend(player, WingSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload(entity));
            }
        });

        // admins: /fluegelreload reads the wing folder again and sends the wings to everyone online
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("fluegelreload").requires(src -> src.hasPermission(2)).executes(ctx -> {
                    int n = WingRegistry.load();
                    MinecraftServer server = ctx.getSource().getServer();
                    for (ServerPlayer p : PlayerLookup.all(server)) sendDefinitions(p);
                    for (ServerPlayer p : PlayerLookup.all(server)) broadcast(server, p);
                    for (net.minecraft.server.level.ServerLevel level : server.getAllLevels())
                        for (net.minecraft.world.entity.Entity e : level.getAllEntities())
                            if (!(e instanceof ServerPlayer) && (e.hasAttached(WING) || e.hasAttached(WORN))) {
                                migrate(e);
                                sendTracking(e);
                            }
                    ctx.getSource().sendSuccess(() -> Component.literal("§d" + n + " Flügel neu geladen und an alle geschickt."), true);
                    return n;
                })));

        // admins: /npcskin sets Cobblemon NPC skins from a link or a Minecraft account
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> NpcSkinCommand.register(dispatcher));

        // admins: /fluegelnpc <entities> <key> puts a cosmetic on NPCs (or any other entity) into its slot,
        // /fluegelnpc <entities> aus [slot] takes off everything or one slot
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("fluegelnpc").requires(src -> src.hasPermission(2))
                        .then(Commands.argument("ziel", net.minecraft.commands.arguments.EntityArgument.entities())
                                .then(Commands.literal("aus")
                                        .executes(ctx -> npc(ctx, null, null))
                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(WingType.SLOTS, builder))
                                                .executes(ctx -> npc(ctx, null, StringArgumentType.getString(ctx, "slot")))))
                                .then(Commands.argument("fluegel", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(WingType.all().stream().map(t -> t.key), builder))
                                        .executes(ctx -> {
                                            WingType type = WingType.byKey(StringArgumentType.getString(ctx, "fluegel"));
                                            if (type == null) {
                                                ctx.getSource().sendFailure(Component.literal("Das gibt es nicht."));
                                                return 0;
                                            }
                                            return npc(ctx, type, null);
                                        })))));

        // players: /kosmetikaus [slot|alle] takes off one slot (default: everything)
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("kosmetikaus")
                        .executes(ctx -> takeOff(ctx.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(WingType.SLOTS.stream(), java.util.stream.Stream.of("alle")), builder))
                                .executes(ctx -> {
                                    String slot = StringArgumentType.getString(ctx, "slot");
                                    return takeOff(ctx.getSource().getPlayerOrException(), slot.equals("alle") ? null : slot);
                                }))));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("fluegelwahl")
                        .then(Commands.argument("fluegel", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        WingType.all().stream().filter(t -> Permissions.check(ctx.getSource(), t.permission(), 2))
                                                .map(t -> t.key), builder))
                                .executes(ctx -> choose(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "fluegel"))))));
    }

    private static int npc(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, WingType type, String slot)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (slot != null && !WingType.SLOTS.contains(slot)) {
            ctx.getSource().sendFailure(Component.literal("Slots: " + String.join(", ", WingType.SLOTS)));
            return 0;
        }
        int n = 0;
        for (net.minecraft.world.entity.Entity e : net.minecraft.commands.arguments.EntityArgument.getEntities(ctx, "ziel")) {
            migrate(e);
            java.util.Map<String, String> worn = worn(e);
            if (type != null) worn.put(type.slot, type.key);
            else if (slot != null) worn.remove(slot);
            else worn.clear();
            store(e, worn);
            sendTracking(e);
            n++;
        }
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Kosmetik bei " + count + " Wesen gesetzt."), true);
        return n;
    }

    private static int choose(ServerPlayer player, String key) {
        // "aus" takes the wings off (the old menu button); clicking what you already wear takes it off
        if (key.equals("aus")) return takeOff(player, "fluegel");
        java.util.Map<String, String> worn = worn(player);
        WingType type = WingType.byKey(key);
        if (type == null) {
            player.sendSystemMessage(Component.literal("§cDas gibt es nicht."));
            return 0;
        }
        if (key.equals(worn.get(type.slot))) {
            worn.remove(type.slot);
            store(player, worn);
            broadcast(player.server, player);
            player.sendSystemMessage(Component.literal("§7Du hast " + type.name + " abgelegt."));
            return 1;
        }
        if (!Permissions.check(player, type.permission(), 2)) {
            player.sendSystemMessage(Component.literal("§c" + type.name + " hast du noch nicht freigeschaltet."));
            return 0;
        }
        worn.put(type.slot, type.key);
        store(player, worn);
        broadcast(player.server, player);
        player.sendSystemMessage(Component.literal("§dDu trägst jetzt " + type.name + "."));
        return 1;
    }

    /** Takes off one slot, or everything for null. */
    private static int takeOff(ServerPlayer player, String slot) {
        if (slot != null && !WingType.SLOTS.contains(slot)) {
            player.sendSystemMessage(Component.literal("§cSlots: " + String.join(", ", WingType.SLOTS) + ", alle"));
            return 0;
        }
        java.util.Map<String, String> worn = worn(player);
        if (slot == null) worn.clear(); else worn.remove(slot);
        store(player, worn);
        broadcast(player.server, player);
        player.sendSystemMessage(Component.literal(slot == null ? "§7Du hast alles abgelegt." : "§7Abgelegt."));
        return 1;
    }

    /** What an entity wears (slot -> key), as a copy that can be changed and stored again. */
    private static java.util.Map<String, String> worn(net.minecraft.world.entity.Entity e) {
        migrate(e);
        return new java.util.LinkedHashMap<>(e.getAttachedOrElse(WORN, java.util.Map.of()));
    }

    private static void store(net.minecraft.world.entity.Entity e, java.util.Map<String, String> worn) {
        if (worn.isEmpty()) e.removeAttached(WORN); else e.setAttached(WORN, java.util.Map.copyOf(worn));
    }

    /** Moves the old single wing choice into the slot map (the slot of its definition, wings if unknown). */
    private static void migrate(net.minecraft.world.entity.Entity e) {
        if (!e.hasAttached(WING)) return;
        String key = e.getAttachedOrElse(WING, "");
        e.removeAttached(WING);
        if (key.isEmpty()) return;
        WingType type = WingType.byKey(key);
        java.util.Map<String, String> worn = new java.util.LinkedHashMap<>(e.getAttachedOrElse(WORN, java.util.Map.of()));
        worn.putIfAbsent(type == null ? "fluegel" : type.slot, key);
        store(e, worn);
    }

    private static WingSyncPayload payload(net.minecraft.world.entity.Entity e) {
        return new WingSyncPayload(e.getUUID(), java.util.List.copyOf(e.getAttachedOrElse(WORN, java.util.Map.of()).values()));
    }

    private static void sendTracking(net.minecraft.world.entity.Entity e) {
        for (ServerPlayer to : PlayerLookup.tracking(e))
            if (ServerPlayNetworking.canSend(to, WingSyncPayload.TYPE)) ServerPlayNetworking.send(to, payload(e));
    }

    /** Pieces whose permission was taken away (for example when a rank runs out) are removed on join. */
    private static void dropIfNotAllowed(ServerPlayer player) {
        migrate(player);
        if (WingType.all().isEmpty()) return;                // the folder could not be read: keep everything
        java.util.Map<String, String> worn = worn(player);
        boolean changed = worn.entrySet().removeIf(en -> {
            WingType type = WingType.byKey(en.getValue());
            return type == null || !type.slot.equals(en.getKey()) || !Permissions.check(player, type.permission(), 2);
        });
        if (changed) store(player, worn);
    }

    /** All definitions to one player (first an empty one, so the client forgets pieces that were removed). */
    private static void sendDefinitions(ServerPlayer to) {
        if (!ServerPlayNetworking.canSend(to, WingDefPayload.TYPE)) return;
        ServerPlayNetworking.send(to, new WingDefPayload("", "", new byte[0]));
        for (WingType t : WingType.all()) ServerPlayNetworking.send(to, new WingDefPayload(t.key, t.json.toString(), t.png));
    }

    private static void send(ServerPlayer to, ServerPlayer about) {
        if (ServerPlayNetworking.canSend(to, WingSyncPayload.TYPE)) ServerPlayNetworking.send(to, payload(about));
    }

    private static void broadcast(MinecraftServer server, ServerPlayer about) {
        for (ServerPlayer to : PlayerLookup.all(server)) send(to, about);
    }
}
