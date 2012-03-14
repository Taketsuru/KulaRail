package com.github.taketsuru.KulaRail;

import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

public class KulaRail extends JavaPlugin {

    @Override
    public void onDisable() {
	signedBlockManager.onDisable(this);
	wirelessDeviceManager.onDisable(this);
	saveConfig();
    }

    @Override
    public void onEnable() {
	signedBlockManager.onEnable(this);
	teleporterManager.onEnable(this);
	wirelessDeviceManager.onEnable(this);
    }

    Logger log = Logger.getLogger("Minecraft");
    SignedBlockManager signedBlockManager = new SignedBlockManager();
    TeleporterManager teleporterManager = new TeleporterManager(signedBlockManager);
    WirelessDeviceManager wirelessDeviceManager = new WirelessDeviceManager(signedBlockManager); 
}
