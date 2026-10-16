package com.adminwheel;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/** Settings: edit actions (name + command template), time options and reasons. */
public class ConfigScreen extends Screen {
    private static final int W = 330;
    private static final int H = 232;
    private static final int ROWS = 6;
    private static final int ROW_H = 24;
    private static final String[] TABS = {"Действия", "Время", "Причины"};

    private final AdminConfig cfg = AdminConfig.get();
    private int left;
    private int top;
    private int tab = 0;
    private int scroll = 0;

    public ConfigScreen() {
        super(Text.literal("Admin Wheel"));
    }

    private int size() {
        return switch (tab) {
            case 0 -> cfg.actions.size();
            case 1 -> cfg.times.size();
            default -> cfg.reasons.size();
        };
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            Text label = Text.literal(TABS[i]);
            if (i == tab) label = Text.literal(TABS[i]).formatted(Formatting.YELLOW);
            addDrawableChild(ButtonWidget.builder(label, b -> {
                tab = t;
                scroll = 0;
                clearAndInit();
            }).dimensions(left + 8 + i * 104, top + 20, 100, 18).build());
        }

        int size = size();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, size - ROWS)));

        for (int r = 0; r < ROWS; r++) {
            final int idx = scroll + r;
            if (idx >= size) break;
            int y = top + 54 + r * ROW_H;
            if (tab == 0) {
                AdminConfig.Action a = cfg.actions.get(idx);
                field(left + 8, y, 80, a.label, 32, s -> a.label = s);
                field(left + 92, y, 160, a.command, 200, s -> a.command = s);
                toggle(left + 256, y, "T", a.time, v -> a.time = v);
                toggle(left + 278, y, "R", a.reason, v -> a.reason = v);
                deleteButton(left + 300, y, () -> cfg.actions.remove(idx));
            } else if (tab == 1) {
                AdminConfig.TimeOpt t = cfg.times.get(idx);
                field(left + 8, y, 120, t.label, 32, s -> t.label = s);
                field(left + 132, y, 120, t.value, 32, s -> t.value = s);
                deleteButton(left + 300, y, () -> cfg.times.remove(idx));
            } else {
                field(left + 8, y, 284, cfg.reasons.get(idx), 100, s -> cfg.reasons.set(idx, s));
                deleteButton(left + 300, y, () -> cfg.reasons.remove(idx));
            }
        }

        int by = top + H - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Добавить"), b -> {
            if (tab == 0) {
                cfg.actions.add(new AdminConfig.Action("Новое", "kick {player} {reason}", false, true, 0x4FC3F7));
            } else if (tab == 1) {
                cfg.times.add(new AdminConfig.TimeOpt("1 мин", "1m"));
            } else {
                cfg.reasons.add("Причина");
            }
            scroll = Integer.MAX_VALUE;
            clearAndInit();
        }).dimensions(left + 8, by, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Сброс"), b -> {
            cfg.resetDefaults();
            scroll = 0;
            clearAndInit();
        }).dimensions(left + 102, by, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(left + W - 8 - 80, by, 80, 20).build());
    }

    private void field(int x, int y, int w, String text, int max, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(max);
        f.setText(text == null ? "" : text);
        f.setChangedListener(onChange);
        addDrawableChild(f);
    }

    private void toggle(int x, int y, String letter, boolean on, Consumer<Boolean> set) {
        boolean[] state = {on};
        ButtonWidget[] ref = new ButtonWidget[1];
        ref[0] = ButtonWidget.builder(letterText(letter, on), b -> {
            state[0] = !state[0];
            set.accept(state[0]);
            ref[0].setMessage(letterText(letter, state[0]));
        }).dimensions(x, y, 20, 18).build();
        addDrawableChild(ref[0]);
    }

    private static Text letterText(String letter, boolean on) {
        return Text.literal(letter).formatted(on ? Formatting.GREEN : Formatting.DARK_GRAY);
    }

    private void deleteButton(int x, int y, Runnable remove) {
        addDrawableChild(ButtonWidget.builder(Text.literal("x").formatted(Formatting.RED), b -> {
            remove.run();
            clearAndInit();
        }).dimensions(x, y, 20, 18).build());
    }

    @Override
    public void render(MatrixStack m, int mx, int my, float delta) {
        renderBackground(m);
        fill(m, left - 1, top - 1, left + W + 1, top + H + 1, 0xFF151515);
        fill(m, left, top, left + W, top + H, 0xF0303030);

        drawTextWithShadow(m, textRenderer, Text.literal("Admin Wheel: настройки"), left + 8, top + 6, 0xFFFFFF);

        String hint;
        if (tab == 0) {
            hint = "Команда: {player} {time} {reason}   T=время R=причина";
        } else if (tab == 1) {
            hint = "Название на колесе  |  значение для {time}";
        } else {
            hint = "Текст причины (он же подставляется в {reason})";
        }
        drawTextWithShadow(m, textRenderer, Text.literal(hint), left + 8, top + 42, 0x909090);

        if (size() == 0) {
            drawTextWithShadow(m, textRenderer, Text.literal("Пусто. Нажми «+ Добавить»"), left + 10, top + 60, 0x808080);
        }

        super.render(m, mx, my, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll -= (int) Math.signum(amount);
        clearAndInit();
        return true;
    }

    @Override
    public void removed() {
        AdminConfig.save();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
