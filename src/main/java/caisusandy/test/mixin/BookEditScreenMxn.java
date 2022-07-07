package caisusandy.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameModeSelectionScreen.ButtonWidget;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen.WrittenBookContents;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

@Mixin(BookEditScreen.class)
public class BookEditScreenMxn extends Screen {

    @Shadow
    private final ItemStack itemStack;
    @Shadow
    private int currentPage;
    @Shadow
    private boolean signing;
    @Shadow
    private String title;

    protected BookEditScreenMxn(Text text) {
        super(text);
        itemStack = null;
    }

    @Inject(method = "charTyped", at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    void charTyped(char c, int i, CallbackInfoReturnable<Boolean> info) {
        if (isBookWritten()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "updateButtons", at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    private void updateButtons(CallbackInfo info) {
        BookEditScreenAccessor accessor = (BookEditScreenAccessor) (BookEditScreen) (Object) this;
        if (accessor != null) {
            accessor.getPreviousPageButton().visible = !this.signing && this.currentPage > 0;
            accessor.getNextPageButton().visible = !this.signing;
            accessor.getDoneButton().visible = !this.signing;
            accessor.getSignButton().visible = !this.signing;
            accessor.getSignButton().active = !this.signing && !isBookWritten();
            accessor.getCancelButton().visible = this.signing;
            accessor.getFinalizeButton().visible = this.signing && !isBookWritten();
            accessor.getFinalizeButton().active = !this.title.trim().isEmpty() && !isBookWritten();
            info.cancel();
        }
    }

    @Inject(method = "getCurrentPageContent", at = @At("HEAD"), locals = LocalCapture.CAPTURE_FAILEXCEPTION, cancellable = true)
    private void getCurrentPageContent(CallbackInfoReturnable<String> sReturnable) {
        if (isBookWritten()) {
            sReturnable.setReturnValue(new WrittenBookContents(itemStack).getPage(currentPage).getString());
        }
    }

    public boolean isBookWritten() {
        return itemStack != null && itemStack.isOf(Items.WRITTEN_BOOK);
    }

}