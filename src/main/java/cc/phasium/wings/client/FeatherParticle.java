package cc.phasium.wings.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;

/** A small feather that drifts down while swaying from side to side and turning, then fades out. */
public class FeatherParticle extends TextureSheetParticle {
    private final float swayPhase, swaySpeed, spin;

    protected FeatherParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.004f;
        this.hasPhysics = true;
        this.lifetime = 70 + random.nextInt(50);
        this.quadSize = 0.07f + random.nextFloat() * 0.03f;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.swaySpeed = 0.12f + random.nextFloat() * 0.06f;
        this.spin = (random.nextFloat() - 0.5f) * 0.12f;
    }

    @Override
    public void tick() {
        super.tick();
        // sway like a falling leaf, slow down the fall, fade out in the last third
        float sway = Mth.sin(age * swaySpeed + swayPhase);
        xd = xd * 0.9 + sway * 0.006;
        zd = zd * 0.9 + Mth.cos(age * swaySpeed + swayPhase) * 0.004;
        yd = Math.max(yd, -0.03);
        oRoll = roll;
        roll += spin + sway * 0.03f;
        float left = (lifetime - age) / (float) lifetime;
        alpha = Math.min(1f, left * 3f);
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
            FeatherParticle p = new FeatherParticle(level, x, y, z, dx, dy, dz);
            p.pickSprite(sprites);
            p.setColor(type.getRed(), type.getGreen(), type.getBlue());
            return p;
        }
    }
}
