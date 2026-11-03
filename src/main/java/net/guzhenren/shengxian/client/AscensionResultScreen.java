package net.guzhenren.shengxian.client;

import java.util.Locale;
import java.util.Objects;
import net.guzhenren.shengxian.network.AscensionOutcomePayload;
import net.guzhenren.shengxian.network.AscensionSnapshotPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A read-only ink-and-paper result sheet backed exclusively by the committed server packet. */
public final class AscensionResultScreen extends Screen {
    private static final int PAPER_WIDTH = 480, PAPER_HEIGHT = 312;
    private static final int INK = 0xFF303D38, SOFT_INK = 0xFF696D5C;
    private static final int JADE = 0xFF4D7969, CINNABAR = 0xFF955A4E;
    private final AscensionOutcomePayload outcome;
    private final AscensionSnapshotPayload snapshot;
    private float paperScale;
    private int paperLeft, paperTop;

    public AscensionResultScreen(AscensionOutcomePayload outcome) {
        super(Component.literal(outcome.successful() ? "升仙功成" : "升仙未成"));
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.snapshot = outcome.snapshot();
    }

    @Override
    protected void init() {
        paperScale = Math.min(1.0f, Math.min((width - 16) / (float) PAPER_WIDTH,
                (height - 16) / (float) PAPER_HEIGHT));
        paperScale = Math.max(0.1f, paperScale);
        paperLeft = Math.round((width - PAPER_WIDTH * paperScale) / 2);
        paperTop = Math.round((height - PAPER_HEIGHT * paperScale) / 2);
        addRenderableWidget(Button.builder(Component.literal("合卷"), button -> onClose())
                .bounds(paperLeft + Math.round(190 * paperScale), paperTop + Math.round(280 * paperScale),
                        Math.round(100 * paperScale), Math.max(12, Math.round(20 * paperScale))).build());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA00D1917);
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(paperLeft, paperTop, 0);
            pose.scale(paperScale, paperScale, 1);
            renderPaper(graphics);
        } finally {
            pose.popPose();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPaper(GuiGraphics g) {
        int accent = outcome.successful() ? JADE : CINNABAR;
        g.fill(3, 4, PAPER_WIDTH + 3, PAPER_HEIGHT + 4, 0x70202A24);
        g.fill(0, 0, PAPER_WIDTH, PAPER_HEIGHT, 0xFFE6DFC9);
        g.fill(7, 5, PAPER_WIDTH - 7, PAPER_HEIGHT - 5, 0xFFF1ECDB);
        g.renderOutline(7, 5, PAPER_WIDTH - 14, PAPER_HEIGHT - 10, 0x88646B55);
        // Deterministic translucent ridgelines and brush fibres need no textures or extra render target.
        for (int x = 9; x < PAPER_WIDTH - 9; x += 3) {
            int ridge = 266 + (int) (9 * Math.sin(x * 0.031) + 5 * Math.sin(x * 0.073));
            g.fill(x, ridge, x + 3, PAPER_HEIGHT - 7, 0x0D496755);
            if (x % 9 == 0) g.fill(x, 8, x + 1, PAPER_HEIGHT - 8, 0x025C503C);
        }
        g.fill(30, 22, 33, 58, accent);
        g.drawString(font, "渡 凡 成 仙", 42, 20, SOFT_INK, false);
        g.pose().pushPose();
        g.pose().translate(42, 35, 0);
        g.pose().scale(1.7f, 1.7f, 1);
        g.drawString(font, title, 0, 0, INK, false);
        g.pose().popPose();
        g.fill(409, 21, 447, 59, accent);
        g.renderOutline(412, 24, 32, 32, 0x99F1ECDB);
        g.drawCenteredString(font, outcome.successful() ? "成" : "憾", 428, 35, 0xFFF3EBD9);
        brush(g, 31, 72, 416, 0xA04B5C48);
        g.drawString(font, outcome.successful() ? "仙窍品级" : "终局记录", 33, 85, SOFT_INK, false);
        g.drawString(font, gradeName(), 126, 85, accent, false);
        String dimensions = outcome.successful()
                ? "福地 " + amount(snapshot.areaMu()) + " 亩    时间比 1:" + amount(snapshot.timeRatio())
                : failureSummary();
        drawFit(g, dimensions, 33, 103, 411, INK);
        if (outcome.successful()) {
            g.drawString(font, "青提仙元  " + amount(snapshot.essence()), 33, 121, INK, false);
        } else {
            g.drawString(font, snapshot.broken() ? "原空窍已碎" : "原空窍状态：未碎", 33, 121, CINNABAR, false);
        }
        brush(g, 31, 139, 416, 0x524B5C48);
        g.drawString(font, "三气平衡 · 结算记录", 33, 151, INK, false);
        double max = Math.max(1, Math.max(snapshot.heaven(), Math.max(snapshot.earth(), snapshot.human())));
        qi(g, "天", snapshot.heaven(), max, 33, 170, 0xFF6B91A1);
        qi(g, "地", snapshot.earth(), max, 174, 170, 0xFF8A966B);
        qi(g, "人", snapshot.human(), max, 315, 170, 0xFFC59469);
        g.drawString(font, "最大失衡风险  " + amount(snapshot.maximumRisk()), 33, 196, SOFT_INK, false);
        g.drawString(font, "人气余量  " + amount(snapshot.humanReserve()), 269, 196, SOFT_INK, false);
        brush(g, 31, 215, 416, 0x524B5C48);
        g.drawString(font, "悟道点变化", 33, 227, INK, false);
        g.drawString(font, "+" + snapshot.insightEarned() + "  /  −" + snapshot.insightSpent()
                + "    结余 " + (snapshot.insightEarned() - snapshot.insightSpent()), 174, 227, accent, false);
        drawFit(g, "本命蛊  " + (snapshot.vitalGuBound() ? snapshot.vitalGuName() : "未绑定"),
                33, 247, 411, SOFT_INK);
        g.drawCenteredString(font, "三气归寂 · 此卷据实留存", PAPER_WIDTH / 2, 265, SOFT_INK);
    }

    private void qi(GuiGraphics g, String name, double value, double maximum, int x, int y, int color) {
        g.drawString(font, name + "  " + amount(value), x, y, INK, false);
        g.fill(x, y + 14, x + 129, y + 17, 0x284A5346);
        g.fill(x, y + 14, x + (int) Math.round(129 * value / maximum), y + 17, color);
    }

    private String gradeName() {
        return switch (outcome.grade()) {
            case SMALL -> "小福地";
            case MEDIUM -> "中等福地";
            case UPPER -> "上等福地";
            case UNKNOWN -> "品级未知 · 已保留真实面积";
            case NONE -> "升仙失败 · 未获仙窍";
        };
    }

    private String failureSummary() {
        String text = snapshot.failureSummary();
        if (text.isBlank()) return "失败原因尚未归档";
        return text.replace("HEAVEN_OVERLOAD", "天气过盛")
                .replace("EARTH_OVERLOAD", "地气过浓")
                .replace("HUMAN_OVERLOAD", "人气过重")
                .replace("TRIBULATION_IMPACT", "外劫冲击")
                .replace("UNCLASSIFIED", "原因未分类");
    }

    private void drawFit(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (font.width(text) <= maxWidth) g.drawString(font, text, x, y, color, false);
        else g.drawString(font, font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…", x, y, color, false);
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%,.0f" : "%,.1f", value);
    }

    private static void brush(GuiGraphics g, int x, int y, int length, int color) {
        g.fill(x, y, x + length, y + 1, color);
        g.fill(x + 4, y + 1, x + length - 12, y + 2, (color & 0x00FFFFFF) | 0x21000000);
    }
}
