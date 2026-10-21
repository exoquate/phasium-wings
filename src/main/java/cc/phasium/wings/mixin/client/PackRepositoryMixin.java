package cc.phasium.wings.mixin.client;

import cc.phasium.wings.client.LivePack;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Adds the downloaded Phasium live pack to the client resource packs (not to data pack repositories). */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void phasium$addLivePack(RepositorySource[] list, CallbackInfo ci) {
        if (sources.stream().noneMatch(s -> s instanceof ClientPackSource)) return;
        Set<RepositorySource> copy = new LinkedHashSet<>(sources);
        copy.add(new LivePack());
        sources = copy;
    }
}
