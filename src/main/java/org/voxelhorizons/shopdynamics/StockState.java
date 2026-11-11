package org.voxelhorizons.shopdynamics;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/** Small durable state file; shop definitions and prices remain owned by ShopGUI+. */
public final class StockState {
    private final File file;
    private final Logger logger;
    private final YamlConfiguration yaml;

    public StockState(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> active(String id) { return yaml.getStringList("shops." + id + ".active"); }
    public List<String> bag(String id) { return yaml.getStringList("shops." + id + ".bag"); }

    public void put(String id, StockSelector.Draw draw) {
        yaml.set("shops." + id + ".active", draw.selected());
        yaml.set("shops." + id + ".bag", draw.remaining());
    }

    public long lastDay() { return yaml.getLong("clock.last-day", -1L); }
    public void lastDay(long day) { yaml.set("clock.last-day", day); }
    public long nextRealTime() { return yaml.getLong("clock.next-real-time", 0L); }
    public void nextRealTime(long value) { yaml.set("clock.next-real-time", value); }

    public void save() {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            logger.severe("Could not save stock state: " + e.getMessage());
        }
    }
}
