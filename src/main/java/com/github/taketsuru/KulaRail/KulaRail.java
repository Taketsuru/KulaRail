package com.github.taketsuru.KulaRail;

import java.util.logging.Logger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class KulaRail extends JavaPlugin {

    Logger log = Logger.getLogger("Minecraft");
    SignedBlockManager signedBlockManager = new SignedBlockManager();
    TeleporterManager teleporterManager = new TeleporterManager(signedBlockManager);
    WirelessDeviceManager wirelessDeviceManager = new WirelessDeviceManager(signedBlockManager);
    MinecartSpeedSignManager minecartSpeedSignManager = new MinecartSpeedSignManager(signedBlockManager);
    CommandManager commandManager = new CommandManager();

    @Override
    public void onDisable() {
	minecartSpeedSignManager.onDisable(this, commandManager);
	wirelessDeviceManager.onDisable(this);
	signedBlockManager.onDisable(this);
	commandManager.onDisable(this);
	saveConfig();
    }

    @Override
    public void onEnable() {
	commandManager.registerSubcommand("reload", new CommandExecutor() {
	    public boolean run(CommandSender sender, Command cmd, String label, String[] args) {
		onDisable();
		onEnable();
		return true;
	    }
	});

	signedBlockManager.onEnable(this);
	teleporterManager.onEnable(this);
	wirelessDeviceManager.onEnable(this);
	minecartSpeedSignManager.onEnable(this, commandManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
	return commandManager.onCommand(sender, cmd, label, args);
    }
    
}
