package com.github.taketsuru.KulaRail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

public class SignedBlockManager implements Listener {

	public static Block getAttachedBlock(Block signBlock) {
		org.bukkit.material.Sign data =
				new org.bukkit.material.Sign(signBlock.getType(),
											 signBlock.getData());
		return signBlock.getRelative(data.getAttachedFace());
	}

	public SignedBlock getSignedBlockAt(Location location) {
		return signedBlocks.get(location);
	}

	public void listen(SignedBlockListener listener) {
		listeners.add(listener);
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		Location blockLocation = event.getBlock().getLocation();
		SignedBlock block = getSignedBlockAt(blockLocation);
		if (block == null) {
			Material type = event.getBlock().getType();
			if (type == Material.SIGN_POST || type == Material.WALL_SIGN) {
				blockLocation =
					getAttachedBlock(event.getBlock()).getLocation();
				block = getSignedBlockAt(blockLocation);
				if (block != null
					&& ! block.getSignBlock().equals(event.getBlock())) {
					block = null;
				}
			}
		}
		if (block == null) {
			return;
		}

		onDestroy(block);
	}

	void onDestroy(SignedBlock block) {
		for (SignedBlockListener listener : listeners) {
			listener.onDestroy(block);
		}

		signedBlocks.remove(block.getLocation());

		Chunk chunk = block.getBlock().getChunk();
		Integer count = countInEachChunk.get(chunk);
		if (count == 1) {
			countInEachChunk.remove(chunk);
			chunk.unload(true, true);
		} else {
			countInEachChunk.put(chunk, count - 1);
		}
	}

	public void onDisable(Plugin plugin) {
		for (Iterator<Chunk> iter = countInEachChunk.keySet().iterator();
			 iter.hasNext(); ) {
			Chunk chunk = iter.next();
			iter.remove();
			chunk.unload(true, true);
		}

		Vector<Object> listData = new Vector<Object>();
		for (SignedBlock entry : signedBlocks.values()) {
			HashMap<String, Object> state = new HashMap<String, Object>();
			entry.saveState(state);
			listData.add(state);
		}
		plugin.getConfig().set("signed_blocks", listData);

		this.plugin = null;
	}

	public void onEnable(Plugin plugin) {
		this.plugin = plugin;

		signedBlocks.clear();

		for (SignedBlockListener listener : listeners) {
			listener.onReset();
		}

		if (plugin.getConfig().isList("signed_blocks")) {
			List<Map<?, ?>> listData =
				plugin.getConfig().getMapList("signed_blocks");
			for (Map<?, ?> listDataEntry : listData) {
				try {
					onCreate(new SignedBlock(plugin.getServer(),
											 listDataEntry));
				} catch (Exception e) {
					log.info("recovered");
					e.printStackTrace();
				}
			}
		}
		log.info(String.format("%d signed blocks are created", signedBlocks.size()));

		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	@EventHandler
	public void onSignChange(SignChangeEvent event) {
		if (event.isCancelled()) {
			return;
		}

		Block signBlock = event.getBlock();
		Block selfBlock = getAttachedBlock(signBlock);
		if (signedBlocks.get(selfBlock.getLocation()) != null) {
			return;
		}

		SignedBlock block =
				new SignedBlock(selfBlock.getLocation(),
								signBlock.getLocation(), event.getLines());
		Player player = event.getPlayer();
		if (! mayCreate(player, block)) {
			player.sendMessage("ERROR: Ç±ÇÃä≈î¬ÇçÏÇÈå†å¿Ç™Ç†ÇËÇ‹ÇπÇÒ");
			event.setCancelled(true);
			return;
		}

		if (onCreate(block)) {
			String[] signText = block.getSignText();
			for (int i = 0; i < signText.length; ++i) {
				event.setLine(i, signText[i]);
			}
			block.setIsChangingSign(false);
		}
	}

	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent event) {
		if (event.isCancelled()) {
			return;
		}

		final Chunk chunk = event.getChunk();
		if (countInEachChunk.containsKey(chunk) && 0 < countInEachChunk.get(chunk)) {
			event.setCancelled(true);
			plugin.getServer().getScheduler()
			.scheduleSyncDelayedTask(plugin, new Runnable() {
				public void run() {
					chunk.load(false);
				}
			}, 1);
		}
	}

	public void unlisten(SignedBlockListener listener) {
		listeners.remove(listener);
	}

	boolean mayCreate(Player player, SignedBlock block) {
		for (SignedBlockListener listener : listeners) {
			if (! listener.mayCreate(player, block)) {
				return false;
			}
		}
		return true;
	}

	boolean onCreate(SignedBlock block) {
		for (SignedBlockListener listener : listeners) {
			if (listener.onCreate(block)) {
				signedBlocks.put(block.getLocation(), block);
				Chunk chunk = block.getBlock().getChunk();
				if (! countInEachChunk.containsKey(chunk)) {
					countInEachChunk.put(chunk, 1);
				} else {
					countInEachChunk.put(chunk, countInEachChunk.get(chunk) + 1);
				}
				return true;
			}
		}
		return false;
	}

	Vector<SignedBlockListener> listeners = new Vector<SignedBlockListener>();
	Logger log = Logger.getLogger("Minecraft");
	Plugin plugin;
	HashMap<Location, SignedBlock> signedBlocks = new HashMap<Location, SignedBlock>();
	HashMap<Chunk, Integer> countInEachChunk = new HashMap<Chunk, Integer>(); 
}
