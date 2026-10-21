package cc.phasium.wings.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Armour skins: an armour piece with the server skin tag (custom_data phasium_skin {id}, put on with /skin from
 * phasium-perks) is drawn as blocks from assets/phasium/ruestung/<id>.json (live pack) instead of the vanilla armour.
 * The json: {"texture", "atlas": [w, h], "swatches": [[u0, v0, u1, v1]], "pieces": {"helm"|"brust"|"hose"|"stiefel":
 * [{"bone", "blocks": [{x, y, z, w, h, d, top, side, bottom, glow?, anim?}]}]}} with blocks in Blockbench space
 * (x to the wearer's right, y up from the feet, like the wings "parts"). anim: {type bob|spin|orbit|pulse, period
 * (ticks), amp, phase (ticks), pivot [x, y, z] in Blockbench space}.
 */
public final class ArmorSkins {
    record Anim(String type, float period, float amp, float phase, float px, float py, float pz) {}
    record Block(float x, float y, float z, float w, float h, float d, float[] top, float[] side, float[] bottom, boolean glow, Anim anim) {}
    record Part(String bone, List<Block> blocks) {}
    record Def(ResourceLocation texture, Map<String, List<Part>> pieces) {}

    private static final Map<String, Optional<Def>> CACHE = new ConcurrentHashMap<>();

    /** resources were reloaded (F3+T, live pack): read the definitions again */
    public static void clear() { CACHE.clear(); }

    static Def def(String id) { return CACHE.computeIfAbsent(id, ArmorSkins::load).orElse(null); }

    private static Optional<Def> load(String id) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("phasium", "ruestung/" + id + ".json");
        var res = Minecraft.getInstance().getResourceManager().getResource(loc);
        if (res.isEmpty()) return Optional.empty();
        try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject j = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray atlas = j.getAsJsonArray("atlas");
            float aw = atlas.get(0).getAsFloat(), ah = atlas.get(1).getAsFloat();
            JsonArray sw = j.getAsJsonArray("swatches");
            Map<String, List<Part>> pieces = new HashMap<>();
            for (var pe : j.getAsJsonObject("pieces").entrySet()) {
                List<Part> parts = new ArrayList<>();
                for (JsonElement el : pe.getValue().getAsJsonArray()) {
                    JsonObject p = el.getAsJsonObject();
                    List<Block> blocks = new ArrayList<>();
                    for (JsonElement be : p.getAsJsonArray("blocks")) {
                        JsonObject b = be.getAsJsonObject();
                        Anim anim = null;
                        if (b.has("anim")) {
                            JsonObject a = b.getAsJsonObject("anim");
                            JsonArray pv = a.has("pivot") ? a.getAsJsonArray("pivot") : null;
                            anim = new Anim(a.get("type").getAsString(), a.has("period") ? a.get("period").getAsFloat() : 40,
                                    a.has("amp") ? a.get("amp").getAsFloat() : 0, a.has("phase") ? a.get("phase").getAsFloat() : 0,
                                    pv == null ? b.get("x").getAsFloat() + b.get("w").getAsFloat() / 2 : pv.get(0).getAsFloat(),
                                    pv == null ? b.get("y").getAsFloat() + b.get("h").getAsFloat() / 2 : pv.get(1).getAsFloat(),
                                    pv == null ? b.get("z").getAsFloat() + b.get("d").getAsFloat() / 2 : pv.get(2).getAsFloat());
                        }
                        blocks.add(new Block(b.get("x").getAsFloat(), b.get("y").getAsFloat(), b.get("z").getAsFloat(),
                                b.get("w").getAsFloat(), b.get("h").getAsFloat(), b.get("d").getAsFloat(),
                                swatch(sw, b.get("top").getAsInt(), aw, ah), swatch(sw, b.get("side").getAsInt(), aw, ah),
                                swatch(sw, b.get("bottom").getAsInt(), aw, ah), b.has("glow") && b.get("glow").getAsBoolean(), anim));
                    }
                    parts.add(new Part(p.get("bone").getAsString(), blocks));
                }
                pieces.put(pe.getKey(), parts);
            }
            return Optional.of(new Def(ResourceLocation.parse(j.get("texture").getAsString()), pieces));
        } catch (Exception e) {
            cc.phasium.wings.PhasiumWings.LOG.error("[Wings] armour skin {} could not be read", id, e);
            return Optional.empty();
        }
    }

    private static float[] swatch(JsonArray swatches, int i, float aw, float ah) {
        JsonArray s = swatches.get(i).getAsJsonArray();
        return new float[]{s.get(0).getAsFloat() / aw, s.get(1).getAsFloat() / ah, s.get(2).getAsFloat() / aw, s.get(3).getAsFloat() / ah};
    }

    static String skinId(ItemStack s) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        CompoundTag t = cd.copyTag();
        return t.contains("phasium_skin") ? t.getCompound("phasium_skin").getString("id") : null;
    }

    static String piece(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "helm";
            case CHEST -> "brust";
            case LEGS -> "hose";
            case FEET -> "stiefel";
            default -> null;
        };
    }

    /** the parts to draw for this armour piece, or null if it has no armour skin (then vanilla draws it) */
    static List<Part> parts(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return null;
        String id = skinId(stack);
        if (id == null) return null;
        Def d = def(id);
        return d == null ? null : d.pieces().get(piece(slot));
    }

    /** used by the armour layer mixin: vanilla skips pieces we draw */
    public static boolean hides(ItemStack stack, EquipmentSlot slot) { return parts(stack, slot) != null; }

    private static ModelPart bone(HumanoidModel<?> m, String name) {
        return switch (name) {
            case "head" -> m.head;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> m.body;
        };
    }

    /** Player render layer: draws every worn armour piece that has an armour skin. */
    public static final class Layer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        public Layer(RenderLayerParent<T, M> parent) { super(parent); }

        @Override
        public void render(PoseStack matrices, MultiBufferSource buffers, int light, T entity, float limbSwing,
                           float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible() || !(getParentModel() instanceof HumanoidModel<?> humanoid)) return;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = entity.getItemBySlot(slot);
                List<Part> parts = parts(stack, slot);
                if (parts == null) continue;
                VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(def(skinId(stack)).texture()));
                for (Part part : parts) {
                    ModelPart mp = bone(humanoid, part.bone());
                    matrices.pushPose();
                    mp.translateAndRotate(matrices);
                    var rest = mp.getInitialPose();
                    for (Block b : part.blocks()) draw(matrices, consumer, b.glow() ? LightTexture.FULL_BRIGHT : light, b, rest.x, rest.y, rest.z, ageInTicks);
                    matrices.popPose();
                }
            }
        }
    }

    /** one block, Blockbench space -> bone model space (x left, y down), with its animation around the pivot */
    private static void draw(PoseStack matrices, VertexConsumer c, int light, Block b, float px, float py, float pz, float age) {
        float x0 = -b.x() - b.w() - px, x1 = -b.x() - px;
        float y0 = 24 - b.y() - b.h() - py, y1 = 24 - b.y() - py;
        float z0 = b.z() - pz, z1 = b.z() + b.d() - pz;
        matrices.pushPose();
        Anim a = b.anim();
        if (a != null) {
            float vx = (-a.px() - px) / 16f, vy = (24 - a.py() - py) / 16f, vz = (a.pz() - pz) / 16f;
            float t = (age + a.phase()) / a.period() * Mth.TWO_PI;
            matrices.translate(vx, vy, vz);
            switch (a.type()) {
                case "bob" -> matrices.translate(0, -a.amp() * Mth.sin(t) / 16f, 0);
                case "spin", "orbit" -> matrices.mulPose(Axis.YP.rotation(t));
                case "pulse" -> { float s = 1 + a.amp() * (0.5f + 0.5f * Mth.sin(t)); matrices.scale(s, s, s); }
                default -> {}
            }
            matrices.translate(-vx, -vy, -vz);
        }
        PoseStack.Pose p = matrices.last();
        float X0 = x0 / 16f, X1 = x1 / 16f, Y0 = y0 / 16f, Y1 = y1 / 16f, Z0 = z0 / 16f, Z1 = z1 / 16f;
        face(p, c, light, b.top(), X0, Y0, Z0, X1, Y0, Z0, X1, Y0, Z1, X0, Y0, Z1, 0, -1, 0);
        face(p, c, light, b.bottom(), X0, Y1, Z1, X1, Y1, Z1, X1, Y1, Z0, X0, Y1, Z0, 0, 1, 0);
        face(p, c, light, b.side(), X0, Y0, Z1, X1, Y0, Z1, X1, Y1, Z1, X0, Y1, Z1, 0, 0, 1);
        face(p, c, light, b.side(), X1, Y0, Z0, X0, Y0, Z0, X0, Y1, Z0, X1, Y1, Z0, 0, 0, -1);
        face(p, c, light, b.side(), X1, Y0, Z1, X1, Y0, Z0, X1, Y1, Z0, X1, Y1, Z1, 1, 0, 0);
        face(p, c, light, b.side(), X0, Y0, Z0, X0, Y0, Z1, X0, Y1, Z1, X0, Y1, Z0, -1, 0, 0);
        matrices.popPose();
    }

    private static void face(PoseStack.Pose p, VertexConsumer c, int light, float[] uv,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float nx, float ny, float nz) {
        c.addVertex(p, ax, ay, az).setColor(0xFFFFFFFF).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        c.addVertex(p, bx, by, bz).setColor(0xFFFFFFFF).setUv(uv[2], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        c.addVertex(p, cx, cy, cz).setColor(0xFFFFFFFF).setUv(uv[2], uv[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        c.addVertex(p, dx, dy, dz).setColor(0xFFFFFFFF).setUv(uv[0], uv[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
