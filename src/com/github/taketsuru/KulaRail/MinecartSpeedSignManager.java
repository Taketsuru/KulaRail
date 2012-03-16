package com.github.taketsuru.KulaRail;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;

public class MinecartSpeedSignManager implements SignedBlockListener, Listener {

    static final String header = "[kr.maxspeed]";
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;
    SignedBlockManager signedBlockManager;
    Map<Location, SignedBlock> speedSigns = new HashMap<Location, SignedBlock>();
    int maxBlocksPerTick = 5;
    double baseSpeed = 0.4;

    public MinecartSpeedSignManager(SignedBlockManager signedBlockManager) {
	this.signedBlockManager = signedBlockManager;
	signedBlockManager.listen(this);
    }

    public void onEnable(Plugin plugin) {
	this.plugin = plugin;
	plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable(Plugin plugin) {
	this.plugin = null;
    }
    
    public boolean isSpeedSign(SignedBlock block) {
	return block.readHeader().equalsIgnoreCase(header);
    }

    boolean checkSpeedSign(Location location, Minecart cart) {
	SignedBlock block = speedSigns.get(location);
	if (block == null || ! isSpeedSign(block)) {
	    return false;
	}

	try {
	    cart.setMaxSpeed(baseSpeed * Double.valueOf(block.readLabel()));
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
	if (from.getWorld() != to.getWorld()
		|| from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
	    return;
	}

	int diffX = to.getBlockX() - from.getBlockX();
	int diffY = to.getBlockY() - from.getBlockY();
	int diffZ = to.getBlockZ() - from.getBlockZ();

	Location blockLocation = new Location(to.getWorld(), to.getBlockX(), to.getBlockY() - 1, to.getBlockZ());

	if (diffX == 0 && diffY == 0 && (-maxBlocksPerTick <= diffZ && diffZ <= maxBlocksPerTick)) {
	    while (diffZ != 0 && ! checkSpeedSign(blockLocation, cart)) {
		if (0 < diffZ) {
		    blockLocation.add(0, 0, -1);
		    --diffZ;
		} else {
		    blockLocation.add(0, 0, 1);
		    ++diffZ;
		}
	    }
	} else if ((-maxBlocksPerTick <= diffX && diffX <= maxBlocksPerTick) && diffY == 0 && diffZ == 0) {
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
	    if (diffX == 0 && diffZ == 0 && diffY < 0) {
		if (cart.getLocation().getY() <= 0) {
		    log.info(String.format("falling %s@%s is removed", cart.toString(), cart.getLocation().toString()));
		    cart.remove();
		}
	    }
	    checkSpeedSign(blockLocation, cart);
	}
    }

    @Override
    public boolean mayCreate(Player player, SignedBlock block) {
	return ! isSpeedSign(block) || player.hasPermission("kularail.maxspeed");
    }

    @Override
    public boolean onCreate(SignedBlock block) {
	if (! isSpeedSign(block)) {
	    return false;
	}

	speedSigns.put(block.getLocation(), block);

	return true;
    }

    @Override
    public void onDestroy(SignedBlock block) {
	if (! isSpeedSign(block)) {
	    return;
	}
	
	speedSigns.remove(block.getLocation());
    }

    @Override
    public void onReset() {
	speedSigns.clear();
    }
}
