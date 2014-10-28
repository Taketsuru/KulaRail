package com.github.taketsuru.KulaRail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;

import org.yaml.snakeyaml.Yaml;

public class MinecartSpeedSignManager implements SignedBlockListener, Listener {

    static final String header = "[kr.maxspeed]";
    static final String speedLabelsFileName = "speeds.yml";
    Map<String, Double> speedLabels = new HashMap<String, Double>();
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;
    SignedBlockManager signedBlockManager;
    double baseSpeed = 0.4;
    Set<Minecart> monitored = new HashSet<Minecart>();
    int monitorTask = -1;

    public MinecartSpeedSignManager(SignedBlockManager signedBlockManager) {
	this.signedBlockManager = signedBlockManager;
	signedBlockManager.listen(this);
    }

    public void onEnable(Plugin plugin, CommandManager commandManager) {
	this.plugin = plugin;
	plugin.getServer().getPluginManager().registerEvents(this, plugin);

	commandManager.registerSubcommand("maxspeed", new CommandExecutor() {
	    public boolean run(CommandSender sender, Command cmd, String label, String[] args) {
		return doMaxSpeedCommand(sender, cmd, label, args);
	    }
	});

	commandManager.registerSubcommand("monitorcart", new CommandExecutor() {
	    public boolean run(CommandSender sender, Command cmd, String label, String[] args) {
		return doMonitorCommand(sender, cmd, label, args);
	    }
	});

	try {
	    reloadSpeedLabels();
	} catch (FileNotFoundException e) {
	    loadDefaultSpeedLabels();
	    try {
		saveSpeedLabels();
	    } catch (Exception ee) {
	    }
	} catch (IOException e) {
	    log.warning("Failed to load " + speedLabelsFileName);
	    loadDefaultSpeedLabels();	    
	}
    }

    public void onDisable(Plugin plugin, CommandManager commandManager) {
	stopMonitor();
	this.plugin = null;
	speedLabels.clear();
    }
 
    void loadDefaultSpeedLabels() {
	speedLabels.clear();
	speedLabels.put("normal", 1.0);
	speedLabels.put("rapid", 1.8);
	speedLabels.put("express", 3.0);
    }

    void reloadSpeedLabels() throws FileNotFoundException, IOException {
	speedLabels.clear();
	File file = new File(plugin.getDataFolder(), speedLabelsFileName);
	FileReader reader = new FileReader(file);
	try {
	    Yaml yaml = new Yaml();
	    Map<?, ?> loadedObj = (Map<?, ?>)yaml.loadAs(reader, speedLabels.getClass());
	    if (loadedObj == null) {
		log.warning("Format error in " + speedLabelsFileName + ". Default values will be loaded.");
		loadDefaultSpeedLabels();
		return;
	    }
	    for (Object key : loadedObj.keySet()) {
		Object value = loadedObj.get(key);
		try {
		    speedLabels.put((String)key, (Double)value);
		} catch (ClassCastException e) {
		    log.info(String.format("type error in %s at line \"%s: %s\"",
			    		   speedLabelsFileName, key.toString(), value.toString()));
		}
	    }
	} finally {
	    reader.close();
	}
    }
    
    void saveSpeedLabels() throws FileNotFoundException, IOException {
	File file = new File(plugin.getDataFolder(), speedLabelsFileName);
	FileWriter writer = new FileWriter(file);
	try {
	    Yaml yaml = new Yaml();
	    yaml.dump(speedLabels, writer);
	} finally {
	    writer.close();
	}
    }

    boolean doMaxSpeedCommand(CommandSender sender, Command cmd, String label, String[] args) {
	switch (args.length) {
	case 1:
	    if (! sender.hasPermission("kularail.maxspeed.list")) {
		sender.sendMessage("You don't have the permission to do it.");
		break;
	    }
	    for (String speedLabel : speedLabels.keySet()) {
		sender.sendMessage(String.format("%s - %g", speedLabel, speedLabels.get(speedLabel)));
	    }
	    break;
	    
	case 2:
	    if (! sender.hasPermission("kularail.maxspeed.remove")) {
		sender.sendMessage("You don't have the permission to do it.");
		break;
	    }
	    speedLabels.remove(args[1]);
	    try {
		saveSpeedLabels();
	    } catch (Exception e) {
		sender.sendMessage("Failed to write to " + speedLabelsFileName);
	    }
	    break;
	    
	case 3:
	    if (! sender.hasPermission("kularail.maxspeed.set")) {
		sender.sendMessage("You don't have the permission to do it.");
		break;
	    }
	    try {
		speedLabels.put(args[1], Double.valueOf(args[2]));
		saveSpeedLabels();
	    } catch (NumberFormatException e) {
		sender.sendMessage(String.format("Usage: %s %s <label> <speed>", label, args[0]));
	    } catch (Exception e) {
		sender.sendMessage("Failed to write to " + speedLabelsFileName);
	    }
	    break;

	default:
	    sender.sendMessage(String.format("Usage: \n  %s %s - show list of speed labels\n %s %s <label> <speed>",
		     			     label, args[0], label, args[0]));
	}
	
	return true;
    }

    boolean doMonitorCommand(CommandSender sender, Command cmd, String label, String[] args) {
	if (! sender.hasPermission("kularail.monitorcart")) {
	    sender.sendMessage("You don't have permission to do it.");
	    return true;
	}

	if (monitorTask < 0) {
	    monitorTask = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
		public void run() {
		    if (monitored.isEmpty()) {
			return;
		    }

		    Iterator<Minecart> iter = monitored.iterator();
		    while (iter.hasNext()) {
			Minecart cart = iter.next();
			if (cart.isEmpty()) {
			    iter.remove();
			    continue;
			}
			log.info(String.format("Minecart %s@[%d,%d,%d], velocity=[%g,%g,%g], maxspeed=%g",
				cart.getUniqueId().toString(),
				cart.getLocation().getBlockX(), cart.getLocation().getBlockY(), cart.getLocation().getBlockZ(),
				cart.getVelocity().getX(), cart.getVelocity().getY(), cart.getVelocity().getZ(),
				cart.getMaxSpeed()));
		    }
		}
	    }, 0, 20);
	    if (monitorTask < 0) {
		log.severe("Failed to schedule monitoring task.");
		return true;
	    }
	} else {
	    stopMonitor();
	}
	
	return true;
    }
    
    void stopMonitor() {
	if (0 <= monitorTask) {
	    plugin.getServer().getScheduler().cancelTask(monitorTask);
	    monitorTask = -1;
	}
    }

    public boolean isSpeedSign(SignedBlock block) {
	return block.readHeader().equalsIgnoreCase(header);
    }

    boolean checkSpeedSign(Location location, Minecart cart) {
	SignedBlock block = signedBlockManager.getSignedBlockAt(location);
	if (block == null || ! isSpeedSign(block)) {
	    return false;
	}

	try {
	    String speedLabel = block.readLabel();
	    Double speed = speedLabels.get(speedLabel);
	    if (speed == null) {
		speed = Double.valueOf(speedLabel);
	    }

	    if (speed == 1.0) {
		monitored.remove(cart);
	    } else {
		monitored.add(cart);
	    }

	    cart.setMaxSpeed(baseSpeed * speed);
	} catch (NumberFormatException e) {
	    return false;
	}

	double velocity = cart.getVelocity().length();
	if (cart.getMaxSpeed() < velocity) {
	    cart.setVelocity(cart.getVelocity().clone().multiply(cart.getMaxSpeed() / velocity));
	}

	return true;
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
	if (! (event.getVehicle() instanceof Minecart)) {
	    return;
	}
	
	Minecart cart = (Minecart)event.getVehicle();
	
	org.bukkit.util.Vector velocity = cart.getVelocity();
	double velocityLimit = cart.getMaxSpeed() * 16.0;
	double velocityLength = velocity.length();
	if (velocityLimit < velocityLength) {
	    cart.setVelocity(velocity.clone().multiply(velocityLimit / velocityLength));
	}
	
	Location from = event.getFrom();
	Location to = event.getTo();

	int diffX = to.getBlockX() - from.getBlockX();
	int diffY = to.getBlockY() - from.getBlockY();
	int diffZ = to.getBlockZ() - from.getBlockZ();

	if ((diffX == 0 && diffY == 0 && diffZ == 0) || from.getWorld() != to.getWorld()) {
	    return;
	}

	Location cursor = new Location(to.getWorld(), to.getBlockX(), to.getBlockY() - 1, to.getBlockZ());

	if (diffX == 0 && diffY == 0) {
	    while (diffZ != 0 && ! checkSpeedSign(cursor, cart)) {
		if (0 < diffZ) {
		    cursor.add(0, 0, -1);
		    --diffZ;
		} else {
		    cursor.add(0, 0, 1);
		    ++diffZ;
		}
	    }
	} else if (diffY == 0 && diffZ == 0) {
	    while (diffX != 0 && ! checkSpeedSign(cursor, cart)) {
		if (0 < diffX) {
		    cursor.add(-1, 0, 0);
		    --diffX;
		} else {
		    cursor.add(1, 0, 0);
		    ++diffX;
		}
	    }
	} else {
	    checkSpeedSign(cursor, cart);
	}
    }

    @Override
    public boolean mayCreate(Player player, SignedBlock block) {
	if (! isSpeedSign(block)) {
	    return true;
	}
	
	String label = block.readLabel();

	return player.hasPermission("kularail.maxspeed.create." + (speedLabels.containsKey(label) ? label : "any"));
    }

    @Override
    public boolean onCreate(SignedBlock block) {
	if (! isSpeedSign(block)) {
	    return false;
	}

	block.setHeaderColor(ChatColor.GREEN);

	return true;
    }

    @Override
    public void onDestroy(SignedBlock block) {
    }

    @Override
    public void onReset() {
    }
}
