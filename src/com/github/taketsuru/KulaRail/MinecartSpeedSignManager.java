package com.github.taketsuru.KulaRail;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;

public class MinecartSpeedSignManager implements SignedBlockListener, Listener {

    static final String header = "[kr.maxspeed]";
    static final String speedConfigurationFileName = "speedLabels.yml";
    FileConfiguration speedConfiguration = null;
    File speedConfigurationFile = null;
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;
    SignedBlockManager signedBlockManager;
    double baseSpeed = 0.4;

    public MinecartSpeedSignManager(SignedBlockManager signedBlockManager) {
	this.signedBlockManager = signedBlockManager;
	signedBlockManager.listen(this);
    }

    public void onEnable(Plugin plugin) {
	this.plugin = plugin;
	reloadSpeedConfiguration();
	plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable(Plugin plugin) {
	this.plugin = null;
	speedConfiguration = null;
	speedConfigurationFile = null;
    }
    
    public boolean isSpeedSign(SignedBlock block) {
	return block.readHeader().equalsIgnoreCase(header);
    }

    boolean checkSpeedSign(Location location, Minecart cart) {
	SignedBlock block = signedBlockManager.getSignedBlockAt(location);
	if (block == null || ! isSpeedSign(block) || speedConfiguration == null) {
	    return false;
	}

	try {
	    String speedLabel = block.readLabel();
	    Double speedObj = (Double)speedConfiguration.get(speedLabel);
	    double multiplier = (speedObj == null) ? Double.valueOf(speedLabel) : speedObj.doubleValue();
	    cart.setMaxSpeed(baseSpeed * multiplier);
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

	if (from.getWorld() != to.getWorld() || (diffX == 0 && diffY == 0 && diffZ == 0)) {
	    return;
	}

	Location blockLocation = new Location(to.getWorld(), to.getBlockX(), to.getBlockY() - 1, to.getBlockZ());

	if (diffX == 0 && diffY == 0) {
	    while (diffZ != 0 && ! checkSpeedSign(blockLocation, cart)) {
		if (0 < diffZ) {
		    blockLocation.add(0, 0, -1);
		    --diffZ;
		} else {
		    blockLocation.add(0, 0, 1);
		    ++diffZ;
		}
	    }
	} else if (diffY == 0 && diffZ == 0) {
	    while (diffX != 0 && ! checkSpeedSign(blockLocation, cart)) {
		if (0 < diffX) {
		    blockLocation.add(-1, 0, 0);
		    --diffX;
		} else {
		    blockLocation.add(1, 0, 0);
		    ++diffX;
		}
	    }
	} else {
	    checkSpeedSign(blockLocation, cart);
	}
    }

    @Override
    public boolean mayCreate(Player player, SignedBlock block) {
	if (! isSpeedSign(block)) {
	    return true;
	}

	return player.hasPermission("kularail.maxspeed")
		&& player.hasPermission("kularail.maxspeed." + (getSpeedConfiguration().contains(block.readLabel()) ? block.readLabel() : "any"));
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
    
    void reloadSpeedConfiguration() {
	if (speedConfigurationFile == null) {
	    speedConfigurationFile = new File(plugin.getDataFolder(), speedConfigurationFileName);
	}
	speedConfiguration = YamlConfiguration.loadConfiguration(speedConfigurationFile);
	InputStream defaultConfigResource = plugin.getResource(speedConfigurationFileName);
	if (defaultConfigResource != null) {
	    YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigResource);
	    speedConfiguration.setDefaults(defaultConfig);
	}
    }
    
    FileConfiguration getSpeedConfiguration() {
	if (speedConfiguration == null) {
	    reloadSpeedConfiguration();
	}
	return speedConfiguration;
    }
    
    void saveSpeedConfiguration() throws java.io.IOException {
	if (speedConfiguration == null || speedConfigurationFile == null) {
	    return;
	}
	speedConfiguration.save(speedConfigurationFile);
    }
}
