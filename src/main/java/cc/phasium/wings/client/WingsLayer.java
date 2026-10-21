package cc.phasium.wings.client;

import cc.phasium.wings.WingType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Render layer for players: draws every cosmetic the server says they wear (wings, hair, hat, belt, jacket). Other entities (NPCs) are drawn by
 * {@link PhasiumWingsClient#drawNpcWings}, because their renderers (Cobblemon) use their own model space.
 */
public class WingsLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** Entity UUID -> keys of the pieces it wears, filled from the server's sync packets. */
    public static final Map<UUID, java.util.List<String>> WORN = new ConcurrentHashMap<>();
    private static final Map<String, WingsRenderer> RENDERERS = new ConcurrentHashMap<>();

    /** The pieces an entity wears whose definition the server has sent. */
    public static java.util.List<WingType> worn(UUID entity) {
        java.util.List<String> keys = WORN.get(entity);
        if (keys == null) return java.util.List.of();
        java.util.List<WingType> types = new java.util.ArrayList<>(keys.size());
        for (String k : keys) {
            WingType t = WingType.byKey(k);
            if (t != null) types.add(t);
        }
        return types;
    }

    /** Renderer for wings whose texture is registered; null while the definition is still missing. */
    public static WingsRenderer renderer(WingType type) {
        if (type == null || type.texture == null) return null;
        return RENDERERS.computeIfAbsent(type.key, k -> new WingsRenderer(type));
    }

    /** New definitions from the server: build the renderers again. */
    public static void definitionsChanged() {
        RENDERERS.clear();
    }

    public WingsLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (WORN.isEmpty()) return;
        if (entity.isInvisible() || !(entity instanceof net.minecraft.world.entity.player.Player)) return;
        HumanoidModel<?> humanoid = getParentModel() instanceof HumanoidModel<?> h ? h : null;
        for (WingType type : worn(entity.getUUID())) {
            WingsRenderer r = renderer(type);
            if (r != null) r.render(matrices, buffers, light, entity, humanoid, limbSwingAmount, ageInTicks);
        }
    }
}
