package caisusandy.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.StatHandler;
import net.minecraft.util.Hand;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMxn {

    @Shadow
    MinecraftClient client;

    @Inject(at = @At("HEAD"), method = "useBook", locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    public void useBook(ItemStack itemStack, Hand hand, CallbackInfo info) {
        this.client.setScreen(new BookEditScreen((ClientPlayerEntity) (Object) this, itemStack, hand));
    }

}
