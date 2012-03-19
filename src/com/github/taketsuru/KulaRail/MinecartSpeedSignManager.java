package com.github.taketsuru.KulaRail;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
	this.plugin = null;
	speedLabels.clear();
    }
 
    void loadDefaultSpeedLabels() {
	speedLabels.clear();
	speedLabels.put("normal", 1.0);
	speedLabels.put("rapid", 2.45);
	speedLabels.put("express", 4.9);
	speedLabels.put("super_express", 9.8);
    }

    void reloadSpeedLabels() throws FileNotFoundException, IOException {
	speedLabels.clear();
	File file = new File(plugin.getDataFolder(), speedLabelsFileName);
	FileReader reader = new FileReader(file);
	try {
	    Yaml yaml = new Yaml();
	    Map<?, ?> loadedObj = (Map<?, ?>)yaml.loadAs(reader, speedLabels.getClass());
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
	    yaml.dump(speedLabels);
	} finally {
	    writer.close();
	}
    }

    boolean doMaxSpeedCommand(CommandSender sender, Command cmd, String label, String[] args) {
	switch (args.length) {
	case 1:
	    for (String speedLabel : speedLabels.keySet()) {
		sender.sendMessage(String.format("%s - %g", speedLabel, speedLabels.get(speedLabel)));
	    }
	    break;
	    
	case 2:
	    speedLabels.remove(args[1]);
	    try {
		saveSpeedLabels();
	    } catch (Exception e) {
		sender.sendMessage("Failed to write to " + speedLabelsFileName);
	    }
	    break;
	    
	case 3:
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

	return player.hasPermission("kularail.maxspeed")
		&& player.hasPermission("kularail.maxspeed." + (speedLabels.containsKey(block.readLabel()) ? block.readLabel() : "any"));
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
