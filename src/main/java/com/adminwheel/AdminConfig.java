package com.adminwheel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AdminConfig {
    public static class Action {
        public String label = "";
        public String command = "";
        public boolean time = false;
        public boolean reason = false;
        public int color = 0x4FC3F7;

        public Action() {}

        public Action(String label, String command, boolean time, boolean reason, int color) {
            this.label = label;
            this.command = command;
            this.time = time;
            this.reason = reason;
            this.color = color;
        }
    }

    public static class TimeOpt {
        public String label = "";
        public String value = "";

        public TimeOpt() {}

        public TimeOpt(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }

    public List<Action> actions = new ArrayList<>();
    public List<TimeOpt> times = new ArrayList<>();
    public List<String> reasons = new ArrayList<>();
    /** Max distance (blocks) for picking the player you look at. */
    public double range = 100;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("adminwheel.json");
    private static AdminConfig instance;

    public static synchronized AdminConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static synchronized void load() {
        AdminConfig loaded = null;
        try {
            if (Files.exists(FILE)) {
                loaded = GSON.fromJson(Files.readString(FILE), AdminConfig.class);
            }
        } catch (Exception e) {
            System.err.println("[AdminWheel] Failed to read config: " + e);
        }
        if (loaded == null) {
            loaded = new AdminConfig();
            loaded.resetDefaults();
        }
        if (loaded.actions == null) loaded.actions = new ArrayList<>();
        if (loaded.times == null) loaded.times = new ArrayList<>();
        if (loaded.reasons == null) loaded.reasons = new ArrayList<>();
        if (loaded.range <= 0) loaded.range = 100;
        instance = loaded;
        save();
    }

    public static synchronized void save() {
        if (instance == null) return;
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (IOException e) {
            System.err.println("[AdminWheel] Failed to save config: " + e);
        }
    }

    public void resetDefaults() {
        actions.clear();
        actions.add(new Action("Мут", "mute {player} {time} {reason}", true, true, 0xE6A700));
        actions.add(new Action("Бан", "ban {player} {time} {reason}", true, true, 0xD32F2F));
        actions.add(new Action("Кик", "kick {player} {reason}", false, true, 0xF57C00));
        actions.add(new Action("Убить", "kill {player}", false, false, 0x7B1FA2));

        times.clear();
        times.add(new TimeOpt("1 час", "1h"));
        times.add(new TimeOpt("12 часов", "12h"));
        times.add(new TimeOpt("1 день", "1d"));
        times.add(new TimeOpt("Навсегда", ""));

        reasons.clear();
        reasons.add("Читы");
        reasons.add("Грифинг");
        reasons.add("Оскорбления");
        reasons.add("Спам");
        reasons.add("Реклама");
        reasons.add("Багоюз");
    }

    /** Fills the command template. A leading slash is optional, extra spaces are collapsed. */
    public static String build(Action a, String player, String time, String reason) {
        String c = a.command == null ? "" : a.command.trim();
        if (c.startsWith("/")) c = c.substring(1);
        c = c.replace("{player}", player)
                .replace("{time}", time == null ? "" : time)
                .replace("{reason}", reason == null ? "" : reason);
        return c.replaceAll("\\s+", " ").trim();
    }
}
