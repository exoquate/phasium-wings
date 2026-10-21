package cc.phasium.wings.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;

/** A small glowing sparkle: always fully bright, twinkles, drifts slowly and shrinks away. */
public class SparkParticle extends TextureSheetParticle {
    private final float baseSize, twinkle;

    protected SparkParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.lifetime = 25 + random.nextInt(25);
        this.baseSize = 0.05f + random.nextFloat() * 0.06f;
        this.quadSize = baseSize;
        this.twinkle = 0.5f + random.nextFloat() * 0.5f;
    }

    @Override
    public void tick() {
        super.tick();
        xd *= 0.92;
        zd *= 0.92;
        float t = age / (float) lifetime;
        quadSize = baseSize * (1f - t * t) * (0.75f + 0.25f * Mth.sin(age * twinkle));   // twinkle while it shrinks
        alpha = Math.min(1f, (1f - t) * 2f);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;                                   // glows in the dark
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ColorParticleOption type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            SparkParticle p = new SparkParticle(level, x, y, z, dx, dy, dz);
            p.pickSprite(sprites);
            p.setColor(type.getRed(), type.getGreen(), type.getBlue());
            return p;
        }
    }
}
