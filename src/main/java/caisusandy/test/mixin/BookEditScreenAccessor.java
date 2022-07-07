package caisusandy.test.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;

@Mixin(BookEditScreen.class)
public interface BookEditScreenAccessor {
    @Accessor
    PageTurnWidget getNextPageButton();

    @Accessor
    PageTurnWidget getPreviousPageButton();

    @Accessor
    ButtonWidget getDoneButton();

    @Accessor
    ButtonWidget getSignButton();

    @Accessor
    ButtonWidget getFinalizeButton();

    @Accessor
    ButtonWidget getCancelButton();
}