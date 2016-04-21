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
import org.bukkit.material.Rails;
import org.bukkit.plugin.Plugin;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.ManagedSign;
import jp.dip.myuminecraft.takecore.Messages;
import jp.dip.myuminecraft.takecore.SignTable;
import jp.dip.myuminecraft.takecore.SignTableListener;
import jp.dip.myuminecraft.takecore.TakeCore;

public class TeleporterManager implements Listener, SignTableListener {

    private enum SignType {
        ENTRY, EXIT
    };

    private class TeleporterSign extends ManagedSign {
        SignType             type;
        List<TeleporterSign> channel;

        public TeleporterSign(Location location, SignTableListener owner,
                SignType type, List<TeleporterSign> channel) {
            super(location, owner);
            this.type = type;
            this.channel = channel;
        }
    }

    static final String               entryHeader = "[kr.entry]";
    static final String               exitHeader  = "[kr.exit]";
    static final BlockFace[]          faces       = { BlockFace.WEST,
    BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH };

    Plugin                            plugin;
    Logger                            logger;
    Messages                          messages;
    SignTable                         signTable;
    Map<String, List<TeleporterSign>> channels;
    Map<Location, TeleporterSign>     blocks;

    public TeleporterManager(Plugin plugin, Logger logger, Messages messages) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        TakeCore takeCore = (TakeCore) plugin.getServer().getPluginManager()
                .getPlugin("TakeCore");
        signTable = takeCore.getSignTable();
        channels = new HashMap<String, List<TeleporterSign>>();
        blocks = new HashMap<Location, TeleporterSign>();

        signTable.addListener(this);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable() {
        signTable.removeListener(this);

        blocks = null;
        channels = null;
        signTable = null;
        messages = null;
        logger = null;
        plugin = null;
    }

    @EventHandler
    public void onVehicleBlockCollision(VehicleBlockCollisionEvent event) {
        TeleporterSign sign = blocks.get(event.getBlock().getLocation());
        if (sign == null) {
            return;
        }

        logger.info("sign.type %s", sign.type);
        if (sign.type != SignType.ENTRY) {
            return;
        }

        Location entryLocation = sign.getLocation();
        List<TeleporterSign> channel = sign.channel;
        World entryWorld = entryLocation.getWorld();
        Location exitLocation = null;
        double minDistanceSquared = Double.MAX_VALUE;
        for (TeleporterSign exitSign : channel) {
            if (exitSign.type != SignType.EXIT) {
                continue;
            }

            Location location = exitSign.getAttachedLocation();
            double distanceSquared = location.getWorld() == entryWorld
                    ? location.distanceSquared(entryLocation)
                    : Double.MAX_VALUE / 2;
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                exitLocation = location;
            }
        }

        logger.info("exitLocation %s", exitLocation);
        if (exitLocation == null) {
            return;
        }

        Block exitBlock = exitLocation.getBlock();

        for (int dir = 0; dir < faces.length; ++dir) {
            BlockFace face = faces[dir];
            Block adjacent = exitBlock.getRelative(face);
            BlockState state = adjacent.getState();
            if (!(state.getData() instanceof Rails)) {
                continue;
            }

            Rails rails = (Rails) state.getData();
            switch (rails.getDirection()) {
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
            logger.info("newLocation %s", newLocation);

            break;
        }
        logger.info("leave");
    }

    @Override
    public boolean mayCreate(Player player, Location location,
            String[] lines) {
        if (!isEntryBlock(lines) && !isExitBlock(lines)) {
            return true;
        }

        if (!player.hasPermission("kularail.teleport")) {
            messages.send(player, "createEntryExitSignPermDenied");
            return false;
        }

        return true;
    }

    @Override
    public ManagedSign create(Location location, String[] lines) {
        boolean isEntry = isEntryBlock(lines);
        if (!isEntry && !isExitBlock(lines)) {
            return null;
        }

        String name = SignUtil.getLabel(lines);
        List<TeleporterSign> channel = channels.get(name);
        if (channel == null) {
            channel = new ArrayList<TeleporterSign>();
            channels.put(name, channel);
            SignUtil.setHeaderColor(lines, ChatColor.RED);
        } else {
            int channelSize = channel.size();
            if (channelSize == 0 || (isEntry ? getExitCount(channel) == 0
                    : getExitCount(channel) == channelSize)) {
                SignUtil.setHeaderColor(lines, ChatColor.RED);
            } else {
                setColor(channel, ChatColor.GREEN);
                SignUtil.setHeaderColor(lines, ChatColor.GREEN);
            }
        }

        TeleporterSign result = new TeleporterSign(location, this,
                isEntry ? SignType.ENTRY : SignType.EXIT, channel);
        channel.add(result);
        blocks.put(result.getAttachedLocation(), result);

        return result;
    }

    @Override
    public void destroy(ManagedSign signBase) {
        TeleporterSign sign = (TeleporterSign) signBase;
        logger.info("destroy %s", sign);

        Location attachedLocation = sign.getAttachedLocation();
        if (blocks.get(attachedLocation) == sign) {
            blocks.remove(attachedLocation);
        }

        List<TeleporterSign> channel = sign.channel;

        if (channel.size() == 1) {
            Location location = sign.getLocation();
            Sign state = (Sign) location.getBlock().getState();
            String[] lines = state.getLines();
            channels.remove(SignUtil.getLabel(lines));
            logger.info("remove channel %s", SignUtil.getLabel(lines));
            return;
        }

        channel.remove(sign);

        int channelSize = channel.size();
        int exitCount = getExitCount(channel);
        if (sign.type == SignType.ENTRY ? channelSize == exitCount
                : exitCount == 0) {
            setColor(channel, ChatColor.RED);
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
        for (TeleporterSign block : channel) {
            Sign state = (Sign) block.getLocation().getBlock().getState();
            SignUtil.setHeaderColor(state.getLines(), color);
            state.update();
        }
    }

}
