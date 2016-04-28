package jp.dip.myuminecraft.kularail;

import java.util.Locale;
import java.util.ResourceBundle;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.Messages;
import jp.dip.myuminecraft.takecore.TakeCore;

public class KulaRail extends JavaPlugin {

    Logger                   logger;
    Messages                 messages;
    TakeCore                 takeCore;
    TeleporterManager        teleporterManager;
    WirelessDeviceManager    wirelessDeviceManager;
    MinecartSpeedSignManager minecartSpeedSignManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ConfigurationSection config = getConfig();

        logger = new Logger(getLogger());
        Locale locale = Messages.getLocale(getConfig().getString("locale"));
        messages = new Messages(ResourceBundle.getBundle("messages", locale), locale);
        teleporterManager = new TeleporterManager(this, logger, messages);
        wirelessDeviceManager = new WirelessDeviceManager(this, logger, messages);
        minecartSpeedSignManager = new MinecartSpeedSignManager(this, logger, messages,
                config.getConfigurationSection("minecart_speed"));
    }

    @Override
    public void onDisable() {
        minecartSpeedSignManager.onDisable();
        wirelessDeviceManager.onDisable();
        teleporterManager.onDisable();
        logger = null;
        messages = null;
        teleporterManager = null;
        wirelessDeviceManager = null;
        minecartSpeedSignManager = null;
    }

}
