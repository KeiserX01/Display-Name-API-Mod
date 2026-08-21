package com.keiserx01.displaynameapi.network;

import com.keiserx01.displaynameapi.internal.NicknameManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Handles network packet registration and processing for the DisplayName API.
 */
public final class NetworkHandler {

    private NetworkHandler() {}

    /**
     * Registers the network packets for the mod.
     * Called from the main mod class during initialization.
     *
     * @param registrar The payload registrar from NeoForge
     */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
            SyncNicknamePacket.TYPE,
            SyncNicknamePacket.STREAM_CODEC,
            NetworkHandler::handleSyncNicknameClient
        );
    }

    /**
     * Registers the network channel for the mod using the modern RegisterPayloadHandlersEvent.
     * This is the modern way to register payloads in NeoForge 21.1+.
     */
    public static void registerChannel(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
            SyncNicknamePacket.TYPE,
            SyncNicknamePacket.STREAM_CODEC,
            NetworkHandler::handleSyncNicknameClient
        );
    }

    /**
     * Sends a nickname sync packet to all clients tracking the specified player.
     * Called from the server when a player's nickname changes.
     *
     * @param player The player whose nickname changed
     * @param composedNickname The composed nickname component, or null to reset
     */
    public static void sendNicknameSync(ServerPlayer player, @Nullable Component composedNickname) {
        SyncNicknamePacket packet = new SyncNicknamePacket(player.getUUID(), composedNickname);
        
        // Send to all players tracking this player (including the player themselves)
        player.server.getPlayerList().getPlayers().forEach(target -> {
            if (target instanceof ServerPlayer serverPlayer) {
                // Only send to players who can see this player (same dimension, close enough)
                if (serverPlayer.level() == player.level() && serverPlayer.distanceToSqr(player) < 6400) { // 80 blocks^2
                    serverPlayer.connection.send(packet);
                }
            }
        });
    }

    /**
     * Handles the nickname sync packet on the client side.
     * Updates the local client nickname cache.
     */
    private static void handleSyncNicknameClient(SyncNicknamePacket packet, IPayloadContext context) {
        // This runs on the client thread - enqueue to ensure thread safety
        context.enqueueWork(() -> {
            com.keiserx01.displaynameapi.client.ClientNicknameCache.getInstance().setNickname(packet.playerId(), packet.composedNickname());
        });
    }
}