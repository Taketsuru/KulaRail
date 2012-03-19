package com.github.taketsuru.KulaRail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class CommandManager {

    Map<String, List<CommandExecutor>> subcommands = new HashMap<String, List<CommandExecutor>>();

    public void onEnable(Plugin plugin) {
	
    }
    
    public void onDisable(Plugin plugin) {
	subcommands.clear();
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
