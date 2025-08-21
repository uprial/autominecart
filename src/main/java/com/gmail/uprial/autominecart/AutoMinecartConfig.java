package com.gmail.uprial.autominecart;

import com.gmail.uprial.autominecart.common.CustomLogger;
import com.gmail.uprial.autominecart.config.ConfigReaderSimple;
import com.gmail.uprial.autominecart.config.InvalidConfigException;
import org.bukkit.configuration.file.FileConfiguration;

public final class AutoMinecartConfig {
    private final boolean enabled;

    private AutoMinecartConfig(final boolean enabled) {
        this.enabled = enabled;
    }

    static boolean isDebugMode(FileConfiguration config, CustomLogger customLogger) throws InvalidConfigException {
        return ConfigReaderSimple.getBoolean(config, customLogger, "debug", "'debug' flag", false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static AutoMinecartConfig getFromConfig(FileConfiguration config, CustomLogger customLogger) throws InvalidConfigException {
        final boolean enabled = ConfigReaderSimple.getBoolean(config, customLogger, "enabled", "'enabled' flag", true);

        return new AutoMinecartConfig(enabled);
    }

    public String toString() {
        return String.format("enabled: %b", enabled);
    }
}
