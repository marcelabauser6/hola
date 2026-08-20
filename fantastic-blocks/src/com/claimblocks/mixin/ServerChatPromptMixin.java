/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.PlayerChatMessage
 *  net.minecraft.network.protocol.game.ServerboundChatPacket
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.server.network.ServerGamePacketListenerImpl
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.claimblocks.mixin;

import com.claimblocks.ClaimBlocksMod;
import com.claimblocks.chat.ChatPromptRouter;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerGamePacketListenerImpl.class})
public abstract class ServerChatPromptMixin {
    @Inject(method={"handleChat"}, at={@At(value="HEAD")}, require=0)
    private void claimblocks$captureMenuPrompt(ServerboundChatPacket packet, CallbackInfo ci) {
        try {
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
            ServerPlayer sender = self.m_142253_();
            if (sender == null || !ChatPromptRouter.hasPending(sender.m_20148_())) {
                return;
            }
            ChatPromptRouter.markPacketCaptureActive();
            ChatPromptRouter.consume(sender, packet.f_133827_());
        }
        catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[ClaimBlocks] Fallo capturando la respuesta del menu", t);
        }
    }

    @Inject(method={"broadcastChatMessage"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    private void claimblocks$hideConsumedPrompt(PlayerChatMessage message, CallbackInfo ci) {
        try {
            ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl)(Object)this;
            ServerPlayer sender = self.m_142253_();
            if (sender == null) {
                return;
            }
            if (ChatPromptRouter.shouldSuppress(sender.m_20148_(), message.m_245728_())) {
                ci.cancel();
            }
        }
        catch (Throwable t) {
            ClaimBlocksMod.LOGGER.error("[ClaimBlocks] Fallo ocultando la respuesta del menu", t);
        }
    }
}

