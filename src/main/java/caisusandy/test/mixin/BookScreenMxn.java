package caisusandy.test.mixin; 

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.font.TextHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BookScreen.class)
public class BookScreenMxn
extends Screen {
    private static final int MAX_TEXT_WIDTH = 114;
    private static final int MAX_TEXT_HEIGHT = 128;
    private static final int WIDTH = 192;
    private static final int HEIGHT = 192;
    private static final Text EDIT_TITLE_TEXT = Text.translatable("book.editTitle");
    private static final Text FINALIZE_WARNING_TEXT = Text.translatable("book.finalizeWarning");
    private static final OrderedText BLACK_CURSOR_TEXT = OrderedText.styledForwardsVisitedString("_", Style.EMPTY.withColor(Formatting.BLACK));
    private static final OrderedText GRAY_CURSOR_TEXT = OrderedText.styledForwardsVisitedString("_", Style.EMPTY.withColor(Formatting.GRAY));
    private final PlayerEntity player;
    private final ItemStack itemStack;
    private boolean dirty;
    private boolean signing;
    private int tickCounter;
    private int currentPage;
    private final List<String> pages = Lists.newArrayList();
    private String title = "";
    private final SelectionManager currentPageSelectionManager = new SelectionManager(this::getCurrentPageContent, this::setPageContent, this::getClipboard, this::setClipboard, string -> string.length() < 1024 && this.textRenderer.getWrappedLinesHeight((String)string, 114) <= 128);
    private final SelectionManager bookTitleSelectionManager = new SelectionManager(() -> this.title, string -> {
        this.title = string;
    }, this::getClipboard, this::setClipboard, string -> string.length() < 16);
    private long lastClickTime;
    private int lastClickIndex = -1;
    private PageTurnWidget nextPageButton;
    private PageTurnWidget previousPageButton;
    private ButtonWidget doneButton;
    private ButtonWidget signButton;
    private ButtonWidget finalizeButton;
    private ButtonWidget cancelButton;
    private final Hand hand;
    @Nullable
    private PageContent pageContent = PageContent.EMPTY;
    private Text pageIndicatorText = ScreenTexts.EMPTY;
    private final Text signedByText;

    public BookScreenMxn(PlayerEntity playerEntity, ItemStack itemStack, Hand hand) {
        super(NarratorManager.EMPTY);
        this.player = playerEntity;
        this.itemStack = itemStack;
        this.hand = hand;
        NbtCompound nbtCompound = itemStack.getNbt();
        if (nbtCompound != null) {
            BookScreen.filterPages(nbtCompound, this.pages::add);
        }
        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
        this.signedByText = Text.translatable("book.byAuthor", playerEntity.getName()).formatted(Formatting.DARK_GRAY);
    }

    private void setClipboard(String string) {
        if (this.client != null) {
            SelectionManager.setClipboard(this.client, string);
        }
    }

    private String getClipboard() {
        return this.client != null ? SelectionManager.getClipboard(this.client) : "";
    }

    private int countPages() {
        return this.pages.size();
    }

    @Override
    public void tick() {
        super.tick();
        ++this.tickCounter;
    }

    @Override
    protected void init() {
        this.invalidatePageContent();
        this.client.keyboard.setRepeatEvents(true);
        this.signButton = this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, 196, 98, 20, Text.translatable("book.signButton"), buttonWidget -> {
            this.signing = true;
            this.updateButtons();
        }));
        this.doneButton = this.addDrawableChild(new ButtonWidget(this.width / 2 + 2, 196, 98, 20, ScreenTexts.DONE, buttonWidget -> {
            this.client.setScreen(null);
            this.finalizeBook(false);
        }));
        this.finalizeButton = this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, 196, 98, 20, Text.translatable("book.finalizeButton"), buttonWidget -> {
            if (this.signing) {
                this.finalizeBook(true);
                this.client.setScreen(null);
            }
        }));
        this.cancelButton = this.addDrawableChild(new ButtonWidget(this.width / 2 + 2, 196, 98, 20, ScreenTexts.CANCEL, buttonWidget -> {
            if (this.signing) {
                this.signing = false;
            }
            this.updateButtons();
        }));
        int i = (this.width - 192) / 2;
        int j = 2;
        this.nextPageButton = this.addDrawableChild(new PageTurnWidget(i + 116, 159, true, buttonWidget -> this.openNextPage(), true));
        this.previousPageButton = this.addDrawableChild(new PageTurnWidget(i + 43, 159, false, buttonWidget -> this.openPreviousPage(), true));
        this.updateButtons();
    }

    private void openPreviousPage() {
        if (this.currentPage > 0) {
            --this.currentPage;
        }
        this.updateButtons();
        this.changePage();
    }

    private void openNextPage() {
        if (this.currentPage < this.countPages() - 1) {
            ++this.currentPage;
        } else {
            this.appendNewPage();
            if (this.currentPage < this.countPages() - 1) {
                ++this.currentPage;
            }
        }
        this.updateButtons();
        this.changePage();
    }

    @Override
    public void removed() {
        this.client.keyboard.setRepeatEvents(false);
    }

    private void updateButtons() {
        this.previousPageButton.visible = !this.signing && this.currentPage > 0;
        this.nextPageButton.visible = !this.signing;
        this.doneButton.visible = !this.signing;
        this.signButton.visible = !this.signing;
        this.cancelButton.visible = this.signing;
        this.finalizeButton.visible = this.signing;
        this.finalizeButton.active = !this.title.trim().isEmpty();
    }

    private void removeEmptyPages() {
        ListIterator<String> listIterator = this.pages.listIterator(this.pages.size());
        while (listIterator.hasPrevious() && listIterator.previous().isEmpty()) {
            listIterator.remove();
        }
    }

    private void finalizeBook(boolean bl) {
        if (!this.dirty) {
            return;
        }
        this.removeEmptyPages();
        this.writeNbtData(bl);
        int i = this.hand == Hand.MAIN_HAND ? this.player.getInventory().selectedSlot : 40;
        this.client.getNetworkHandler().sendPacket(new BookUpdateC2SPacket(i, this.pages, bl ? Optional.of(this.title.trim()) : Optional.empty()));
    }

    private void writeNbtData(boolean bl) {
        NbtList nbtList = new NbtList();
        this.pages.stream().map(NbtString::of).forEach(nbtList::add);
        if (!this.pages.isEmpty()) {
            this.itemStack.setSubNbt("pages", nbtList);
        }
        if (bl) {
            this.itemStack.setSubNbt("author", NbtString.of(this.player.getGameProfile().getName()));
            this.itemStack.setSubNbt("title", NbtString.of(this.title.trim()));
        }
    }

    private void appendNewPage() {
        if (this.countPages() >= 100) {
            return;
        }
        this.pages.add("");
        this.dirty = true;
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        if (super.keyPressed(i, j, k)) {
            return true;
        }
        if (this.signing) {
            return this.keyPressedSignMode(i, j, k);
        }
        boolean bl = this.keyPressedEditMode(i, j, k);
        if (bl) {
            this.invalidatePageContent();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int i) {
        if (super.charTyped(c, i)) {
            return true;
        }
        if (this.signing) {
            boolean bl = this.bookTitleSelectionManager.insert(c);
            if (bl) {
                this.updateButtons();
                this.dirty = true;
                return true;
            }
            return false;
        }
        if (SharedConstants.isValidChar(c)) {
            this.currentPageSelectionManager.insert(Character.toString(c));
            this.invalidatePageContent();
            return true;
        }
        return false;
    }

    private boolean keyPressedEditMode(int i, int j, int k) {
        if (Screen.isSelectAll(i)) {
            this.currentPageSelectionManager.selectAll();
            return true;
        }
        if (Screen.isCopy(i)) {
            this.currentPageSelectionManager.copy();
            return true;
        }
        if (Screen.isPaste(i)) {
            this.currentPageSelectionManager.paste();
            return true;
        }
        if (Screen.isCut(i)) {
            this.currentPageSelectionManager.cut();
            return true;
        }
        SelectionManager.SelectionType selectionType = Screen.hasControlDown() ? SelectionManager.SelectionType.WORD : SelectionManager.SelectionType.CHARACTER;
        switch (i) {
            case 259: {
                this.currentPageSelectionManager.delete(-1, selectionType);
                return true;
            }
            case 261: {
                this.currentPageSelectionManager.delete(1, selectionType);
                return true;
            }
            case 257: 
            case 335: {
                this.currentPageSelectionManager.insert("\n");
                return true;
            }
            case 263: {
                this.currentPageSelectionManager.moveCursor(-1, Screen.hasShiftDown(), selectionType);
                return true;
            }
            case 262: {
                this.currentPageSelectionManager.moveCursor(1, Screen.hasShiftDown(), selectionType);
                return true;
            }
            case 265: {
                this.moveUpLine();
                return true;
            }
            case 264: {
                this.moveDownLine();
                return true;
            }
            case 266: {
                this.previousPageButton.onPress();
                return true;
            }
            case 267: {
                this.nextPageButton.onPress();
                return true;
            }
            case 268: {
                this.moveToLineStart();
                return true;
            }
            case 269: {
                this.moveToLineEnd();
                return true;
            }
        }
        return false;
    }

    private void moveUpLine() {
        this.moveVertically(-1);
    }

    private void moveDownLine() {
        this.moveVertically(1);
    }

    private void moveVertically(int i) {
        int j = this.currentPageSelectionManager.getSelectionStart();
        int k = this.getPageContent().getVerticalOffset(j, i);
        this.currentPageSelectionManager.moveCursorTo(k, Screen.hasShiftDown());
    }

    private void moveToLineStart() {
        if (Screen.hasControlDown()) {
            this.currentPageSelectionManager.moveCursorToStart(Screen.hasShiftDown());
        } else {
            int i = this.currentPageSelectionManager.getSelectionStart();
            int j = this.getPageContent().getLineStart(i);
            this.currentPageSelectionManager.moveCursorTo(j, Screen.hasShiftDown());
        }
    }

    private void moveToLineEnd() {
        if (Screen.hasControlDown()) {
            this.currentPageSelectionManager.moveCursorToEnd(Screen.hasShiftDown());
        } else {
            PageContent pageContent = this.getPageContent();
            int i = this.currentPageSelectionManager.getSelectionStart();
            int j = pageContent.getLineEnd(i);
            this.currentPageSelectionManager.moveCursorTo(j, Screen.hasShiftDown());
        }
    }

    private boolean keyPressedSignMode(int i, int j, int k) {
        switch (i) {
            case 259: {
                this.bookTitleSelectionManager.delete(-1);
                this.updateButtons();
                this.dirty = true;
                return true;
            }
            case 257: 
            case 335: {
                if (!this.title.isEmpty()) {
                    this.finalizeBook(true);
                    this.client.setScreen(null);
                }
                return true;
            }
        }
        return false;
    }

    private String getCurrentPageContent() {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            return this.pages.get(this.currentPage);
        }
        return "";
    }

    private void setPageContent(String string) {
        if (this.currentPage >= 0 && this.currentPage < this.pages.size()) {
            this.pages.set(this.currentPage, string);
            this.dirty = true;
            this.invalidatePageContent();
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int i, int j, float f) {
        this.renderBackground(matrixStack);
        this.setFocused(null);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, BookScreen.BOOK_TEXTURE);
        int k = (this.width - 192) / 2;
        int l = 2;
        this.drawTexture(matrixStack, k, 2, 0, 0, 192, 192);
        if (this.signing) {
            boolean bl = this.tickCounter / 6 % 2 == 0;
            OrderedText orderedText = OrderedText.concat(OrderedText.styledForwardsVisitedString(this.title, Style.EMPTY), bl ? BLACK_CURSOR_TEXT : GRAY_CURSOR_TEXT);
            int m = this.textRenderer.getWidth(EDIT_TITLE_TEXT);
            this.textRenderer.draw(matrixStack, EDIT_TITLE_TEXT, (float)(k + 36 + (114 - m) / 2), 34.0f, 0);
            int n = this.textRenderer.getWidth(orderedText);
            this.textRenderer.draw(matrixStack, orderedText, (float)(k + 36 + (114 - n) / 2), 50.0f, 0);
            int o = this.textRenderer.getWidth(this.signedByText);
            this.textRenderer.draw(matrixStack, this.signedByText, (float)(k + 36 + (114 - o) / 2), 60.0f, 0);
            this.textRenderer.drawTrimmed(FINALIZE_WARNING_TEXT, k + 36, 82, 114, 0);
        } else {
            int p = this.textRenderer.getWidth(this.pageIndicatorText);
            this.textRenderer.draw(matrixStack, this.pageIndicatorText, (float)(k - p + 192 - 44), 18.0f, 0);
            PageContent pageContent = this.getPageContent();
            for (Line line : pageContent.lines) {
                this.textRenderer.draw(matrixStack, line.text, (float)line.x, (float)line.y, -16777216);
            }
            this.drawSelection(pageContent.selectionRectangles);
            this.drawCursor(matrixStack, pageContent.position, pageContent.atEnd);
        }
        super.render(matrixStack, i, j, f);
    }

    private void drawCursor(MatrixStack matrixStack, Position position, boolean bl) {
        if (this.tickCounter / 6 % 2 == 0) {
            position = this.absolutePositionToScreenPosition(position);
            if (!bl) {
                DrawableHelper.fill(matrixStack, position.x, position.y - 1, position.x + 1, position.y + this.textRenderer.fontHeight, -16777216);
            } else {
                this.textRenderer.draw(matrixStack, "_", (float)position.x, (float)position.y, 0);
            }
        }
    }

    private void drawSelection(Rect2i[] rect2is) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(0.0f, 0.0f, 255.0f, 255.0f);
        RenderSystem.disableTexture();
        RenderSystem.enableColorLogicOp();
        RenderSystem.logicOp(GlStateManager.LogicOp.OR_REVERSE);
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        for (Rect2i rect2i : rect2is) {
            int i = rect2i.getX();
            int j = rect2i.getY();
            int k = i + rect2i.getWidth();
            int l = j + rect2i.getHeight();
            bufferBuilder.vertex(i, l, 0.0).next();
            bufferBuilder.vertex(k, l, 0.0).next();
            bufferBuilder.vertex(k, j, 0.0).next();
            bufferBuilder.vertex(i, j, 0.0).next();
        }
        tessellator.draw();
        RenderSystem.disableColorLogicOp();
        RenderSystem.enableTexture();
    }

    private Position screenPositionToAbsolutePosition(Position position) {
        return new Position(position.x - (this.width - 192) / 2 - 36, position.y - 32);
    }

    private Position absolutePositionToScreenPosition(Position position) {
        return new Position(position.x + (this.width - 192) / 2 + 36, position.y + 32);
    }

    @Override
    public boolean mouseClicked(double d, double e, int i) {
        if (super.mouseClicked(d, e, i)) {
            return true;
        }
        if (i == 0) {
            long l = Util.getMeasuringTimeMs();
            PageContent pageContent = this.getPageContent();
            int j = pageContent.getCursorPosition(this.textRenderer, this.screenPositionToAbsolutePosition(new Position((int)d, (int)e)));
            if (j >= 0) {
                if (j == this.lastClickIndex && l - this.lastClickTime < 250L) {
                    if (!this.currentPageSelectionManager.isSelecting()) {
                        this.selectCurrentWord(j);
                    } else {
                        this.currentPageSelectionManager.selectAll();
                    }
                } else {
                    this.currentPageSelectionManager.moveCursorTo(j, Screen.hasShiftDown());
                }
                this.invalidatePageContent();
            }
            this.lastClickIndex = j;
            this.lastClickTime = l;
        }
        return true;
    }

    private void selectCurrentWord(int i) {
        String string = this.getCurrentPageContent();
        this.currentPageSelectionManager.setSelection(TextHandler.moveCursorByWords(string, -1, i, false), TextHandler.moveCursorByWords(string, 1, i, false));
    }

    @Override
    public boolean mouseDragged(double d, double e, int i, double f, double g) {
        if (super.mouseDragged(d, e, i, f, g)) {
            return true;
        }
        if (i == 0) {
            PageContent pageContent = this.getPageContent();
            int j = pageContent.getCursorPosition(this.textRenderer, this.screenPositionToAbsolutePosition(new Position((int)d, (int)e)));
            this.currentPageSelectionManager.moveCursorTo(j, true);
            this.invalidatePageContent();
        }
        return true;
    }

    private PageContent getPageContent() {
        if (this.pageContent == null) {
            this.pageContent = this.createPageContent();
            this.pageIndicatorText = Text.translatable("book.pageIndicator", this.currentPage + 1, this.countPages());
        }
        return this.pageContent;
    }

    private void invalidatePageContent() {
        this.pageContent = null;
    }

    private void changePage() {
        this.currentPageSelectionManager.putCursorAtEnd();
        this.invalidatePageContent();
    }

    private PageContent createPageContent() {
        int l;
        Position position;
        boolean bl;
        String string = this.getCurrentPageContent();
        if (string.isEmpty()) {
            return PageContent.EMPTY;
        }
        int i2 = this.currentPageSelectionManager.getSelectionStart();
        int j2 = this.currentPageSelectionManager.getSelectionEnd();
        IntArrayList intList = new IntArrayList();
        ArrayList<Line> list = Lists.newArrayList();
        MutableInt mutableInt = new MutableInt();
        MutableBoolean mutableBoolean = new MutableBoolean();
        TextHandler textHandler = this.textRenderer.getTextHandler();
        textHandler.wrapLines(string, 114, Style.EMPTY, true, (style, i, j) -> {
            int k = mutableInt.getAndIncrement();
            String string2 = string.substring(i, j);
            mutableBoolean.setValue(string2.endsWith("\n"));
            String string3 = StringUtils.stripEnd(string2, " \n");
            int fontHeight = k * this.textRenderer.fontHeight;
            Position p = this.absolutePositionToScreenPosition(new Position(0, fontHeight));
            intList.add(i);
            list.add(new Line(style, string3, p.x, p.y));
        });
        int[] is = intList.toIntArray();
        boolean bl2 = bl = i2 == string.length();
        if (bl && mutableBoolean.isTrue()) {
            position = new Position(0, list.size() * this.textRenderer.fontHeight);
        } else {
            int k = BookScreenMxn.getLineFromOffset(is, i2);
            l = this.textRenderer.getWidth(string.substring(is[k], i2));
            position = new Position(l, k * this.textRenderer.fontHeight);
        }
        ArrayList<Rect2i> list2 = Lists.newArrayList();
        if (i2 != j2) {
            int o;
            l = Math.min(i2, j2);
            int m = Math.max(i2, j2);
            int n = BookScreenMxn.getLineFromOffset(is, l);
            if (n == (o = BookScreenMxn.getLineFromOffset(is, m))) {
                int p = n * this.textRenderer.fontHeight;
                int q = is[n];
                list2.add(this.getLineSelectionRectangle(string, textHandler, l, m, p, q));
            } else {
                int p = n + 1 > is.length ? string.length() : is[n + 1];
                list2.add(this.getLineSelectionRectangle(string, textHandler, l, p, n * this.textRenderer.fontHeight, is[n]));
                for (int q = n + 1; q < o; ++q) {
                    int r = q * this.textRenderer.fontHeight;
                    String string2 = string.substring(is[q], is[q + 1]);
                    int s = (int)textHandler.getWidth(string2);
                    list2.add(this.getRectFromCorners(new Position(0, r), new Position(s, r + this.textRenderer.fontHeight)));
                }
                list2.add(this.getLineSelectionRectangle(string, textHandler, is[o], m, o * this.textRenderer.fontHeight, is[o]));
            }
        }
        return new PageContent(string, position, bl, is, list.toArray(new Line[0]), list2.toArray(new Rect2i[0]));
    }

    static int getLineFromOffset(int[] is, int i) {
        int j = Arrays.binarySearch(is, i);
        if (j < 0) {
            return -(j + 2);
        }
        return j;
    } 

    private Rect2i getLineSelectionRectangle(String string, TextHandler textHandler, int i, int j, int k, int l) {
        String string2 = string.substring(l, i);
        String string3 = string.substring(l, j);
        Position position = new Position((int)textHandler.getWidth(string2), k);
        Position position2 = new Position((int)textHandler.getWidth(string3), k + this.textRenderer.fontHeight);
        return this.getRectFromCorners(position, position2);
    }

    private Rect2i getRectFromCorners(Position position, Position position2) {
        Position position3 = this.absolutePositionToScreenPosition(position);
        Position position4 = this.absolutePositionToScreenPosition(position2);
        int i = Math.min(position3.x, position4.x);
        int j = Math.max(position3.x, position4.x);
        int k = Math.min(position3.y, position4.y);
        int l = Math.max(position3.y, position4.y);
        return new Rect2i(i, k, j - i, l - k);
    }

    @Environment(value=EnvType.CLIENT)
    static class PageContent {
        static final PageContent EMPTY = new PageContent("", new Position(0, 0), true, new int[]{0}, new Line[]{new Line(Style.EMPTY, "", 0, 0)}, new Rect2i[0]);
        private final String pageContent;
        final Position position;
        final boolean atEnd;
        private final int[] lineStarts;
        final Line[] lines;
        final Rect2i[] selectionRectangles;

        public PageContent(String string, Position position, boolean bl, int[] is, Line[] lines, Rect2i[] rect2is) {
            this.pageContent = string;
            this.position = position;
            this.atEnd = bl;
            this.lineStarts = is;
            this.lines = lines;
            this.selectionRectangles = rect2is;
        }

        public int getCursorPosition(TextRenderer textRenderer, Position position) {
            int i = position.y / textRenderer.fontHeight;
            if (i < 0) {
                return 0;
            }
            if (i >= this.lines.length) {
                return this.pageContent.length();
            }
            Line line = this.lines[i];
            return this.lineStarts[i] + textRenderer.getTextHandler().getTrimmedLength(line.content, position.x, line.style);
        }

        public int getVerticalOffset(int i, int j) {
            int o;
            int k = BookScreenMxn.getLineFromOffset(this.lineStarts, i);
            int l = k + j;
            if (0 <= l && l < this.lineStarts.length) {
                int m = i - this.lineStarts[k];
                int n = this.lines[l].content.length();
                o = this.lineStarts[l] + Math.min(m, n);
            } else {
                o = i;
            }
            return o;
        }

        public int getLineStart(int i) {
            int j = BookScreenMxn.getLineFromOffset(this.lineStarts, i);
            return this.lineStarts[j];
        }

        public int getLineEnd(int i) {
            int j = BookScreenMxn.getLineFromOffset(this.lineStarts, i);
            return this.lineStarts[j] + this.lines[j].content.length();
        }
    }

    @Environment(value=EnvType.CLIENT)
    static class Line {
        final Style style;
        final String content;
        final Text text;
        final int x;
        final int y;

        public Line(Style style, String string, int i, int j) {
            this.style = style;
            this.content = string;
            this.x = i;
            this.y = j;
            this.text = Text.literal(string).setStyle(style);
        }
    }

    @Environment(value=EnvType.CLIENT)
    static class Position {
        public final int x;
        public final int y;

        Position(int i, int j) {
            this.x = i;
            this.y = j;
        }
    }
}

