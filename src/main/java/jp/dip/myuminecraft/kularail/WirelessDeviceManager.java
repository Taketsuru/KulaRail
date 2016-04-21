package jp.dip.myuminecraft.kularail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.material.Lever;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.ManagedSign;
import jp.dip.myuminecraft.takecore.Messages;
import jp.dip.myuminecraft.takecore.SignTable;
import jp.dip.myuminecraft.takecore.SignTableListener;
import jp.dip.myuminecraft.takecore.TakeCore;

public class WirelessDeviceManager implements Listener, SignTableListener {

    private enum SignType {
        RECEIVER, TRANSMITTER
    }

    private final class WirelessSign extends ManagedSign {
        SignType           type;
        List<WirelessSign> channel;
        List<WirelessSign> receivers;
        WirelessSign       xmitter;
        boolean            isPowered;

        public WirelessSign(Location location, SignTableListener owner,
                SignType type, List<WirelessSign> channel, boolean isPowered) {
            super(location, owner);
            this.type = type;
            this.channel = channel;
            if (type == SignType.TRANSMITTER) {
                receivers = new ArrayList<WirelessSign>();
            }
            this.isPowered = isPowered;
        }
    }

    static final String             receiverHeader       = "[kr.receiver]";
    static final String             transmitterHeader    = "[kr.xmitter]";
    static final String             signCreationPermNode = "kularail.wireless";
    static final BlockFace[]        faces                = { BlockFace.EAST,
    BlockFace.NORTH, BlockFace.WEST, BlockFace.SOUTH, BlockFace.DOWN,
    BlockFace.UP };

    Plugin                          plugin;
    Logger                          logger;
    Messages                        messages;
    SignTable                       signTable;
    Map<String, List<WirelessSign>> devices;
    Map<Location, WirelessSign>     locationToXmitter;

    public WirelessDeviceManager(Plugin plugin, Logger logger,
            Messages messages) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        TakeCore takeCore = (TakeCore) pluginManager.getPlugin("TakeCore");
        signTable = takeCore.getSignTable();
        devices = new HashMap<String, List<WirelessSign>>();
        locationToXmitter = new HashMap<Location, WirelessSign>();

        signTable.addListener(this);
        pluginManager.registerEvents(this, plugin);
    }

    public void onDisable() {
        signTable.removeListener(this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        final Location location = event.getBlock().getLocation();
        final WirelessSign xmitter = locationToXmitter.get(location);
        if (xmitter == null) {
            return;
        }

        boolean powered = xmitter.getAttachedBlock().isBlockPowered();
        if (xmitter.isPowered == powered) {
            return;
        }

        xmitter.isPowered = powered;

        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin,
                new Runnable() {
                    @Override
                    public void run() {
                        if (locationToXmitter.get(location) == xmitter) {
                            transmit(xmitter);
                        }
                    }
                }, 1);
    }

    @Override
    public boolean mayCreate(Player player, Location location,
            String[] lines) {
        if (!isReceiverSign(lines) && !isTransmitterSign(lines)) {
            return true;
        }

        if (!player.hasPermission(signCreationPermNode)) {
            messages.send(player, "signCreationPermDenied");
            return false;
        }

        return true;
    }

    @Override
    public ManagedSign create(Location location, String[] lines) {
        boolean isReceiver = isReceiverSign(lines);
        boolean isTransmitter = !isReceiver && isTransmitterSign(lines);

        if (!isReceiver && !isTransmitter) {
            return null;
        }

        String name = SignUtil.getLabel(lines);
        List<WirelessSign> list = devices.get(name);
        boolean needToConnect = false;
        if (list == null) {
            list = new ArrayList<WirelessSign>();
            devices.put(name, list);
            SignUtil.setHeaderColor(lines, ChatColor.RED);
        } else {
            int xmitterCount = getTransmitterCount(list);
            int receiverCount = list.size() - xmitterCount;
            if (list.size() == 0 || (isTransmitter ? receiverCount == 0
                    : xmitterCount == 0)) {
                SignUtil.setHeaderColor(lines, ChatColor.RED);
            } else {
                if (isTransmitter ? xmitterCount == 0 : receiverCount == 0) {
                    setColor(list, ChatColor.GREEN);
                }
                SignUtil.setHeaderColor(lines, ChatColor.GREEN);
                needToConnect = true;
            }
        }

        WirelessSign sign = new WirelessSign(location, this,
                isReceiver ? SignType.RECEIVER : SignType.TRANSMITTER, list,
                ManagedSign.getAttachedBlock(location.getBlock())
                        .isBlockPowered());

        if (needToConnect) {
            switch (sign.type) {
            case TRANSMITTER:
                for (WirelessSign receiver : list) {
                    if (receiver.xmitter != null) {
                        double currentValue = SignUtil
                                .distanceSquared(receiver, receiver.xmitter);
                        double newValue = SignUtil.distanceSquared(receiver,
                                sign);
                        if (currentValue <= newValue) {
                            continue;
                        }
                        receiver.xmitter.receivers.remove(receiver);
                    }
                    receiver.xmitter = sign;
                    sign.receivers.add(receiver);
                }
                break;

            case RECEIVER:
                connectToNearestTransmitter(sign);
                break;

            default:
            }
        }

        list.add(sign);

        if (isTransmitter) {
            locationToXmitter.put(sign.getAttachedBlock().getLocation(), sign);
            transmit(sign);
        } else if (sign.xmitter != null) {
            transmit(sign.xmitter);
        }

        return sign;
    }

    @Override
    public void destroy(ManagedSign signBase) {
        WirelessSign sign = (WirelessSign) signBase;

        if (sign.type == SignType.TRANSMITTER) {
            locationToXmitter.remove(sign.getAttachedLocation());
        }

        if (sign.channel.size() == 1) {
            Sign state = (Sign) sign.getLocation().getBlock().getState();
            devices.remove(SignUtil.getLabel(state.getLines()));
            return;
        }

        sign.channel.remove(sign);

        int xmitterCount = getTransmitterCount(sign.channel);
        int receiverCount = sign.channel.size() - xmitterCount;
        if (sign.type == SignType.TRANSMITTER ? xmitterCount == 0
                : receiverCount == 0) {
            setColor(sign.channel, ChatColor.RED);
        }

        switch (sign.type) {
        case TRANSMITTER:
            while (!sign.receivers.isEmpty()) {
                WirelessSign receiver = sign.receivers.get(0);
                connectToNearestTransmitter(receiver);
                setReceiverPowered(receiver, receiver.xmitter != null
                        ? receiver.xmitter.isPowered : false);
            }
            break;

        case RECEIVER:
            if (sign.xmitter != null) {
                sign.xmitter.receivers.remove(sign);
            }
            break;

        default:
            throw new AssertionError();
        }
    }

    static boolean isTransmitterSign(String[] lines) {
        return ChatColor.stripColor(lines[0])
                .equalsIgnoreCase(transmitterHeader);
    }

    static boolean isReceiverSign(String[] lines) {
        return ChatColor.stripColor(lines[0]).equalsIgnoreCase(receiverHeader);
    }

    static int getTransmitterCount(List<WirelessSign> channel) {
        int xmitterCount = 0;
        for (WirelessSign sign : channel) {
            if (sign.type == SignType.TRANSMITTER) {
                ++xmitterCount;
            }
        }
        return xmitterCount;
    }

    void setColor(List<WirelessSign> channel, ChatColor color) {
        for (WirelessSign block : channel) {
            Sign state = (Sign) block.getLocation().getBlock().getState();
            SignUtil.setHeaderColor(state.getLines(), color);
            state.update();
        }
    }

    void transmit(WirelessSign xmitter) {
        List<WirelessSign> receivers = xmitter.receivers;
        if (receivers.isEmpty()) {
            return;
        }

        boolean powered = xmitter.isPowered;
        for (WirelessSign receiver : receivers) {
            setReceiverPowered(receiver, powered);
        }
    }

    void setReceiverPowered(WirelessSign receiver, boolean powered) {
        for (BlockFace face : faces) {
            Block adjacent = receiver.getAttachedBlock().getRelative(face);
            BlockState adjacentState = adjacent.getState();

            if (adjacentState.getType() != Material.LEVER) {
                continue;
            }

            Lever lever = (Lever) adjacentState.getData();
            if (lever.isPowered() != powered) {
                lever.setPowered(powered);
                adjacentState.setData(lever);
                adjacentState.update();
            }
        }
    }

    void connectToNearestTransmitter(WirelessSign receiver) {
        double minValue = Double.MAX_VALUE;
        WirelessSign nearest = null;
        for (WirelessSign sign : receiver.channel) {
            if (sign.type != SignType.TRANSMITTER) {
                continue;
            }
            double value = SignUtil.distanceSquared(receiver, sign);
            if (value < minValue) {
                minValue = value;
                nearest = sign;
            }
        }

        if (receiver.xmitter != null) {
            receiver.xmitter.receivers.remove(receiver);
        }
        receiver.xmitter = nearest;
        if (nearest != null) {
            nearest.receivers.add(receiver);
        }
    }
}
