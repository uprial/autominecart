package com.gmail.uprial.autominecart;

import com.gmail.uprial.autominecart.common.CustomLogger;
import com.gmail.uprial.autominecart.config.InvalidConfigException;
import com.gmail.uprial.autominecart.listeners.MoveListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

import static com.gmail.uprial.autominecart.AutoMinecartCommandExecutor.COMMAND_NS;

public final class AutoMinecart extends JavaPlugin {
    private final String CONFIG_FILE_NAME = "config.yml";
    private final File configFile = new File(getDataFolder(), CONFIG_FILE_NAME);

    private CustomLogger consoleLogger = null;
    private AutoMinecartConfig autoMinecartConfig = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        consoleLogger = new CustomLogger(getLogger());
        autoMinecartConfig = loadConfig(getConfig(), consoleLogger);

        getServer().getPluginManager().registerEvents(new MoveListener(this, consoleLogger), this);

        getCommand(COMMAND_NS).setExecutor(new AutoMinecartCommandExecutor(this));
        consoleLogger.info("Plugin enabled");
    }

    public AutoMinecartConfig getAutoMinecartConfig() {
        return autoMinecartConfig;
    }

    void reloadConfig(CustomLogger userLogger) {
        reloadConfig();
        autoMinecartConfig = loadConfig(getConfig(), userLogger, consoleLogger);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        consoleLogger.info("Plugin disabled");
    }

    @Override
    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            saveResource(CONFIG_FILE_NAME, false);
        }
    }

    @Override
    public FileConfiguration getConfig() {
        return YamlConfiguration.loadConfiguration(configFile);
    }

    static AutoMinecartConfig loadConfig(FileConfiguration config, CustomLogger customLogger) {
        return loadConfig(config, customLogger, null);
    }

    private static AutoMinecartConfig loadConfig(FileConfiguration config, CustomLogger mainLogger, CustomLogger secondLogger) {
        AutoMinecartConfig autoMinecartConfig = null;
        try {
            boolean isDebugMode = AutoMinecartConfig.isDebugMode(config, mainLogger);
            mainLogger.setDebugMode(isDebugMode);
            if(secondLogger != null) {
                secondLogger.setDebugMode(isDebugMode);
            }

            autoMinecartConfig = AutoMinecartConfig.getFromConfig(config, mainLogger);
        } catch (InvalidConfigException e) {
            mainLogger.error(e.getMessage());
        }

        return autoMinecartConfig;
    }
}
