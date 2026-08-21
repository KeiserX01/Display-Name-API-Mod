package com.keiserx01.displaynameapi.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Packet sent from server to client to synchronize player nickname data.
 * Used for rendering custom nameplates on the client side.
 */
public record SyncNicknamePacket(
    java.util.UUID playerId,
    @Nullable Component composedNickname
) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("displaynameapimod", "sync_nickname");
    public static final Type<SyncNicknamePacket> TYPE = new Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SyncNicknamePacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUUID(packet.playerId());
            // Write component manually using standard methods
            Component nickname = packet.composedNickname();
            if (nickname != null) {
                buf.writeBoolean(true);
                // Write component as JSON string
                String json = Component.Serializer.toJson(nickname, null);
                buf.writeUtf(json);
            } else {
                buf.writeBoolean(false);
            }
        },
        buf -> {
            java.util.UUID playerId = buf.readUUID();
            boolean hasNickname = buf.readBoolean();
            Component composedNickname = null;
            if (hasNickname) {
                String json = buf.readUtf();
                composedNickname = Component.Serializer.fromJson(json, null);
            }
            return new SyncNicknamePacket(playerId, composedNickname);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}