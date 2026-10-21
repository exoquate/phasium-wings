package cc.phasium.wings;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/** Server to client: every cosmetic an entity wears (wings, hair, hat, ...), as keys; empty = nothing. */
public record WingSyncPayload(UUID player, List<String> worn) implements CustomPacketPayload {
    public static final Type<WingSyncPayload> TYPE = new Type<>(PhasiumWings.id("sync2"));
    public static final StreamCodec<FriendlyByteBuf, WingSyncPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WingSyncPayload::player,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), WingSyncPayload::worn,
            WingSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
