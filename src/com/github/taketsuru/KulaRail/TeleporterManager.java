package com.github.taketsuru.KulaRail;

import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Logger;

import net.minecraft.server.WorldServer;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.material.Rails;
import org.bukkit.plugin.Plugin;

public class TeleporterManager implements Listener, SignedBlockListener {

  public static boolean isEntryBlock(SignedBlock block) {
    return block.readHeader().equalsIgnoreCase(entryHeader);
  }

  public static boolean isExitBlock(SignedBlock block) {
    return block.readHeader().equalsIgnoreCase(exitHeader);
  }

  public TeleporterManager(SignedBlockManager signedBlockManager) {
    this.signedBlockManager = signedBlockManager;
    signedBlockManager.listen(this);
  }

  public void onEnable(Plugin plugin) {
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler
  public void onVehicleBlockCollision(VehicleBlockCollisionEvent event) {
    Block blockCollidedWith = event.getBlock();
    SignedBlock entry = signedBlockManager
      .getSignedBlockAt(blockCollidedWith.getLocation());
    if (entry == null || !isEntryBlock(entry)) {
      return;
    }

    String channelName = entry.readLabel();
    Vector<SignedBlock> channel = channels.get(channelName);
    if (channel == null) {
      return;
    }

    Location exitLocation = null;
    double minDistanceSquared = Double.MAX_VALUE;
    for (SignedBlock block : channel) {
      if (!isExitBlock(block)) {
        continue;
      }
      double distanceSquared = 
        block.getLocation().getWorld() == entry.getLocation().getWorld()
        ? block.getLocation().distanceSquared(entry.getLocation())
        : Double.MAX_VALUE / 2;
      if (distanceSquared < minDistanceSquared) {
        minDistanceSquared = distanceSquared;
        exitLocation = block.getLocation();
      }
    }
    if (exitLocation == null) {
      return;
    }

    Block exitBlock = exitLocation.getBlock();

    BlockFace[] faces = { BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST,
                          BlockFace.SOUTH };
    for (int dir = 0; dir < faces.length; ++dir) {
      BlockFace face = faces[dir];
      Block adjacent = exitBlock.getRelative(face);
      BlockState state = adjacent.getState();
      if (!(state.getData() instanceof org.bukkit.material.Rails)) {
        continue;
      }

      Rails rails = (org.bukkit.material.Rails) state.getData();
      boolean attached = false;
      switch (rails.getDirection()) {
      case EAST:
      case WEST:
        attached = face.equals(BlockFace.EAST)
          || face.equals(BlockFace.WEST);
        break;

      case NORTH:
      case SOUTH:
        attached = face.equals(BlockFace.SOUTH)
          || face.equals(BlockFace.NORTH);
        break;

      default:
        break;
      }

      if (attached) {
        Vehicle vehicle = event.getVehicle();
        vehicle.eject();
        Location newLocation =
          exitLocation.clone().add(face.getModX() + 0.5, face.getModY() + 0.35,
                                   face.getModZ() + 0.5);
        newLocation.setYaw(dir * 90.0f);
        if (newLocation.getWorld() == vehicle.getWorld()) {
          vehicle.teleport(newLocation);
        } else {
          WorldServer sourceWorld =
            ((CraftWorld)vehicle.getWorld()).getHandle();
          WorldServer destWorld =
            ((CraftWorld)newLocation.getWorld()).getHandle();
          net.minecraft.server.Entity vehicleHandle =
            ((CraftEntity)vehicle).getHandle();
          sourceWorld.tracker.untrackEntity(vehicleHandle);
          vehicleHandle.world.removeEntity(vehicleHandle);
          vehicleHandle.dead = false;
          vehicleHandle.world = destWorld;
          vehicleHandle.setLocation(newLocation.getX(),
                                    newLocation.getY(),
                                    newLocation.getZ(),
                                    newLocation.getYaw(),
                                    newLocation.getPitch());
          destWorld.addEntity(vehicleHandle);
          destWorld.tracker.track(vehicleHandle);
        }
        vehicle.setVelocity(new org.bukkit.util.Vector());
        break;
      }
    }
  }

  @Override
  public void onReset() {
    channels.clear();
  }

  @Override
  public boolean mayCreate(Player player, SignedBlock block) {
    if (!isEntryBlock(block) && !isExitBlock(block)) {
      return true;
    }

    return player.hasPermission(isEntryBlock(block)
				? "kularail.entry"
				: "kularail.exit");
  }

  @Override
  public boolean onCreate(SignedBlock block) {
    if (!isEntryBlock(block) && !isExitBlock(block)) {
      return false;
    }
    String name = block.readLabel();
    Vector<SignedBlock> channel = channels.get(name);
    if (channel == null) {
      channel = new Vector<SignedBlock>();
      channels.put(name, channel);
    }
    channel.add(block);
    updateColor(channel);

    return true;
  }

  @Override
  public void onDestroy(SignedBlock block) {
    if (!isEntryBlock(block) && !isExitBlock(block)) {
      return;
    }
    String name = block.readLabel();
    Vector<SignedBlock> channel = channels.get(name);
    channel.remove(block);
    if (channel.isEmpty()) {
      channels.remove(name);
    }
    updateColor(channel);
  }

  void updateColor(Vector<SignedBlock> channel) {
    int entryCount = 0;
    for (SignedBlock block : channel) {
      if (isEntryBlock(block)) {
        ++entryCount;
      }
    }
    int exitCount = channel.size() - entryCount;

    ChatColor exitColor = 0 < entryCount ? ChatColor.GREEN : ChatColor.RED;
    ChatColor entryColor = 0 < exitCount ? ChatColor.GREEN : ChatColor.RED;
    for (SignedBlock block : channel) {
      block.setHeaderColor(isEntryBlock(block) ? entryColor : exitColor);
    }
  }

  static final String entryHeader = "[kr.entry]";
  static final String exitHeader = "[kr.exit]";
  Logger log = Logger.getLogger("Minecraft");
  SignedBlockManager signedBlockManager;
  HashMap<String, Vector<SignedBlock>> channels =
    new HashMap<String, Vector<SignedBlock>>();
}
