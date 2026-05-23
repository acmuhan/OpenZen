package shit.zen.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AgentWarningScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 156;

    private final Screen parent;

    public AgentWarningScreen(Screen parent) {
        super(Component.literal("OpenZen Patch Warning"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int buttonY = panelY + 124;

        this.addRenderableWidget(Button.builder(
                Component.literal("我知道了"),
                button -> this.minecraft.setScreen(this.parent)
        ).bounds(centerX - 75, buttonY, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE6101018);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, 0xFFFF6B6B);
        graphics.drawCenteredString(this.font, "OpenZen 补丁未生效", centerX, panelY + 14, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "当前只是普通 Forge Mod 加载，核心 Patch 没有安装。", centerX, panelY + 38, 0xFFDCDC);
        graphics.drawCenteredString(this.font, "部分功能可能打不开或没有效果。", centerX, panelY + 54, 0xFFDCDC);
        graphics.drawCenteredString(this.font, "请使用 Actions 里的 OpenZenLoader.exe 启动，", centerX, panelY + 78, 0xDDEEFF);
        graphics.drawCenteredString(this.font, "或在启动器 Java 虚拟机参数加入 -javaagent:<OpenZen jar>", centerX, panelY + 94, 0xDDEEFF);

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
