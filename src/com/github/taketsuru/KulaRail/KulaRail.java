package com.github.taketsuru.KulaRail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    Map<String, List<CommandExecutor>> subcommands = new HashMap<String, List<CommandExecutor>>();

    @Override
    public void onDisable() {
	signedBlockManager.onDisable(this);
	wirelessDeviceManager.onDisable(this);
	minecartSpeedSignManager.onDisable(this);
	saveConfig();
	subcommands.clear();
    }

    @Override
    public void onEnable() {
	registerSubcommand("reload", new CommandExecutor() {
	    public boolean run(CommandSender sender, Command cmd, String label, String[] args) {
		onDisable();
		onEnable();
		return true;
	    }
	});

	signedBlockManager.onEnable(this);
	teleporterManager.onEnable(this);
	wirelessDeviceManager.onEnable(this);
	minecartSpeedSignManager.onEnable(this);
    }
    
    public void registerSubcommand(String name, CommandExecutor executor) {
	List<CommandExecutor> entry = subcommands.get(name);
	if (entry == null) {
	    entry = new ArrayList<CommandExecutor>(1);
	    subcommands.put(name, entry);
	}
	entry.add(executor);
    }
    
    public void unregisterSubcommand(String name, CommandExecutor executor) {
	List<CommandExecutor> entry = subcommands.get(name);
	entry.remove(executor);
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
	if (cmd.getName().equalsIgnoreCase("kr")) {
	    if (args.length < 1) {
		return false;
	    }
	    List<CommandExecutor> entry = subcommands.get(args[0]);
	    if (entry != null) {
		for (CommandExecutor executor : entry) {
		    if (executor.run(sender, cmd, label, args)) {
			return true;
		    }
		}
	    }
	}
	
	return false;
    }
}
