package com.github.taketsuru.KulaRail;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public interface CommandExecutor {
    boolean run(CommandSender sender, Command cmd, String label, String[] args);
}
