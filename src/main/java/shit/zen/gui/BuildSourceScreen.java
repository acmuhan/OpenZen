package shit.zen.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import shit.zen.BuildInfo;

public class BuildSourceScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 142;

    private final Screen parent;

    public BuildSourceScreen(Screen parent) {
        super(Component.literal("OpenZen Build Notice"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int buttonY = panelY + 100;

        this.addRenderableWidget(Button.builder(
                Component.literal("跳转仓库"),
                button -> Util.getPlatform().openUri(BuildInfo.REPOSITORY_URL)
        ).bounds(centerX - 155, buttonY, 150, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("关闭弹窗"),
                button -> this.minecraft.setScreen(this.parent)
        ).bounds(centerX + 5, buttonY, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE6101018);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, 0xFF62D6FF);
        graphics.drawCenteredString(this.font, "OpenZen 构建来源提示", centerX, panelY + 16, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "您正在使用的是 " + BuildInfo.REPOSITORY_LABEL, centerX, panelY + 42, 0xDDEEFF);
        graphics.drawCenteredString(this.font, "构建的版本", centerX, panelY + 56, 0xDDEEFF);
        graphics.drawCenteredString(this.font, "SHA: " + BuildInfo.shortSha(), centerX, panelY + 76, 0xA8FFB0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
