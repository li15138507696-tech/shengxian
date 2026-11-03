package net.guzhenren.shengxian.client;

import net.guzhenren.shengxian.core.InsightCatalog;
import net.guzhenren.shengxian.core.Phase;
import net.guzhenren.shengxian.network.AscensionActionPayload;
import net.guzhenren.shengxian.network.AscensionSnapshotPayload;
import net.guzhenren.shengxian.network.AscensionSnapshotRequestPayload;
import net.guzhenren.shengxian.network.InsightPayload;
import net.guzhenren.shengxian.network.QiIntentPayload;
import net.guzhenren.shengxian.server.AscensionLifecycleService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Complete diagnostic/operation book backed only by server-owned session snapshots. */
public final class QiControlScreen extends Screen {
    private enum Page { OVERVIEW, QI, INSIGHT, TRIBULATION, VITAL_GU, APERTURE }

    private static final int PANEL_HEIGHT = 304;
    private Page page = Page.OVERVIEW;
    /** Mini-game: the three intents the player drags, in ticks-of-rate units 0..1. */
    private static final double[] INTENT = {0.5, 0.5, 0.5};
    /** Half-width of the balance track in pixels. */
    private static final int TRACK_HALF = 96;
    private static final int TRACK_HEIGHT = 8;
    private static final int TRACK_STEP = 26;
    /** Index of the track being dragged, or -1. */
    private int dragging = -1;
    private boolean seek;
    private int selectedInsight;
    private Component status = Component.empty();
    private AscensionSnapshotPayload snapshot = AscensionClientState.latest();
    private final List<ActionButton> actionButtons = new ArrayList<>();
    private final List<Button> absorbingButtons = new ArrayList<>();

    private record ActionButton(Button button, int action) {}

    public QiControlScreen() {
        super(Component.translatable("screen.shengxian.qi"));
    }

    @Override
    protected void init() {
        clearWidgets();
        actionButtons.clear();
        absorbingButtons.clear();
        PacketDistributor.sendToServer(new AscensionSnapshotRequestPayload(true));
        int left = panelLeft();
        int top = panelTop();
        int pageWidth = (panelWidth() - 24) / Page.values().length;
        for (Page target : Page.values()) {
            boolean active = target == page;
            Button tab = Button.builder(Component.literal(
                            (active ? "◆ " : "") + pageName(target)), button -> {
                page = target;
                status = Component.empty();
                init();
            }).bounds(left + 12 + target.ordinal() * pageWidth, top + 44, pageWidth - 4, 16).build();
            tab.active = !active;
            addRenderableWidget(tab);
        }

        int center = width / 2;
        switch (page) {
            case OVERVIEW -> initOverview(center, top);
            case QI -> initQi(center, top);
            case INSIGHT -> initInsight(center, top);
            case TRIBULATION -> initTribulation(center, top);
            case VITAL_GU -> initVitalGu(center, top);
            case APERTURE -> initAperture(center, top);
        }
    }

    private int panelWidth() {
        return Math.max(280, Math.min(520, width - 16));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(4, (height - PANEL_HEIGHT) / 2);
    }

    // ---- 玄墨金线 theme ------------------------------------------------

    private static final int INK_PANEL = 0xF314100D;      // deep ink body
    private static final int INK_INNER = 0xE61C1712;      // slightly lifted inner field
    private static final int GOLD = 0xFFC9A86A;           // main gold line
    private static final int GOLD_DIM = 0xFF8A744E;       // muted gold
    private static final int GOLD_GLOW = 0x33C9A86A;      // translucent gold halo
    private static final int TEXT_MAIN = 0xFFE8DCC2;      // parchment text
    private static final int TEXT_SUB = 0xFFA89878;       // secondary text
    private static final int HEAVEN_QI = 0xFF7FA8D8;      // sky-blue jade
    private static final int EARTH_QI = 0xFF8FB07A;       // celadon green
    private static final int HUMAN_QI = 0xFFC98A6A;       // terracotta

    /** Ornamental corner: three stepped gold ticks meeting at right angles. */
    private void drawCorner(GuiGraphics graphics, int x, int y, int dx, int dy) {
        for (int i = 0; i < 3; i++) {
            int len = 9 - i * 2;
            graphics.fill(x + (dx > 0 ? 0 : -len), y + (dy > 0 ? 0 : -len),
                    x + (dx > 0 ? len : 0), y + (dy > 0 ? 1 : 0) + (dy > 0 ? 0 : 1), GOLD_DIM);
        }
        // Simplified L: one long arm + one short arm, both gold.
        graphics.fill(Math.min(x, x + dx * 9), y, Math.max(x, x + dx * 9), y + 1, GOLD);
        graphics.fill(x, Math.min(y, y + dy * 9), x + 1, Math.max(y, y + dy * 9), GOLD);
    }

    /** Draws the shared ink panel with a gold double border and corner ornaments. */
    private void drawPanel(GuiGraphics graphics, int left, int top, int panelWidth) {
        graphics.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, INK_PANEL);
        // soft inner field
        graphics.fill(left + 5, top + 5, left + panelWidth - 5, top + PANEL_HEIGHT - 5, INK_INNER);
        // gold double border
        graphics.renderOutline(left + 2, top + 2, panelWidth - 4, PANEL_HEIGHT - 4, GOLD);
        graphics.renderOutline(left + 5, top + 5, panelWidth - 10, PANEL_HEIGHT - 10, GOLD_DIM);
        // corner ornaments
        drawCorner(graphics, left + 5, top + 5, 1, 1);
        drawCorner(graphics, left + panelWidth - 6, top + 5, -1, 1);
        drawCorner(graphics, left + 5, top + PANEL_HEIGHT - 6, 1, -1);
        drawCorner(graphics, left + panelWidth - 6, top + PANEL_HEIGHT - 6, -1, -1);
        // faint gold glow band above the title
        graphics.fill(left + 24, top + 30, left + panelWidth - 24, top + 31, GOLD_GLOW);
    }

    /** Draws one ornamental side tab; the active one is filled gold on ink. */
    private void drawSideTab(GuiGraphics graphics, Page page, int index, int left, int top,
                             boolean active) {
        int h = 18;
        int y = top + 44 + index * (h + 4);
        if (active) {
            graphics.fill(left + 8, y, left + 34, y + h, GOLD);
            graphics.fill(left + 9, y + 1, left + 33, y + h - 1, INK_PANEL);
            graphics.renderOutline(left + 8, y, 27, h, GOLD);
        } else {
            graphics.fill(left + 8, y, left + 34, y + h, 0xFF221C15);
            graphics.renderOutline(left + 8, y, 27, h, GOLD_DIM);
        }
        String label = pageName(page);
        graphics.drawCenteredString(font, Component.literal(String.valueOf(label.charAt(0))),
                left + 21, y + 5, active ? GOLD : TEXT_SUB);
    }

    /** Diamond knob used on the qi sliders. */
    private void drawDiamond(GuiGraphics graphics, int cx, int cy, int half, int color) {
        for (int i = -half; i <= half; i++) {
            int width = half - Math.abs(i);
            graphics.fill(cx - width, cy + i, cx + width + 1, cy + i + 1, color);
        }
    }

    private static String pageName(Page page) {
        return switch (page) {
            case OVERVIEW -> "总览";
            case QI -> "三气";
            case INSIGHT -> "求知";
            case TRIBULATION -> "灾劫";
            case VITAL_GU -> "本命蛊";
            case APERTURE -> "仙窍";
        };
    }

    private void initOverview(int center, int top) {
        int left = panelLeft();
        int third = (panelWidth() - 24) / 3;
        addAction("备妥", AscensionLifecycleService.ACTION_PREPARE, left + 8, top + 222, third);
        addAction("冲击窍壁", AscensionLifecycleService.ACTION_SHATTER, left + 12 + third, top + 222, third);
        addAction("冲击不可取消", AscensionLifecycleService.ACTION_CANCEL, left + 16 + third * 2, top + 222, third);
        addAction("收束三气", AscensionLifecycleService.ACTION_OPEN, left + 8, top + 250, third);
        addAction("投入本命蛊", AscensionLifecycleService.ACTION_OFFER_GU, left + 12 + third, top + 250, third);
        addAction("形成仙窍", AscensionLifecycleService.ACTION_FORM, left + 16 + third * 2, top + 250, third);
    }

    /**
     * 纳气小游戏：三条速率轨道，拖动即可实时调气。
     *
     * <p>The three intents are dragged instead of typed, so the player keeps the three qi levels
     * aligned while pushing them up. The server still owns every value; this screen only sends
     * the same {@link QiIntentPayload} the command path sends.</p>
     */
    private void initQi(int center, int top) {
        addRenderableWidget(Button.builder(seekLabel(), button -> {
            seek = !seek;
            button.setMessage(seekLabel());
            status = Component.literal("已选择师法自然；分心会加速失衡，收益以服务端窗口为准。");
            send();
        }).bounds(center - 82, top + 236, 164, 20).build());
        Button match = Button.builder(Component.literal("速率配平"), button -> {
            // The canon-cited trap: human qi is released at 3.0/tick while the two ambient sources
            // absorb at 4.0/tick, so equal intent is NOT equal growth.
            INTENT[2] = Math.min(1.0, INTENT[0] * 4.0 / 3.0);
            INTENT[1] = INTENT[0];
            send();
            status = Component.literal("已按 4:4:3 的速率比配平三条轨道。");
        }).bounds(center - 82, top + 260, 80, 20).build();
        Button stop = Button.builder(Component.literal("停止抽纳"), button -> {
            PacketDistributor.sendToServer(new QiIntentPayload(0, 0, 0, false, true));
            status = Component.literal("已提交停止请求；自然气机与灾劫由服务器推进。");
        }).bounds(center + 2, top + 260, 80, 20).build();
        absorbingButtons.add(match);
        absorbingButtons.add(stop);
        addRenderableWidget(match);
        addRenderableWidget(stop);
    }

    private void initInsight(int center, int top) {
        addRenderableWidget(Button.builder(Component.literal("换一条"), button -> {
            selectedInsight = (selectedInsight + 1) % InsightCatalog.entries().size();
            status = Component.empty();
            init();
        }).bounds(center - 82, top + 184, 78, 20).build());
        Button seekButton = Button.builder(Component.literal("参悟"), button -> {
            var entry = InsightCatalog.entries().get(selectedInsight);
            PacketDistributor.sendToServer(new InsightPayload(entry.id()));
            status = Component.literal("已提交固定条目，等待服务器校验交感窗口。");
        }).bounds(center + 2, top + 184, 80, 20).build();
        absorbingButtons.add(seekButton);
        addRenderableWidget(seekButton);
    }

    private void initTribulation(int center, int top) {
        // Guardian interactions are tied to visible server carriers; no placeholder strike action is offered.
    }

    private void initVitalGu(int center, int top) {
        addAction("投入本命蛊", AscensionLifecycleService.ACTION_OFFER_GU,
                center - 76, top + 218, 152);
    }

    private void initAperture(int center, int top) {
        addAction("形成仙窍", AscensionLifecycleService.ACTION_FORM,
                center - 76, top + 218, 152);
    }

    private void addAction(String label, int action, int x, int y, int buttonWidth) {
        Button button = Button.builder(Component.literal(label), ignored -> {
            PacketDistributor.sendToServer(new AscensionActionPayload(action));
            status = Component.literal("已提交，等待服务器核对并回传状态。");
        }).bounds(x, y, buttonWidth, 20).build();
        actionButtons.add(new ActionButton(button, action));
        addRenderableWidget(button);
    }

    private EditBox field(int x, int y, String value) {
        EditBox box = new EditBox(font, x, y, 164, 20, Component.empty());
        box.setMaxLength(6);
        box.setValue(value);
        return box;
    }

    private Component seekLabel() {
        return Component.literal("师法自然：" + (seek ? "开" : "关"));
    }

    private boolean canUseAction(int action) {
        if (!snapshot.routeAvailable()) return false;
        if (!snapshot.exists()) return action == AscensionLifecycleService.ACTION_PREPARE;
        if (snapshot.phase() < 0 || snapshot.phase() >= Phase.values().length) return false;
        Phase current = Phase.values()[snapshot.phase()];
        return switch (action) {
            case AscensionLifecycleService.ACTION_PREPARE -> false;
            case AscensionLifecycleService.ACTION_SHATTER -> current == Phase.PREPARING;
            case AscensionLifecycleService.ACTION_CANCEL -> !snapshot.broken()
                    && (current == Phase.PREPARING || current == Phase.BREAKING);
            case AscensionLifecycleService.ACTION_OPEN -> current == Phase.ABSORBING;
            case AscensionLifecycleService.ACTION_OFFER_GU -> current == Phase.OPENING;
            case AscensionLifecycleService.ACTION_FORM -> current == Phase.FORMING;
            default -> false;
        };
    }

    private void send() {
        PacketDistributor.sendToServer(new QiIntentPayload(
                INTENT[0], INTENT[1], INTENT[2], seek, false));
        status = Component.literal("已提交调气；关闭界面也不会暂停服务器模拟。");
    }

    private static double parse(String text) {
        double value = Double.parseDouble(text.trim());
        if (!Double.isFinite(value)) throw new NumberFormatException("not finite");
        return Math.max(0, Math.min(1, value));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        snapshot = AscensionClientState.latest();
        for (ActionButton action : actionButtons) action.button().active = canUseAction(action.action());
        boolean absorbing = snapshot.routeAvailable() && snapshot.exists()
                && snapshot.phase() == Phase.ABSORBING.ordinal();
        for (Button button : absorbingButtons) button.active = absorbing;

        graphics.fill(0, 0, width, height, 0xB9080605);
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int center = width / 2;
        drawPanel(graphics, left, top, panelWidth);
        // Title: main line + seal-style subtitle
        graphics.drawCenteredString(font, title, center, top + 11, GOLD);
        graphics.drawCenteredString(font, Component.literal("· 三气汇卷 · 普通五转升仙 ·"),
                center, top + 24, TEXT_SUB);
        // gold rule under the title
        graphics.fill(left + 30, top + 38, left + panelWidth - 30, top + 39, GOLD_DIM);
        super.render(graphics, mouseX, mouseY, partialTick);

        switch (page) {
            case OVERVIEW -> renderOverview(graphics, center, top);
            case QI -> renderQi(graphics, center, top);
            case INSIGHT -> renderInsight(graphics, center, top);
            case TRIBULATION -> renderTribulation(graphics, center, top);
            case VITAL_GU -> renderVitalGu(graphics, center, top);
            case APERTURE -> renderAperture(graphics, center, top);
        }
        if (!status.getString().isEmpty()) {
            graphics.drawCenteredString(font, status, center, top + PANEL_HEIGHT - 20, GOLD);
            graphics.fill(left + 30, top + PANEL_HEIGHT - 34, left + panelWidth - 30,
                    top + PANEL_HEIGHT - 33, GOLD_GLOW);
        }
    }

    private void renderOverview(GuiGraphics graphics, int center, int top) {
        drawLine(graphics, center, top + 68, "当前阶段：" + phaseName(snapshot)
                + (snapshot.broken() ? " · 碎窍已不可逆" : ""));
        drawLine(graphics, center, top + 88, snapshot.routeAvailable()
                ? "普通路线已通过服务端能力检查" : "升仙入口关闭，完成必要机制并验收后开放");
        drawWrapped(graphics, snapshot.readinessMessage(), panelLeft() + 18,
                top + 110, panelWidth() - 36, GOLD_DIM);
        if (snapshot.failurePending()) {
            drawLine(graphics, center, top + 181, "待恢复失败后果：" + emptyAs(snapshot.failureSummary(), "已记录"));
        }
        drawLine(graphics, center, top + 205, snapshot.broken()
                ? "碎窍完成后不可返回准备阶段" : "准备与冲击仅由服务端检查资格、真元和阶段");
    }

    private void renderQi(GuiGraphics graphics, int center, int top) {
        int left = panelLeft();
        int trackLeft = left + 64;
        int trackWidth = panelWidth() - 128;
        drawLine(graphics, center, top + 62, qiSummary(snapshot));
        double mean = (snapshot.heaven() + snapshot.earth() + snapshot.human()) / 3.0;
        double[] values = {snapshot.heaven(), snapshot.earth(), snapshot.human()};
        String[] names = {"天", "地", "人"};
        int[] colors = {HEAVEN_QI, EARTH_QI, HUMAN_QI};
        double tolerance = Math.max(0.18, mean * 0.02);
        boolean allBalanced = true;
        for (int i = 0; i < 3; i++) {
            boolean balanced = Math.abs(values[i] - mean) <= tolerance;
            allBalanced &= balanced;
            int y = top + 86 + i * TRACK_STEP;
            // label + live value
            graphics.drawString(font, Component.literal(names[i]), trackLeft - 16, y + 1, colors[i]);
            graphics.drawString(font, Component.literal(fmt(values[i])),
                    trackLeft + trackWidth + 8, y + 1, balanced ? TEXT_MAIN : 0xFFB24B3E);
            // socket + gradient value bar with a gold tip
            int valueWidth = (int) Math.round(Math.min(1.0, values[i] / 64.0) * trackWidth);
            graphics.fill(trackLeft - 1, y - 1, trackLeft + trackWidth + 1, y + TRACK_HEIGHT + 1, 0xFF0C0A08);
            graphics.fill(trackLeft, y, trackLeft + valueWidth, y + TRACK_HEIGHT, colors[i]);
            graphics.fill(trackLeft, y, trackLeft + valueWidth, y + 2, 0x33FFFFFF);
            if (valueWidth > 2) graphics.fill(trackLeft + valueWidth - 2, y, trackLeft + valueWidth,
                    y + TRACK_HEIGHT, GOLD);
            // intent rail under the bar with a diamond knob
            int railY = y + TRACK_HEIGHT + 3;
            graphics.fill(trackLeft, railY, trackLeft + trackWidth, railY + 1, GOLD_DIM);
            int knobX = trackLeft + (int) Math.round(INTENT[i] * trackWidth);
            drawDiamond(graphics, knobX, railY, 3, GOLD);
            // rate caption on the right end of the rail
            graphics.drawString(font, Component.literal(String.format(Locale.ROOT, "%.0f%%", INTENT[i] * 100)),
                    trackLeft + trackWidth + 8, railY - 3, TEXT_SUB);
        }
        // risk meter: a thin blood-jade bar at the bottom of the tracks
        double riskRatio = Math.min(1.0, snapshot.maximumRisk() / 12.0);
        int meterY = top + 86 + 3 * TRACK_STEP + 6;
        graphics.fill(trackLeft - 1, meterY, trackLeft + trackWidth + 1, meterY + 5, 0xFF0C0A08);
        int riskWidth = (int) Math.round(riskRatio * trackWidth);
        graphics.fill(trackLeft, meterY, trackLeft + riskWidth, meterY + 5,
                riskRatio > 0.75 ? 0xFFB24B3E : riskRatio > 0.4 ? 0xFFC9A24A : 0xFF7A8C5A);
        graphics.drawString(font, Component.literal("劫"), trackLeft - 16, meterY, 0xFFB24B3E);
        String state = allBalanced ? "三气相谐" : "三气失衡";
        drawLine(graphics, center, top + 186,
                state + " · 平衡带 ±" + fmt(tolerance) + " · 外界 天 "
                        + fmt(snapshot.ambientHeaven()) + " / 地 " + fmt(snapshot.ambientEarth()));
        drawWrapped(graphics, "拖动金菱设定速率：人气上限 3.0/刻、天地各 4.0/刻，按 4/3 配平才同步。",
                left + 18, top + 202, panelWidth() - 36, GOLD_DIM);
    }

    /** Y band of one track, used by both rendering and hit-testing. */
    private int trackTop(int index) {
        return panelTop() + 86 + index * TRACK_STEP;
    }

    /** True when the click landed on any track; drags then only need the index. */
    private int trackAt(double mouseX, double mouseY) {
        if (page != Page.QI) return -1;
        int trackLeft = panelLeft() + 64;
        int trackWidth = panelWidth() - 128;
        if (mouseX < trackLeft - 4 || mouseX > trackLeft + trackWidth + 4) return -1;
        for (int i = 0; i < 3; i++) {
            int y = trackTop(i);
            if (mouseY >= y - 3 && mouseY <= y + TRACK_HEIGHT + 7) return i;
        }
        return -1;
    }

    private void setIntentFromMouse(int index, double mouseX) {
        int trackLeft = panelLeft() + 64;
        int trackWidth = panelWidth() - 128;
        double ratio = (mouseX - trackLeft) / (double) trackWidth;
        INTENT[index] = Math.max(0.0, Math.min(1.0, ratio));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int track = button == 0 ? trackAt(mouseX, mouseY) : -1;
        if (track >= 0) {
            dragging = track;
            setIntentFromMouse(track, mouseX);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging >= 0) {
            setIntentFromMouse(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging >= 0) {
            setIntentFromMouse(dragging, mouseX);
            dragging = -1;
            // Submit once per gesture: the server ticks the simulation, not every mouse pixel.
            send();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderInsight(GuiGraphics graphics, int center, int top) {
        drawLine(graphics, center, top + 68, "已得领悟 " + snapshot.insightEarned()
                + " · 已用 " + snapshot.insightSpent());
        drawLine(graphics, center, top + 88, "交感窗口 " + snapshot.windowTicks() + " tick");
        var entry = InsightCatalog.entries().get(selectedInsight);
        drawWrapped(graphics, entry.title() + "：" + entry.summary(), center - 210,
                top + 118, 420, 0xFF40382D);
        drawLine(graphics, center, top + 164, "只提交目录条目；服务端检查阶段、窗口、额度和重复领取");
    }

    private void renderTribulation(GuiGraphics graphics, int center, int top) {
        drawLine(graphics, center, top + 68, "当前活动载体：" + snapshot.tribulationCount());
        String summary = snapshot.tribulationSummary().isEmpty()
                ? "暂无已记录的活动灾劫" : snapshot.tribulationSummary();
        drawWrapped(graphics, summary, panelLeft() + 18, top + 94, panelWidth() - 36, TEXT_MAIN);
        drawWrapped(graphics, "天气与地气分别追踪。柳风、峥虎、音磬、荒兽与血雨属于不同原著个例，不会互相替代。",
                panelLeft() + 18, top + 152, panelWidth() - 36, GOLD_DIM);
        drawLine(graphics, center, top + 211, "护道者只处理外劫；升仙者仍须本人调和三气");
        drawLine(graphics, center, top + 232, String.format(Locale.ROOT,
                "纳气期催蛊反噬：确认结果点累计 %d 次后损失一只来源蛊（游戏参数）",
                (int) snapshot.backlashThreshold()));
    }

    private void renderVitalGu(GuiGraphics graphics, int center, int top) {
        drawLine(graphics, center, top + 72, "本命蛊：" + (snapshot.vitalGuBound() ? snapshot.vitalGuName() : "未绑定"));
        String state = !snapshot.vitalGuBound() ? "尚未核验本体真实本命蛊"
                : snapshot.exists() && snapshot.phase() >= Phase.OPENING.ordinal()
                ? snapshot.exists() && snapshot.phase() >= Phase.IMMORTAL.ordinal()
                    ? "原物完整托管于已成仙窍内"
                    : "身份已核验；投入操作由服务器持久托管"
                : "身份已核验；尚未投入";
        drawWrapped(graphics, state, panelLeft() + 18, top + 102, panelWidth() - 36, TEXT_MAIN);
        drawWrapped(graphics, "当前证据不足以确定普通路线通用的余气升炼映射，因此不会自动返还、升级或生成仙蛊。",
                panelLeft() + 18, top + 140, panelWidth() - 36, GOLD_DIM);
    }

    private void renderAperture(GuiGraphics graphics, int center, int top) {
        if (snapshot.areaMu() <= 0) {
            drawLine(graphics, center, top + 78, "仙窍尚未形成");
            drawLine(graphics, center, top + 106, "十绝体与未核特殊路线保持关闭");
        } else {
            drawLine(graphics, center, top + 78, String.format(Locale.ROOT, "福地 %.0f 亩", snapshot.areaMu()));
            drawLine(graphics, center, top + 101, String.format(Locale.ROOT, "时间比 1:%.1f", snapshot.timeRatio()));
            drawLine(graphics, center, top + 124, String.format(Locale.ROOT, "青提仙元 %.1f", snapshot.essence()));
            double net = (0.01 - snapshot.apertureUpkeep()) * snapshot.timeRatio() * 20;
            drawLine(graphics, center, top + 147, String.format(Locale.ROOT,
                    "维持 %.4f/内部刻，净收支 %+.2f/秒%s", snapshot.apertureUpkeep(), net,
                    net < 0 ? "（仙窍亏损，会停摆）" : ""));
        }
        drawWrapped(graphics, "模型记录福地档次与仙元；空间尺寸缩尺，仙元不与凡人真元混算。",
                panelLeft() + 18, top + 158, panelWidth() - 36, GOLD_DIM);
    }

    private String phaseName(AscensionSnapshotPayload snapshot) {
        if (!snapshot.exists()) return "尚未备妥";
        if (snapshot.phase() < 0 || snapshot.phase() >= Phase.values().length) return "未知";
        return switch (Phase.values()[snapshot.phase()]) {
            case PREPARING -> "准备";
            case BREAKING -> "冲击窍壁";
            case BROKEN -> "空窍已碎";
            case ABSORBING -> "纳气";
            case OPENING -> "投入本命蛊";
            case FORMING -> "仙窍定型";
            case IMMORTAL -> "已成仙";
            case FAILED -> "升仙失败";
        };
    }

    private String qiSummary(AscensionSnapshotPayload state) {
        if (!state.exists()) return "尚无升仙会话";
        return String.format(Locale.ROOT, "天 %.0f · 地 %.0f · 人 %.0f · 人气储备 %.0f",
                state.heaven(), state.earth(), state.human(), state.humanReserve());
    }

    private String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String emptyAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void drawLine(GuiGraphics graphics, int center, int y, String text) {
        graphics.drawCenteredString(font, Component.literal(text), center, y, TEXT_MAIN);
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        String remaining = text == null ? "" : text;
        while (!remaining.isEmpty()) {
            int end = 1;
            while (end < remaining.length() && font.width(remaining.substring(0, end + 1)) <= maxWidth) end++;
            graphics.drawString(font, remaining.substring(0, end), x, y, color);
            remaining = remaining.substring(end);
            y += 12;
        }
        return y;
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page == Page.QI && keyCode == 257) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}