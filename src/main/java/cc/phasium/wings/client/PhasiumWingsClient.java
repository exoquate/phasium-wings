package cc.phasium.wings.client;

import cc.phasium.wings.WingSyncPayload;
import cc.phasium.wings.WingType;
import cc.phasium.wings.PhasiumWings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

public class PhasiumWingsClient implements ClientModInitializer {
    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WingSyncPayload.TYPE, (payload, context) -> {
            if (payload.worn().isEmpty()) WingsLayer.WORN.remove(payload.player());
            else WingsLayer.WORN.put(payload.player(), java.util.List.copyOf(payload.worn()));
        });
        // wing definitions from the server: json + atlas, the atlas becomes a texture of its own
        ClientPlayNetworking.registerGlobalReceiver(cc.phasium.wings.WingDefPayload.TYPE, (payload, context) -> {
            WingsLayer.definitionsChanged();
            if (payload.key().isEmpty()) { WingType.clear(); return; }
            try {
                var json = com.google.gson.JsonParser.parseString(payload.json()).getAsJsonObject();
                WingType type = WingType.create(payload.key(), json, payload.png());
                var image = com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(payload.png()));
                type.texture = PhasiumWings.id("dyn/" + payload.key());
                context.client().getTextureManager().register(type.texture, new net.minecraft.client.renderer.texture.DynamicTexture(image));
                WingType.put(type);
            } catch (Exception e) {
                PhasiumWings.LOG.warn("[Flügel] {} konnte nicht geladen werden: {}", payload.key(), e.getMessage());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { WingsLayer.WORN.clear(); WingsLayer.definitionsChanged(); });
        ParticleFactoryRegistry.getInstance().register(PhasiumWings.FEATHER, FeatherParticle.Provider::new);
        ParticleFactoryRegistry.getInstance().register(PhasiumWings.SPARK, SparkParticle.Provider::new);
        ClientTickEvents.END_CLIENT_TICK.register(PhasiumWingsClient::dropFeathers);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(
                new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override public net.minecraft.resources.ResourceLocation getFabricId() { return PhasiumWings.id("ruestung"); }
                    @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) { ArmorSkins.clear(); }
                });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer) {
                helper.register(new WingsLayer<>(renderer));
                helper.register(new ArmorSkins.Layer<>(renderer));
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(PhasiumWingsClient::drawNpcWings);
        // the Phasium button look (night frames with gold rivets, sliders, tabs, Mod Menu icon, night panorama) as a
        // built-in resource pack that is always on, so players who update the modpack get it without touching options
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(PhasiumWings.MOD_ID).ifPresent(mod ->
                net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(PhasiumWings.id("knoepfe"), mod,
                        net.minecraft.network.chat.Component.literal("Phasium Knöpfe"), net.fabricmc.fabric.api.resource.ResourcePackActivationType.ALWAYS_ENABLED));
        // /fluegeleffekte [an|aus]: everyone can switch the feathers and sparkles off for themselves
        effects = !"aus".equals(readSetting());
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("fluegeleffekte")
                        .executes(ctx -> setEffects(ctx.getSource(), !effects))
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("an").executes(ctx -> setEffects(ctx.getSource(), true)))
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("aus").executes(ctx -> setEffects(ctx.getSource(), false)))));
    }

    /** Whether feathers and sparkles are drawn; each player decides for themselves, saved in config/phasium_wings_client.txt. */
    private static boolean effects = true;

    private static java.nio.file.Path settingFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("phasium_wings_client.txt");
    }

    private static String readSetting() {
        try {
            return java.nio.file.Files.readString(settingFile()).trim();
        } catch (Exception e) {
            return "an";
        }
    }

    private static int setEffects(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean on) {
        effects = on;
        try {
            java.nio.file.Files.writeString(settingFile(), on ? "an" : "aus");
        } catch (Exception e) {
            PhasiumWings.LOG.warn("[Flügel] Einstellung konnte nicht gespeichert werden: {}", e.getMessage());
        }
        source.sendFeedback(net.minecraft.network.chat.Component.literal(on
                ? "§dFlügeleffekte sind wieder an."
                : "§7Flügeleffekte sind aus. Mit /fluegeleffekte an schaltest du sie wieder ein."));
        return 1;
    }

    /** Every tick, players wearing angel wings may lose a feather: rarely while standing, more often while flying. */
    private static void dropFeathers(Minecraft client) {
        if (client.level == null || client.isPaused() || !effects) return;
        for (Player player : client.level.players()) {   // feathers only for players, NPCs stay tidy
            if (player.isInvisible()) continue;
            for (WingType type : WingsLayer.worn(player.getUUID())) dropFeathers(client, player, type);
        }
    }

    private static void dropFeathers(Minecraft client, Player player, WingType type) {
        if (type.sparkles()) sparkle(client, player, type);
        if (type.feathers < 0) return;
        boolean flying = player.isFallFlying() || player.getAbilities().flying || (!player.onGround() && player.getDeltaMovement().y < -0.1);
        if (player.getRandom().nextFloat() > (flying ? 0.18f : 0.025f)) return;
        // somewhere behind the back, out to one of the wings
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double side = (player.getRandom().nextBoolean() ? 1 : -1) * (0.25 + player.getRandom().nextDouble() * 0.6);
        double back = 0.3;
        double x = player.getX() - Mth.cos(yaw) * side + Mth.sin(yaw) * back;
        double z = player.getZ() - Mth.sin(yaw) * side - Mth.cos(yaw) * back;
        double y = player.getY() + 1.0 + player.getRandom().nextDouble() * 0.7;
        client.level.addParticle(net.minecraft.core.particles.ColorParticleOption.create(PhasiumWings.FEATHER, 0xFF000000 | type.feathers), x, y, z, 0, -0.01, 0);
    }

    /**
     * Phasium night wings: a few glowing sparkles around the wings while standing, a whole trail of them while
     * flying, spread over the wing span, drifting backwards a little like the shimmer behind Ho-Oh.
     */
    private static void sparkle(Minecraft client, Player player, WingType type) {
        var rnd = player.getRandom();
        boolean flying = player.isFallFlying() || player.getAbilities().flying || (!player.onGround() && player.getDeltaMovement().y < -0.1);
        double speed = player.getDeltaMovement().horizontalDistance();
        int count = flying ? 2 + rnd.nextInt(3) : (rnd.nextFloat() < 0.12f + speed * 0.6f ? 1 : 0);
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < count; i++) {
            double side = (rnd.nextBoolean() ? 1 : -1) * (0.15 + rnd.nextDouble() * 1.05);
            double back = 0.3 + rnd.nextDouble() * 0.15;
            double x = player.getX() - Mth.cos(yaw) * side + Mth.sin(yaw) * back;
            double z = player.getZ() - Mth.sin(yaw) * side - Mth.cos(yaw) * back;
            double y = player.getY() + 0.8 + rnd.nextDouble() * 1.3 - Math.abs(side) * 0.25;
            var motion = player.getDeltaMovement().scale(-0.08);
            var spark = net.minecraft.core.particles.ColorParticleOption.create(PhasiumWings.SPARK, 0xFF000000 | type.sparks[rnd.nextInt(type.sparks.length)]);
            client.level.addParticle(spark, x, y, z, motion.x + (rnd.nextDouble() - 0.5) * 0.01, 0.004 + rnd.nextDouble() * 0.006, motion.z + (rnd.nextDouble() - 0.5) * 0.01);
        }
    }

    /**
     * Wings on entities other than players (NPCs): drawn in the world with the same transform the vanilla living
     * entity renderer uses for players (turned with the body, flipped, model origin at the neck).
     */
    static void drawNpcWings(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
        if (WingsLayer.WORN.isEmpty() || ctx.consumers() == null || ctx.matrixStack() == null) return;
        float delta = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        net.minecraft.world.phys.Vec3 cam = ctx.camera().getPosition();
        com.mojang.blaze3d.vertex.PoseStack m = ctx.matrixStack();
        for (net.minecraft.world.entity.Entity e : ctx.world().entitiesForRendering()) {
            if (e instanceof Player || !(e instanceof net.minecraft.world.entity.LivingEntity living) || e.isInvisible()) continue;
            java.util.List<WingType> worn = WingsLayer.worn(e.getUUID());
            if (worn.isEmpty()) continue;
            double x = Mth.lerp(delta, e.xOld, e.getX()) - cam.x, y = Mth.lerp(delta, e.yOld, e.getY()) - cam.y, z = Mth.lerp(delta, e.zOld, e.getZ()) - cam.z;
            float bodyYaw = Mth.rotLerp(delta, living.yBodyRotO, living.yBodyRot);
            int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(ctx.world(), e.blockPosition().above());
            m.pushPose();
            m.translate(x, y, z);
            m.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - bodyYaw));
            m.scale(-1f, -1f, 1f);
            m.translate(0f, -1.501f, 0f);
            for (WingType type : worn) {
                WingsRenderer renderer = WingsLayer.renderer(type);
                if (renderer != null) renderer.render(m, ctx.consumers(), light, living, null, 0f, living.tickCount + delta);
            }
            m.popPose();
        }
    }
}
