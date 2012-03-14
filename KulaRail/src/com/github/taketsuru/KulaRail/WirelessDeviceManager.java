package com.github.taketsuru.KulaRail;

import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.material.Lever;
import org.bukkit.plugin.Plugin;

public class WirelessDeviceManager implements Listener, SignedBlockListener {

    public WirelessDeviceManager(SignedBlockManager signedBlockManager) {
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
    
    public boolean isTransmitter(SignedBlock block) {
	return block.readHeader().equalsIgnoreCase(transmitterHeader);
    }
    
    public boolean isReceiver(SignedBlock block) {
	return block.readHeader().equalsIgnoreCase(receiverHeader);
    }
    
    public void updateChannel(String channelName) {
	Vector<SignedBlock> channel = devices.get(channelName);
	if (channel == null) {
	    return;
	}

	for (SignedBlock receiver : channel) {
	    if (isReceiver(receiver)) {

		SignedBlock nearest = null;
		double minDistanceSquared = Double.MAX_VALUE;
		boolean powered = false;
		for (SignedBlock transmitter : channel) {
		    if (! isTransmitter(transmitter)) {
			continue;
		    }
		    double distanceSquared = transmitter.getBlock().getLocation().distanceSquared(receiver.getLocation());
		    if (distanceSquared < minDistanceSquared) {
			minDistanceSquared = distanceSquared;
			nearest = transmitter;
		    }
		}
		
		if (nearest != null) {
		    powered = nearest.getBlock().isBlockPowered();
		}

		BlockFace[] faces = { BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH, BlockFace.DOWN, BlockFace.UP };
		for (BlockFace face : faces) {
		    Block adjacent = receiver.getBlock().getRelative(face);
		    BlockState adjacentState = adjacent.getState();
		    
		    if (adjacentState.getType() != Material.LEVER) {
			continue;
		    }

		    Lever lever = (Lever)adjacentState.getData();
		    if (lever.isPowered() != powered) {
			net.minecraft.server.World world = ((org.bukkit.craftbukkit.CraftWorld)adjacent.getWorld()).getHandle();
			net.minecraft.server.Block.byId[adjacent.getType().getId()]
				.interact(world, adjacent.getX(), adjacent.getY(), adjacent.getZ(), null);
		    }
		}
	    }
	}
    }

    @EventHandler
    public void onBlockRedstone(BlockRedstoneEvent event) {
	if (event.getBlock().getType() != Material.SIGN_POST &&
		event.getBlock().getType() != Material.WALL_SIGN) {
	    return;
	}

	Block blockAttachedTo = SignedBlockManager.getAttachedBlock(event.getBlock());
	SignedBlock signedBlock = signedBlockManager.getSignedBlockAt(blockAttachedTo.getLocation());
	if (signedBlock == null) {
	    return;
	}
	
	if (! isTransmitter(signedBlock)) {
	    return;
	}

	String name = signedBlock.readLabel();
	Vector<SignedBlock> channel = devices.get(name);
	if (channel == null) {
	    return;
	}
	
	class Updater implements Runnable {

	    WirelessDeviceManager manager;
	    String channelName;

	    Updater(WirelessDeviceManager manager, String channelName) {
		this.manager = manager;
		this.channelName = channelName;
	    }
	    
	    public void run() {
		manager.updateChannel(channelName);
	    }
	}

	plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Updater(this, name), 1);
    }

    @Override
    public boolean onCreate(SignedBlock block) {
	if (! isReceiver(block) && ! isTransmitter(block)) {
	    return false;
	}

	String label = block.readLabel();

	Vector<SignedBlock> list = devices.get(label);
	if (list == null) {
	    list = new Vector<SignedBlock>();
	    devices.put(label, list);
	}
	list.add(block);

	updateColor(label);
	
	return true;
    }

    @Override
    public void onDestroy(SignedBlock block) {
	if (! isReceiver(block) && ! isTransmitter(block)) {
	    return;
	}
	
	String label = block.readLabel();

	Vector<SignedBlock> list = devices.get(label);
	if (list == null) {
	    return;
	}
	list.remove(block);
	if (list.isEmpty()) {
	    devices.remove(label);
	}

	updateColor(label);
    }

    @Override
    public void onReset() {
	devices.clear();
    }

    void updateColor(String channelName) {
	Vector<SignedBlock> channel = devices.get(channelName);
	if (channel == null) {
	    return;
	}

	int transmitterCount = 0;
	for (SignedBlock device : channel) {
	    if (isTransmitter(device)) {
		++transmitterCount;
	    }
	}
	int receiverCount = channel.size() - transmitterCount;
	
	ChatColor transmitterColor = null;
	ChatColor receiverColor = null;
	if (0 < transmitterCount) {
	    if (receiverCount == 0) {
		transmitterColor = ChatColor.RED;
	    } else {
		receiverColor = transmitterColor = ChatColor.GREEN;
	    }
	} else if (0 < receiverCount) {
	    receiverColor = ChatColor.RED;
	}

	for (SignedBlock device : channel) {
	    device.setHeaderColor(isTransmitter(device) ? transmitterColor : receiverColor);
	}
    }

    static final String receiverHeader = "[kr.receiver]";
    static final String transmitterHeader = "[kr.xmitter]";
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;
    SignedBlockManager signedBlockManager;
    HashMap<String, Vector<SignedBlock>> devices = new HashMap<String, Vector<SignedBlock>>();
}
