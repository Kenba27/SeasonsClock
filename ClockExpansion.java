package vn.seasonsclock;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class ClockExpansion extends PlaceholderExpansion {

    private final SeasonsClock plugin;

    public ClockExpansion(SeasonsClock plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "seasonclock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SeasonsClock";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        switch (params.toLowerCase()) {
            case "timeleft": {
                long s = plugin.timeLeftSeconds();
                return String.format("%02d:%02d", s / 60, s % 60);
            }
            case "timeleft_seconds":
                return String.valueOf(plugin.timeLeftSeconds());
            case "day":
                return String.valueOf(plugin.calendar()[0]);
            case "month":
                return String.valueOf(plugin.calendar()[1]);
            case "year":
                return String.valueOf(plugin.calendar()[2]);
            case "date":
                return plugin.formatDate();
            default:
                return null;
        }
    }
}
