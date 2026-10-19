package com.wayback09.campfire.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public class CampfireButton extends PressableWidget {
    public interface PressAction {
        void onPress(CampfireButton button);
    }

    private final PressAction onPress;
    private final boolean accent;

    public CampfireButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        this(x, y, width, height, message, onPress, hasOrangeText(message));
    }

    public CampfireButton(int x, int y, int width, int height, Text message, PressAction onPress, boolean accent) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.accent = accent;
    }

    public static boolean hasOrangeText(Text text) {
        if (text == null) return false;
        String str = text.getString();
        return str.contains("🔥") || str.toLowerCase().contains("host") || str.contains("★");
    }

    public static Builder builder(Text message, PressAction onPress) {
        return new Builder(message, onPress);
    }

    public static Builder accentBuilder(Text message, PressAction onPress) {
        return new Builder(message, onPress).accent(true);
    }

    public static class Builder {
        private final Text message;
        private final PressAction onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private boolean accent;
        private Tooltip tooltip;

        public Builder(Text message, PressAction onPress) {
            this.message = message;
            this.onPress = onPress;
            this.accent = hasOrangeText(message);
        }

        public Builder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(Tooltip tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder accent(boolean accent) {
            this.accent = accent;
            return this;
        }

        public CampfireButton build() {
            CampfireButton btn = new CampfireButton(this.x, this.y, this.width, this.height, this.message, this.onPress, this.accent);
            if (this.tooltip != null) {
                btn.setTooltip(this.tooltip);
            }
            return btn;
        }
    }

    @Override
    public void onPress() {
        if (this.onPress != null) {
            this.onPress.onPress(this);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean hovered = isHovered();
        boolean pressed = isSelected() && hovered;

        int bgColor;
        int borderColor;
        int textColor;

        if (!this.active) {
            bgColor = 0x80141519;
            borderColor = 0xFF23252E;
            textColor = 0xFF64748B;
        } else if (this.accent) {
            bgColor = pressed ? 0xFF1C150F : (hovered ? 0xFF2D2116 : 0xFF211811);
            borderColor = hovered ? 0xFFFF9900 : 0xFFD97706;
            textColor = hovered ? 0xFFFFB84D : 0xFFFF9900;
        } else {
            bgColor = pressed ? 0xFF14151B : (hovered ? 0xFF282B37 : 0xFF1E2028);
            borderColor = hovered ? 0xFF474C60 : 0xFF2F313D;
            textColor = hovered ? 0xFFFFFFFF : 0xFFE2E8F0;
        }

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        context.fill(x, y, x + w, y + h, bgColor);

        context.fill(x, y, x + w, y + 1, borderColor);
        context.fill(x, y + h - 1, x + w, y + h, borderColor);
        context.fill(x, y + 1, x + 1, y + h - 1, borderColor);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);

        int textY = y + (h - 8) / 2;
        context.drawCenteredTextWithShadow(client.textRenderer, getMessage(), x + w / 2, textY, textColor);
    }
}
