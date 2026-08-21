package com.keiserx01.displaynameapi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameplateEvent;

/**
 * Client-side event handler for rendering custom nameplates.
 * Uses the ClientNicknameCache to render custom nicknames above players' heads.
 */
@EventBusSubscriber(modid = "displaynameapimod", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientNameplateRenderer {

    /**
     * Handles the nameplate rendering event.
     * If the player has a custom nickname in the cache, renders it instead of the default name.
     */
    @SubscribeEvent
    public static void onRenderNameplate(RenderNameplateEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        var cache = ClientNicknameCache.getInstance();
        java.util.UUID playerId = player.getUUID();
        
        Component customNickname = cache.getNickname(playerId);
        if (customNickname != null) {
            // Cancel the default nameplate rendering
            event.setCanceled(true);
            
            // Render the custom nickname
            renderCustomNameplate(event, player, customNickname);
        }
    }

    /**
     * Renders a custom nameplate using the provided component.
     */
    private static void renderCustomNameplate(RenderNameplateEvent event, Player player, Component customNickname) {
        // Get the Minecraft instance and font renderer
        Minecraft minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        
        // Set up the pose stack
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        
        // Position the nameplate above the player's head
        poseStack.pushPose();
        poseStack.translate(0.0, player.getEyeHeight() + 0.5, 0.0);
        
        // Make the nameplate face the camera
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        
        // Draw the text with background
        // The text is centered, so we need to offset by half the width
        int width = font.width(customNickname);
        poseStack.translate(-width / 2.0, 0.0, 0.0);
        
        // Render the text with shadow for readability
        font.drawInBatch(
            customNickname,
            0, 0,
            0xFFFFFFFF, // White color (the component should have its own colors)
            false, // No shadow for now, the component handles formatting
            poseStack.last().pose(),
            bufferSource,
            net.minecraft.client.gui.Font.TextBackground.NORMAL,
            0, // No background color override
            net.minecraft.client.renderer.LightTexture.FULL_BRIGHT
        );
        
        poseStack.popPose();
    }
}