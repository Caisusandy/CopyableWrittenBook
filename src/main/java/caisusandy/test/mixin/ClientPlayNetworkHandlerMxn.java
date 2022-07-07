package caisusandy.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.At;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.util.telemetry.TelemetrySender;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.util.Hand;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMxn {

    @Shadow
    MinecraftClient client;

    @Inject(at = @At("HEAD"), method = "onOpenWrittenBook", locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    public void onOpenWrittenBook(OpenWrittenBookS2CPacket openWrittenBookS2CPacket,
            CallbackInfo returnable) {
        NetworkThreadUtils.forceMainThread(openWrittenBookS2CPacket, (ClientPlayNetworkHandler) (Object) this,
                this.client);
        ItemStack itemStack = this.client.player.getStackInHand(openWrittenBookS2CPacket.getHand());
        if (itemStack.isOf(Items.WRITTEN_BOOK)) {
            this.client.setScreen(new BookEditScreen(client.player, itemStack, Hand.MAIN_HAND));
        }
        returnable.cancel();
    }
}
