package cc.phasium.wings;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: one wing definition (its json and atlas png). An empty key means "forget all wings", which the
 * server sends before the full list so removed wings disappear too.
 */
public record WingDefPayload(String key, String json, byte[] png) implements CustomPacketPayload {
    public static final Type<WingDefPayload> TYPE = new Type<>(PhasiumWings.id("def"));
    public static final StreamCodec<FriendlyByteBuf, WingDefPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WingDefPayload::key,
            ByteBufCodecs.stringUtf8(1 << 20), WingDefPayload::json,
            ByteBufCodecs.byteArray(1 << 20), WingDefPayload::png,
            WingDefPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
