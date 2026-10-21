package cc.phasium.wings.client;

import cc.phasium.wings.WingType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one cosmetic: wings as flat textured planes on the back of the wearer (the same way the Blockbench preview
 * does), a block "halo" on the head, and blocks on any body part ("parts": hair, hats, belts, jackets).
 * The planes come with the wing definition the server sends (written by the v2 scripts): each plane is given
 * for the left wing in model pixels from the shoulder (x outwards, y down), with a texel rectangle in the atlas
 * whose left edge is the wing root. The right wing is the mirror image.
 * Model space: +x is the wearer's left, +y points down, +z is the back.
 */
public class WingsRenderer {
    private static final float SHOULDER_X = 1.5f, SHOULDER_Y = 1, BACK_Z = 3.5f;

    private record Plane(float x, float y, float w, float h, float z, float yaw, float u0, float v0, float u1, float v1) {}

    private final WingType type;
    private final ResourceLocation texture;
    private final List<Plane> planes = new ArrayList<>();

    /** One halo block: corners in head space (model px, y down) and the uv rectangles of its top, side and bottom colour. */
    private record HaloBlock(float x0, float y0, float z0, float x1, float y1, float z1, float[] top, float[] side, float[] bottom) {}
    private final List<HaloBlock> halo = new ArrayList<>();
    /** False for head pieces that sit tight (hats): "bob": false in the halo definition. */
    private boolean haloBobs = true;

    /** Body parts the "parts" blocks can follow, with their resting pivot in model space (px, y down from the neck). */
    private enum Bone {
        HEAD(0, 0, 0), BODY(0, 0, 0), RIGHT_ARM(-5, 2, 0), LEFT_ARM(5, 2, 0), RIGHT_LEG(-1.9f, 12, 0), LEFT_LEG(1.9f, 12, 0);
        final float px, py, pz;
        Bone(float px, float py, float pz) { this.px = px; this.py = py; this.pz = pz; }

        static Bone of(String name) {
            return switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "head", "kopf" -> HEAD;
                case "right_arm", "arm_r", "rechter_arm" -> RIGHT_ARM;
                case "left_arm", "arm_l", "linker_arm" -> LEFT_ARM;
                case "right_leg", "leg_r", "rechtes_bein" -> RIGHT_LEG;
                case "left_leg", "leg_l", "linkes_bein" -> LEFT_LEG;
                default -> BODY;
            };
        }

        net.minecraft.client.model.geom.ModelPart part(HumanoidModel<?> m) {
            return switch (this) {
                case HEAD -> m.head;
                case BODY -> m.body;
                case RIGHT_ARM -> m.rightArm;
                case LEFT_ARM -> m.leftArm;
                case RIGHT_LEG -> m.rightLeg;
                case LEFT_LEG -> m.leftLeg;
            };
        }
    }
    /** Blocks per body part, in Blockbench space (absolute, y up, feet at 0) as written by the generator scripts. */
    private record PartBlock(float x, float y, float z, float w, float h, float d, float[] top, float[] side, float[] bottom) {}
    private final java.util.Map<Bone, List<PartBlock>> parts = new java.util.EnumMap<>(Bone.class);

    public WingsRenderer(WingType type) {
        this.type = type;
        this.texture = type.texture;
        JsonObject def = type.geometry();
        JsonArray atlas = def.getAsJsonArray("atlas");
        float aw = atlas.get(0).getAsFloat(), ah = atlas.get(1).getAsFloat();
        for (JsonElement el : def.has("planes") ? def.getAsJsonArray("planes") : new JsonArray()) {
            JsonObject p = el.getAsJsonObject();
            JsonArray uv = p.getAsJsonArray("uv");
            planes.add(new Plane(p.get("x").getAsFloat(), p.get("y").getAsFloat(), p.get("w").getAsFloat(), p.get("h").getAsFloat(),
                    p.get("z").getAsFloat(), p.getAsJsonArray("rot").get(1).getAsFloat() * Mth.DEG_TO_RAD,
                    uv.get(0).getAsFloat() / aw, uv.get(1).getAsFloat() / ah, uv.get(2).getAsFloat() / aw, uv.get(3).getAsFloat() / ah));
        }
        if (def.has("halo")) {
            // the halo is given in Blockbench space (y up, feet at 0, the neck at y = 24); the head bone pivots at the neck
            JsonObject h = def.getAsJsonObject("halo");
            float hy = h.get("y").getAsFloat();
            haloBobs = !h.has("bob") || h.get("bob").getAsBoolean();
            JsonArray sw = h.getAsJsonArray("swatches");
            for (JsonElement el : h.getAsJsonArray("blocks")) {
                JsonObject b = el.getAsJsonObject();
                float x = b.get("x").getAsFloat(), z = b.get("z").getAsFloat(), size = b.get("s").getAsFloat(), height = b.get("h").getAsFloat();
                float y = hy + (b.has("y") ? b.get("y").getAsFloat() : 0) - 24;
                halo.add(new HaloBlock(-x - size, -(y + height), z, -x, -y, z + size,
                        swatch(sw, b.get("top").getAsInt(), aw, ah), swatch(sw, b.get("side").getAsInt(), aw, ah),
                        swatch(sw, b.get("bottom").getAsInt(), aw, ah)));
            }
        }
        if (def.has("parts")) {
            // {"swatches": [...], "parts": [{"bone": "body", "blocks": [{x, y, z, w|s, h, d|s, top, side, bottom}]}]}
            JsonArray sw = def.getAsJsonArray("swatches");
            for (JsonElement pe : def.getAsJsonArray("parts")) {
                JsonObject part = pe.getAsJsonObject();
                List<PartBlock> list = parts.computeIfAbsent(Bone.of(part.get("bone").getAsString()), k -> new ArrayList<>());
                for (JsonElement el : part.getAsJsonArray("blocks")) {
                    JsonObject b = el.getAsJsonObject();
                    float s = b.has("s") ? b.get("s").getAsFloat() : 1;
                    list.add(new PartBlock(b.get("x").getAsFloat(), b.get("y").getAsFloat(), b.get("z").getAsFloat(),
                            b.has("w") ? b.get("w").getAsFloat() : s, b.has("h") ? b.get("h").getAsFloat() : s, b.has("d") ? b.get("d").getAsFloat() : s,
                            swatch(sw, b.get("top").getAsInt(), aw, ah), swatch(sw, b.get("side").getAsInt(), aw, ah),
                            swatch(sw, b.get("bottom").getAsInt(), aw, ah)));
                }
            }
        }
    }

    private static float[] swatch(JsonArray swatches, int i, float aw, float ah) {
        JsonArray s = swatches.get(i).getAsJsonArray();
        return new float[]{s.get(0).getAsFloat() / aw, s.get(1).getAsFloat() / ah, s.get(2).getAsFloat() / aw, s.get(3).getAsFloat() / ah};
    }

    /** Draws the "parts" blocks, each group following its body part (for NPCs: at the resting position). */
    private void renderParts(PoseStack matrices, VertexConsumer consumer, int light, HumanoidModel<?> humanoid) {
        for (var en : parts.entrySet()) {
            Bone bone = en.getKey();
            matrices.pushPose();
            float px = bone.px, py = bone.py, pz = bone.pz;
            if (humanoid != null) {
                var part = bone.part(humanoid);
                part.translateAndRotate(matrices);
                var rest = part.getInitialPose();                 // slim arms rest half a pixel lower than wide ones
                px = rest.x; py = rest.y; pz = rest.z;
            } else {
                matrices.translate(px / 16f, py / 16f, pz / 16f);
            }
            PoseStack.Pose pose = matrices.last();
            for (PartBlock b : en.getValue()) {
                // Blockbench (x to the wearer's right, y up from the feet) -> model space of the bone (x left, y down)
                float x0 = -b.x() - b.w() - px, x1 = -b.x() - px;
                float y0 = 24 - b.y() - b.h() - py, y1 = 24 - b.y() - py;
                float z0 = b.z() - pz, z1 = b.z() + b.d() - pz;
                block(pose, consumer, light, new HaloBlock(x0, y0, z0, x1, y1, z1, b.top(), b.side(), b.bottom()));
            }
            matrices.popPose();
        }
    }

    /** Returns {spread, lift} in radians: spread swings the wings back around the shoulder, lift raises the tips. */
    private float[] pose(LivingEntity entity, float limbSwingAmount, float age) {
        boolean flying = entity.isFallFlying() || (entity instanceof Player p && p.getAbilities().flying)
                || (!entity.onGround() && !entity.isInWater() && !entity.isPassenger() && entity.getDeltaMovement().y < -0.1);
        float walk = Math.min(limbSwingAmount, 1) * 0.3f;
        boolean butterfly = type.style == WingType.Style.BUTTERFLY;
        float spread = type.spread, lift = type.lift;                          // degrees, like the Blockbench preview
        if (entity.isCrouching()) {
            return new float[]{60 * Mth.DEG_TO_RAD, 0};
        }
        if (flying) {
            float beat = butterfly ? Mth.sin(age * 1.1f) * 22 : Mth.sin(age * 0.5f) * 18;
            return new float[]{(spread + 8 + beat) * Mth.DEG_TO_RAD, (lift + beat * 0.3f) * Mth.DEG_TO_RAD};
        }
        float breathe = butterfly ? Mth.sin(age * 0.22f) * 5 : Mth.sin(age * 0.09f) * 3;
        return new float[]{(spread + breathe) * Mth.DEG_TO_RAD + walk, lift * Mth.DEG_TO_RAD};
    }

    /**
     * Draws the wings on the back (called from {@link WingsLayer}). With a humanoid model (players) they follow the
     * body and the halo follows the head; for other models (Cobblemon NPCs) both sit at the default neck position.
     */
    public void render(PoseStack matrices, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> humanoid,
                       float limbSwingAmount, float ageInTicks) {
        float[] pose = pose(entity, limbSwingAmount, ageInTicks);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        matrices.pushPose();
        if (humanoid != null) humanoid.body.translateAndRotate(matrices);
        for (int side : new int[]{1, -1}) {
            matrices.pushPose();
            matrices.translate(side * SHOULDER_X / 16f, SHOULDER_Y / 16f, BACK_Z / 16f);
            matrices.mulPose(new Quaternionf().rotationZYX(-side * pose[1], -side * pose[0], 0));
            for (Plane p : planes) {
                matrices.pushPose();
                matrices.mulPose(new Quaternionf().rotationY(-side * p.yaw()));
                quad(matrices.last(), consumer, light, side, p);
                matrices.popPose();
            }
            matrices.popPose();
        }
        matrices.popPose();

        if (!parts.isEmpty()) renderParts(matrices, consumer, light, humanoid);

        if (!halo.isEmpty()) {
            // follows the head; a halo bobs gently (0.4 px, once every 2 seconds), a hat does not
            matrices.pushPose();
            if (humanoid != null) humanoid.head.translateAndRotate(matrices);
            if (haloBobs) matrices.translate(0, -0.4f * Mth.sin(ageInTicks * Mth.TWO_PI / 40f) / 16f, 0);
            PoseStack.Pose head = matrices.last();
            for (HaloBlock b : halo) block(head, consumer, light, b);
            matrices.popPose();
        }
    }

    /** A halo block: six faces, the top lit, the sides a bit darker, the bottom darkest (y points down). */
    private static void block(PoseStack.Pose p, VertexConsumer c, int light, HaloBlock b) {
        float x0 = b.x0() / 16f, x1 = b.x1() / 16f, y0 = b.y0() / 16f, y1 = b.y1() / 16f, z0 = b.z0() / 16f, z1 = b.z1() / 16f;
        face(p, c, light, b.top(), x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
        face(p, c, light, b.bottom(), x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        face(p, c, light, b.side(), x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        face(p, c, light, b.side(), x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
        face(p, c, light, b.side(), x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
        face(p, c, light, b.side(), x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
    }

    private static void face(PoseStack.Pose p, VertexConsumer c, int light, float[] uv,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz) {
        c.addVertex(p, ax, ay, az).setColor(0xFFFFFFFF).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        c.addVertex(p, bx, by, bz).setColor(0xFFFFFFFF).setUv(uv[2], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        c.addVertex(p, cx, cy, cz).setColor(0xFFFFFFFF).setUv(uv[2], uv[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        c.addVertex(p, dx, dy, dz).setColor(0xFFFFFFFF).setUv(uv[0], uv[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    /** One plane as a single quad, seen from both sides (the render type does not cull). */
    private static void quad(PoseStack.Pose pose, VertexConsumer c, int light, int side, Plane p) {
        float xi = side * p.x() / 16f, xo = side * (p.x() + p.w()) / 16f;    // root edge and outer edge
        float yt = p.y() / 16f, yb = (p.y() + p.h()) / 16f, z = p.z() / 16f;
        vertex(pose, c, light, xi, yt, z, p.u0(), p.v0());
        vertex(pose, c, light, xi, yb, z, p.u0(), p.v1());
        vertex(pose, c, light, xo, yb, z, p.u1(), p.v1());
        vertex(pose, c, light, xo, yt, z, p.u1(), p.v0());
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer c, int light, float x, float y, float z, float u, float v) {
        c.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }
}
