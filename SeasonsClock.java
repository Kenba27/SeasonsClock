package vn.seasonsclock;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Addon cho SeasonsPlus 1.4.1: them dem nguoc + lich ngay/thang/nam qua PlaceholderAPI.
 * Doc du lieu cua SeasonsPlus bang reflection (khong sua plugin goc).
 */
public class SeasonsClock extends JavaPlugin {

    private static final String[] ORDER = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};

    private Object seasonManager;
    private Method getCurrentSeason;
    private Method changeSeason;
    private Field startField;
    private Field durationField;

    private ClockExpansion expansion;
    private File dataFile;
    private int year = 1;
    private String lastSeason = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");

        Plugin seasons = Bukkit.getPluginManager().getPlugin("Seasons");
        if (seasons == null) {
            getLogger().severe("Khong tim thay plugin Seasons (SeasonsPlus). Tat SeasonsClock.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            Object manager = seasons.getClass().getMethod("getSeasonManager").invoke(seasons);
            Class<?> cls = manager.getClass();
            Method get = cls.getMethod("getCurrentSeason");
            Method change = cls.getDeclaredMethod("changeSeason");
            change.setAccessible(true);
            Field start = cls.getDeclaredField("seasonStartRealTime");
            start.setAccessible(true);
            Field duration = cls.getDeclaredField("seasonDuration");
            duration.setAccessible(true);

            this.getCurrentSeason = get;
            this.changeSeason = change;
            this.startField = start;
            this.durationField = duration;
            this.seasonManager = manager;
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().severe("Khong tuong thich voi phien ban Seasons nay: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        year = data.getInt("year", getConfig().getInt("start-year", 1));
        String current = currentSeason();
        long saved = data.getLong("elapsed-ms", -1L);
        if (getConfig().getBoolean("keep-progress-on-restart", true)
                && saved >= 0 && saved < durationMs()
                && current.equals(data.getString("season", ""))) {
            setStart(System.currentTimeMillis() - saved);
        }
        lastSeason = current;

        expansion = new ClockExpansion(this);
        expansion.register();

        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::save, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (seasonManager != null) {
            save();
        }
        if (expansion != null) {
            expansion.unregister();
        }
    }

    private void tick() {
        String now = currentSeason();
        if (!now.equals(lastSeason)) {
            if ("WINTER".equals(lastSeason) && "SPRING".equals(now)) {
                year++;
            }
            lastSeason = now;
            save();
        }
        if (getConfig().getBoolean("exact-season-change", true) && elapsedMs() >= durationMs()) {
            try {
                changeSeason.invoke(seasonManager);
            } catch (ReflectiveOperationException e) {
                getLogger().warning("Khong the doi mua dung gio: " + e);
            }
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("year", year);
        data.set("season", currentSeason());
        data.set("elapsed-ms", Math.min(elapsedMs(), durationMs() - 1));
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Khong the luu data.yml: " + e);
        }
    }

    // ---- doc du lieu tu SeasonsPlus ----

    private String currentSeason() {
        try {
            Object s = getCurrentSeason.invoke(seasonManager);
            return s == null ? "" : ((Enum<?>) s).name();
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    private long startMs() {
        try {
            return startField.getLong(seasonManager);
        } catch (IllegalAccessException e) {
            return System.currentTimeMillis();
        }
    }

    private void setStart(long value) {
        try {
            startField.setLong(seasonManager, value);
        } catch (IllegalAccessException e) {
            getLogger().warning("Khong the khoi phuc thoi gian mua: " + e);
        }
    }

    private long durationMs() {
        try {
            return Math.max(1, durationField.getInt(seasonManager)) * 60000L;
        } catch (IllegalAccessException e) {
            return 60000L;
        }
    }

    private long elapsedMs() {
        return Math.max(0L, System.currentTimeMillis() - startMs());
    }

    // ---- API cho placeholder ----

    public long timeLeftSeconds() {
        long left = Math.max(0L, durationMs() - elapsedMs());
        return (left + 999L) / 1000L;
    }

    /** Tra ve {ngay, thang, nam}. */
    public int[] calendar() {
        long dur = durationMs();
        long el = Math.min(elapsedMs(), dur - 1);
        int monthsPerSeason = Math.max(1, getConfig().getInt("months-per-season", 3));
        int daysPerMonth = Math.max(1, getConfig().getInt("days-per-month", 30));
        long totalDays = (long) monthsPerSeason * daysPerMonth;

        long dayIndex = el * totalDays / dur;
        int monthInSeason = (int) (dayIndex / daysPerMonth);
        int day = (int) (dayIndex % daysPerMonth) + 1;

        int seasonIndex = 0;
        String cur = currentSeason();
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(cur)) {
                seasonIndex = i;
            }
        }
        int month = seasonIndex * monthsPerSeason + monthInSeason + 1;
        return new int[]{day, month, year};
    }

    public String formatDate() {
        int[] c = calendar();
        return getConfig().getString("date-format", "{day}/{month}/{year}")
                .replace("{day}", String.valueOf(c[0]))
                .replace("{month}", String.valueOf(c[1]))
                .replace("{year}", String.valueOf(c[2]));
    }
}
