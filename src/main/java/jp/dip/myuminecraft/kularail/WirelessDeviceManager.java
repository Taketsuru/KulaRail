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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.material.Lever;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.ManagedSign;
import jp.dip.myuminecraft.takecore.Messages;
import jp.dip.myuminecraft.takecore.SignTable;
import jp.dip.myuminecraft.takecore.SignTableListener;
import jp.dip.myuminecraft.takecore.TakeCore;

public class WirelessDeviceManager implements Listener, SignTableListener {

    enum SignType {
        RECEIVER, TRANSMITTER
    }

    static class WirelessSign extends ManagedSign {
        SignType           type;
        String             channelName;
        List<WirelessSign> receivers;
        WirelessSign       transmitter;
        boolean            isPowered;

        public WirelessSign(SignTableListener owner, Location location,
                Location attachedLocation, SignType type, String channelName,
                boolean isPowered) {
            super(owner, location, attachedLocation);
            this.type = type;
            this.channelName = channelName;
            if (type == SignType.TRANSMITTER) {
                receivers = new ArrayList<WirelessSign>();
            }
            this.isPowered = isPowered;
        }
    }

    static class TransmitterBlock {
        List<WirelessSign> signs = new ArrayList<WirelessSign>();
        boolean            isPowered;
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
    Map<String, List<WirelessSign>> channels;
    Map<Location, TransmitterBlock> transmitterBlocks;
    Map<Location, WirelessSign>     receiverBlocks;

    public WirelessDeviceManager(Plugin plugin, Logger logger,
            Messages messages) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        TakeCore takeCore = (TakeCore) pluginManager.getPlugin("TakeCore");
        signTable = takeCore.getSignTable();
        channels = new HashMap<String, List<WirelessSign>>();
        transmitterBlocks = new HashMap<Location, TransmitterBlock>();
        receiverBlocks = new HashMap<Location, WirelessSign>();
        signTable.addListener(this);
        pluginManager.registerEvents(this, plugin);
    }

    public void onDisable() {
        signTable.removeListener(this);
        channels.clear();
        transmitterBlocks.clear();
        receiverBlocks.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        TransmitterBlock transmitterBlock = transmitterBlocks
                .get(block.getLocation());
        if (transmitterBlock == null) {
            return;
        }

        boolean powered = block.isBlockPowered();
        if (powered == transmitterBlock.isPowered) {
            return;
        }
        transmitterBlock.isPowered = powered;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (WirelessSign sign : transmitterBlock.signs) {
                    if (sign.isPowered != powered) {
                        sign.isPowered = powered;
                        transmit(sign);
                    }
                }
            }
        }.runTaskLater(plugin, 1);
    }

    @Override
    public boolean mayCreate(Player player, Location location,
            Location attachedLocation, String[] lines) {
        boolean isReceiver = isReceiverSign(lines);
        if (!isReceiver && !isTransmitterSign(lines)) {
            return true;
        }

        if (!player.hasPermission(signCreationPermNode)) {
            messages.send(player, "wirelessSignCreationPermDenied");
            return false;
        }

        if (isReceiver && receiverBlocks.containsKey(attachedLocation)) {
            messages.send(player, "multipleReceiverSigns");
            return false;
        }

        return true;
    }

    @Override
    public ManagedSign create(Player player, Location location,
            Location attachedLocation, String[] lines) {
        boolean isReceiver = isReceiverSign(lines);
        if (!isReceiver && !isTransmitterSign(lines)) {
            return null;
        }

        String channelName = SignUtil.getLabel(lines);
        List<WirelessSign> channel = channels.get(channelName);
        boolean needToConnect = false;
        if (channel == null) {
            channel = new ArrayList<WirelessSign>();
            channels.put(channelName, channel);
        } else {
            int transmitterCount = getTransmitterCount(channel);
            int receiverCount = channel.size() - transmitterCount;

            if (isReceiver ? receiverCount == 0 : transmitterCount == 0) {
                setColor(channel, ChatColor.GREEN);
            }

            needToConnect = isReceiver ? 0 < transmitterCount
                    : 0 < receiverCount;
        }

        SignUtil.setHeaderColor(lines,
                needToConnect ? ChatColor.GREEN : ChatColor.RED);

        boolean isPowered = attachedLocation.getBlock().isBlockPowered();
        WirelessSign sign = new WirelessSign(this, location, attachedLocation,
                isReceiver ? SignType.RECEIVER : SignType.TRANSMITTER,
                channelName, isPowered);

        if (needToConnect) {
            if (isReceiver) {
                connectToNearestTransmitter(sign, channel);

            } else {
                for (WirelessSign receiver : channel) {
                    if (receiver.transmitter != null) {
                        double currentValue = SignUtil.distanceSquared(
                                receiver, receiver.transmitter);
                        double newValue = SignUtil.distanceSquared(receiver,
                                sign);
                        if (currentValue <= newValue) {
                            continue;
                        }
                        receiver.transmitter.receivers.remove(receiver);
                    }
                    receiver.transmitter = sign;
                    sign.receivers.add(receiver);
                }
            }
        }

        channel.add(sign);

        if (isReceiver) {
            receiverBlocks.put(attachedLocation, sign);
            if (sign.transmitter != null) {
                setReceiverPowered(sign, sign.transmitter.isPowered);
            }

        } else {
            TransmitterBlock transmitters = transmitterBlocks
                    .get(attachedLocation);
            if (transmitters == null) {
                transmitters = new TransmitterBlock();
                transmitters.isPowered = isPowered;
                transmitterBlocks.put(attachedLocation, transmitters);
            }
            transmitters.signs.add(sign);
            transmit(sign);
        }

        return sign;
    }

    @Override
    public void destroy(Player player, ManagedSign signBase) {
        WirelessSign sign = (WirelessSign) signBase;

        switch (sign.type) {
        case TRANSMITTER:
            Location attachedLocation = sign.getAttachedLocation();
            TransmitterBlock transmitters = transmitterBlocks
                    .get(attachedLocation);
            transmitters.signs.remove(sign);
            if (transmitters.signs.isEmpty()) {
                transmitterBlocks.remove(attachedLocation);
            }
            break;

        case RECEIVER:
            receiverBlocks.remove(sign.getAttachedLocation());
            break;

        default:
            throw new AssertionError();
        }

        String channelName = sign.channelName;
        List<WirelessSign> channel = channels.get(channelName);
        if (channel.size() == 1) {
            channels.remove(channelName);
            return;
        }

        channel.remove(sign);

        int transmitterCount = getTransmitterCount(channel);
        int receiverCount = channel.size() - transmitterCount;
        if (sign.type == SignType.TRANSMITTER ? transmitterCount == 0
                : receiverCount == 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    List<WirelessSign> channel = channels.get(channelName);
                    if (channel != null) {
                        setColor(channel, ChatColor.RED);
                    }
                }
            }.runTask(plugin);
        }

        switch (sign.type) {
        case TRANSMITTER:
            List<WirelessSign> receivers = sign.receivers;
            List<WirelessSign> worklist = new ArrayList<WirelessSign>(
                    receivers);

            while (!receivers.isEmpty()) {
                connectToNearestTransmitter(
                        receivers.get(receivers.size() - 1), channel);
            }

            for (WirelessSign receiver : worklist) {
                WirelessSign transmitter = receiver.transmitter;
                if (transmitter != null) {
                    setReceiverPowered(receiver, transmitter.isPowered);
                }
            }
            break;

        case RECEIVER:
            if (sign.transmitter != null) {
                sign.transmitter.receivers.remove(sign);
                sign.transmitter = null;
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
        int result = 0;
        for (WirelessSign sign : channel) {
            if (sign.type == SignType.TRANSMITTER) {
                ++result;
            }
        }
        return result;
    }

    void setColor(List<WirelessSign> channel, ChatColor color) {
        for (WirelessSign sign : new ArrayList<WirelessSign>(channel)) {
            BlockState state = sign.getLocation().getBlock().getState();
            if (state instanceof Sign) {
                SignUtil.setHeaderColor(((Sign) state).getLines(), color);
                state.update(false, false);
            }
        }
    }

    void transmit(WirelessSign transmitter) {
        List<WirelessSign> receivers = transmitter.receivers;
        if (receivers.isEmpty()) {
            return;
        }

        boolean powered = transmitter.isPowered;
        for (WirelessSign receiver : receivers) {
            setReceiverPowered(receiver, powered);
        }
    }

    void setReceiverPowered(WirelessSign receiver, boolean powered) {
        Block attachedBlock = receiver.getAttachedBlock();
        for (BlockFace face : faces) {
            Block adjacent = attachedBlock.getRelative(face);
            if (adjacent.getType() == Material.LEVER) {
                BlockState adjacentState = adjacent.getState();
                Lever lever = (Lever) adjacentState.getData();
                if (lever.isPowered() != powered) {
                    lever.setPowered(powered);
                    adjacentState.setData(lever);
                    adjacentState.update();
                }
            }
        }
    }

    void connectToNearestTransmitter(WirelessSign receiver,
            List<WirelessSign> channel) {
        double minValue = Double.MAX_VALUE;
        WirelessSign nearest = null;
        for (WirelessSign sign : channel) {
            if (sign.type != SignType.TRANSMITTER) {
                continue;
            }
            double value = SignUtil.distanceSquared(receiver, sign);
            if (value < minValue) {
                minValue = value;
                nearest = sign;
            }
        }

        if (receiver.transmitter != null) {
            receiver.transmitter.receivers.remove(receiver);
        }
        receiver.transmitter = nearest;
        if (nearest != null) {
            nearest.receivers.add(receiver);
        }
    }
}
