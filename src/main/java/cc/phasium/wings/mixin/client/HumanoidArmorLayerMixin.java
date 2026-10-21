package cc.phasium.wings.mixin.client;

import cc.phasium.wings.client.ArmorSkins;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla armour is not drawn for pieces with an armour skin; {@link ArmorSkins.Layer} draws those. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void phasium$hideSkinned(PoseStack matrices, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot,
                                     int light, HumanoidModel<?> model, CallbackInfo ci) {
        if (ArmorSkins.hides(entity.getItemBySlot(slot), slot)) ci.cancel();
    }
}
