package com.adminwheel;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * GTA-style weapon wheel. Hold the key, move the mouse to a segment, left click to choose.
 * Stages: action -> time (if the action uses it) -> reason (if the action uses it) -> command is sent.
 * Right click goes one step back. Releasing the key closes the wheel.
 */
public class RadialScreen extends Screen {
    private static final float INNER = 48f;
    private static final float OUTER = 112f;

    private record Item(String label, int color) {}

    private final String target;
    private final KeyBinding key;
    private final AdminConfig cfg = AdminConfig.get();

    private int stage = 0; // 0 = action, 1 = time, 2 = reason
    private AdminConfig.Action action;
    private AdminConfig.TimeOpt time;
    private int hovered = -1;

    public RadialScreen(String target, KeyBinding key) {
        super(Text.literal("Admin wheel"));
        this.target = target;
        this.key = key;
    }

    private List<Item> items() {
        List<Item> out = new ArrayList<>();
        if (stage == 0) {
            for (AdminConfig.Action a : cfg.actions) out.add(new Item(a.label, a.color));
        } else if (stage == 1) {
            for (AdminConfig.TimeOpt t : cfg.times) out.add(new Item(t.label, action.color));
        } else {
            for (String r : cfg.reasons) out.add(new Item(r, action.color));
        }
        return out;
    }

    private String crumb() {
        if (stage == 0) return "Действие";
        StringBuilder sb = new StringBuilder(action.label);
        if (time != null) sb.append(" > ").append(time.label);
        sb.append(stage == 1 ? " > Время" : " > Причина");
        return sb.toString();
    }

    @Override
    public void tick() {
        if (!AdminWheelClient.isHeld(key)) {
            close();
        }
    }

    @Override
    public void render(MatrixStack m, int mx, int my, float delta) {
        fill(m, 0, 0, width, height, 0x50000000);

        List<Item> items = items();
        int n = items.size();
        int cx = width / 2;
        int cy = height / 2;
        hovered = hover(mx, my, cx, cy, n);

        if (n > 0) {
            drawRing(m, items, cx, cy);
        }

        drawCenteredTextWithShadow(m, textRenderer, Text.literal(target).formatted(Formatting.WHITE), cx, cy - 10, 0xFFFFFF);
        String crumb = textRenderer.trimToWidth(crumb(), 90);
        drawCenteredTextWithShadow(m, textRenderer, Text.literal(crumb), cx, cy + 2, 0xAAAAAA);

        float seg = n > 0 ? (float) (Math.PI * 2 / n) : 0f;
        float rMid = (INNER + OUTER) / 2f;
        for (int i = 0; i < n; i++) {
            float ang = i * seg;
            int lx = cx + Math.round(rMid * (float) Math.sin(ang));
            int ly = cy - Math.round(rMid * (float) Math.cos(ang)) - 4;
            String label = textRenderer.trimToWidth(items.get(i).label(), 62);
            drawCenteredTextWithShadow(m, textRenderer, Text.literal(label), lx, ly, i == hovered ? 0xFFFFFF : 0xE0E0E0);
        }

        if (n == 0) {
            drawCenteredTextWithShadow(m, textRenderer, Text.literal("Список пуст: настрой в меню"), cx, cy + 20, 0xFF6666);
        }

        super.render(m, mx, my, delta);
    }

    private int hover(int mx, int my, int cx, int cy, int n) {
        if (n == 0) return -1;
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < INNER * 0.5) return -1;
        double ang = Math.atan2(dx, -dy); // 0 = up, clockwise
        if (ang < 0) ang += Math.PI * 2;
        double seg = Math.PI * 2 / n;
        return (int) ((ang + seg / 2) / seg) % n;
    }

    private void drawRing(MatrixStack m, List<Item> items, int cx, int cy) {
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f mat = m.peek().getPositionMatrix();

        int n = items.size();
        float seg = (float) (Math.PI * 2 / n);
        float gap = n > 1 ? 0.025f : 0f;

        for (int i = 0; i < n; i++) {
            boolean h = i == hovered;
            int c = items.get(i).color();
            int r = (c >> 16) & 255;
            int g = (c >> 8) & 255;
            int b = c & 255;
            int a = 165;
            float outer = OUTER;
            if (h) {
                r = Math.min(255, r + 45);
                g = Math.min(255, g + 45);
                b = Math.min(255, b + 45);
                a = 240;
                outer = OUTER + 6f;
            }
            float a0 = i * seg - seg / 2f + gap;
            float a1 = i * seg + seg / 2f - gap;
            int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 0.1f));
            for (int s = 0; s < steps; s++) {
                float s0 = a0 + (a1 - a0) * s / steps;
                float s1 = a0 + (a1 - a0) * (s + 1) / steps;
                quad(buf, mat, cx, cy, INNER, outer, s0, s1, r, g, b, a);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buf, Matrix4f mat, int cx, int cy, float r0, float r1,
                             float s0, float s1, int r, int g, int b, int a) {
        float sin0 = (float) Math.sin(s0), cos0 = (float) Math.cos(s0);
        float sin1 = (float) Math.sin(s1), cos1 = (float) Math.cos(s1);
        buf.vertex(mat, cx + r0 * sin0, cy - r0 * cos0, 0f).color(r, g, b, a).next();
        buf.vertex(mat, cx + r1 * sin0, cy - r1 * cos0, 0f).color(r, g, b, a).next();
        buf.vertex(mat, cx + r1 * sin1, cy - r1 * cos1, 0f).color(r, g, b, a).next();
        buf.vertex(mat, cx + r0 * sin1, cy - r0 * cos1, 0f).color(r, g, b, a).next();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0) {
            choose(hovered);
            return true;
        }
        if (button == 1) {
            if (stage == 0) {
                close();
            } else {
                stage = (stage == 2 && action.time && !cfg.times.isEmpty()) ? 1 : 0;
                if (stage == 0) {
                    action = null;
                }
                time = null;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void choose(int i) {
        if (stage == 0) {
            if (i >= cfg.actions.size()) return;
            action = cfg.actions.get(i);
            time = null;
            if (action.time && !cfg.times.isEmpty()) {
                stage = 1;
            } else if (action.reason && !cfg.reasons.isEmpty()) {
                stage = 2;
            } else {
                send(null, null);
            }
        } else if (stage == 1) {
            if (i >= cfg.times.size()) return;
            time = cfg.times.get(i);
            if (action.reason && !cfg.reasons.isEmpty()) {
                stage = 2;
            } else {
                send(time.value, null);
            }
        } else {
            if (i >= cfg.reasons.size()) return;
            send(time == null ? null : time.value, cfg.reasons.get(i));
        }
    }

    private void send(String timeValue, String reason) {
        String cmd = AdminConfig.build(action, target, timeValue, reason);
        if (client != null && client.player != null && !cmd.isEmpty()) {
            client.player.networkHandler.sendChatCommand(cmd);
            client.player.sendMessage(Text.literal("/" + cmd).formatted(Formatting.GRAY), true);
        }
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
