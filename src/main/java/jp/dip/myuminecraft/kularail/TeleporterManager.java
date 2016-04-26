package jp.dip.myuminecraft.kularail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleBlockCollisionEvent;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Rails;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.ManagedSign;
import jp.dip.myuminecraft.takecore.Messages;
import jp.dip.myuminecraft.takecore.SignTable;
import jp.dip.myuminecraft.takecore.SignTableListener;
import jp.dip.myuminecraft.takecore.TakeCore;

public class TeleporterManager implements Listener, SignTableListener {

    enum SignType {
        ENTRY, EXIT
    };

    static class TeleporterSign extends ManagedSign {
        SignType type;
        String   channelName;

        public TeleporterSign(SignTableListener owner, Location location,
                Location attachedLocation, SignType type, String channelName) {
            super(owner, location, attachedLocation);
            this.type = type;
            this.channelName = channelName;
        }
    }

    static final String               entryHeader = "[kr.entry]";
    static final String               exitHeader  = "[kr.exit]";
    static final BlockFace[]          faces       = { BlockFace.WEST,
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH };

    JavaPlugin                        plugin;
    Logger                            logger;
    Messages                          messages;
    SignTable                         signTable;
    Map<String, List<TeleporterSign>> channels;
    Map<Location, TeleporterSign>     blocks;

    public TeleporterManager(JavaPlugin plugin, Logger logger,
            Messages messages) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        TakeCore takeCore = (TakeCore) plugin.getServer().getPluginManager()
                .getPlugin("TakeCore");
        signTable = takeCore.getSignTable();
        channels = new HashMap<String, List<TeleporterSign>>();
        blocks = new HashMap<Location, TeleporterSign>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        signTable.addListener(this);
    }

    public void onDisable() {
        signTable.removeListener(this);
        channels.clear();
        blocks.clear();
    }

    @EventHandler
    public void onVehicleBlockCollision(VehicleBlockCollisionEvent event) {
        Location entryLocation = event.getBlock().getLocation();
        TeleporterSign sign = blocks.get(entryLocation);
        if (sign == null) {
            return;
        }

        if (sign.type != SignType.ENTRY) {
            return;
        }

        List<TeleporterSign> channel = channels.get(sign.channelName);
        World entryWorld = entryLocation.getWorld();
        Location exitLocation = null;
        double minDistanceSquared = Double.MAX_VALUE;
        for (TeleporterSign exitSign : channel) {
            if (exitSign.type != SignType.EXIT) {
                continue;
            }

            Location location = exitSign.getAttachedLocation();
            double distanceSquared = location.getWorld() == entryWorld
                    ? location.distanceSquared(location)
                    : Double.MAX_VALUE / 2;
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                exitLocation = location;
            }
        }

        if (exitLocation == null) {
            return;
        }

        Block exitBlock = exitLocation.getBlock();

        for (int dir = 0; dir < faces.length; ++dir) {
            BlockFace face = faces[dir];
            Block adjacent = exitBlock.getRelative(face);
            MaterialData data = adjacent.getState().getData();
            if (!(data instanceof Rails)) {
                continue;
            }

            switch (((Rails) data).getDirection()) {
            case EAST:
            case WEST:
                if (face != BlockFace.EAST && face != BlockFace.WEST) {
                    continue;
                }
                break;

            case NORTH:
            case SOUTH:
                if (face != BlockFace.SOUTH && face != BlockFace.NORTH) {
                    continue;
                }
                break;

            default:
                continue;
            }

            Vehicle vehicle = event.getVehicle();
            vehicle.eject();
            Location newLocation = exitLocation.clone().add(
                    face.getModX() + 0.5, face.getModY() + 0.35,
                    face.getModZ() + 0.5);
            newLocation.setYaw(dir * 90.0f);
            vehicle.teleport(newLocation);
            vehicle.setVelocity(new org.bukkit.util.Vector());

            break;
        }
    }

    @Override
    public boolean mayCreate(Player player, Location location,
            Location attachedLocation, String[] lines) {
        if (!isEntryBlock(lines) && !isExitBlock(lines)) {
            return true;
        }

        if (!player.hasPermission("kularail.teleport")) {
            messages.send(player, "createEntryExitSignPermDenied");
            return false;
        }

        if (blocks.containsKey(attachedLocation)) {
            messages.send(player, "multipleTeleporterSigns");
            return false;
        }

        return true;
    }

    @Override
    public ManagedSign create(Location location, Location attachedLocation,
            String[] lines) {
        boolean isEntry = isEntryBlock(lines);
        if (!isEntry && !isExitBlock(lines)) {
            return null;
        }

        boolean hasPeer = false;
        String channelName = SignUtil.getLabel(lines);
        List<TeleporterSign> channel = channels.get(channelName);
        if (channel == null) {
            channel = new ArrayList<TeleporterSign>();
            channels.put(channelName, channel);
        } else {
            int exitCount = getExitCount(channel);
            int entryCount = channel.size() - exitCount;
            if (isEntry ? 0 < exitCount : 0 < entryCount) {
                hasPeer = true;
                if (isEntry ? entryCount == 0 : exitCount == 0) {
                    setColor(channel, ChatColor.GREEN);
                }
            }
        }

        SignUtil.setHeaderColor(lines,
                hasPeer ? ChatColor.GREEN : ChatColor.RED);

        TeleporterSign result = new TeleporterSign(this, location,
                attachedLocation, isEntry ? SignType.ENTRY : SignType.EXIT,
                channelName);

        channel.add(result);
        blocks.put(attachedLocation, result);

        return result;
    }

    @Override
    public void destroy(ManagedSign signBase) {
        TeleporterSign sign = (TeleporterSign) signBase;

        blocks.remove(sign.getAttachedLocation());

        String channelName = sign.channelName;
        List<TeleporterSign> channel = channels.get(channelName);

        if (channel.size() == 1) {
            channels.remove(channelName);
            return;
        }

        channel.remove(sign);

        int exitCount = getExitCount(channel);
        int entryCount = channel.size() - exitCount;
        if (sign.type == SignType.ENTRY ? entryCount == 0
                : exitCount == 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    List<TeleporterSign> channel = channels.get(channelName);
                    if (channel != null) {
                        setColor(channel, ChatColor.RED);
                    }
                }
            }.runTask(plugin);
        }
    }

    static boolean isEntryBlock(String[] lines) {
        return ChatColor.stripColor(lines[0]).equalsIgnoreCase(entryHeader);
    }

    static boolean isExitBlock(String[] lines) {
        return ChatColor.stripColor(lines[0]).equalsIgnoreCase(exitHeader);
    }

    static int getExitCount(List<TeleporterSign> channel) {
        int exitCount = 0;
        for (TeleporterSign block : channel) {
            if (block.type == SignType.EXIT) {
                ++exitCount;
            }
        }
        return exitCount;
    }

    void setColor(List<TeleporterSign> channel, ChatColor color) {
        for (TeleporterSign sign : new ArrayList<TeleporterSign>(channel)) {
            BlockState state = sign.getLocation().getBlock().getState();
            if (state instanceof Sign) {
                SignUtil.setHeaderColor(((Sign) state).getLines(), color);
                state.update(false, false);
            }
        }
    }
}
