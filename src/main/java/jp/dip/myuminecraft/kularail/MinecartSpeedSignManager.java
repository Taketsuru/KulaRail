package jp.dip.myuminecraft.kularail;

import java.util.HashMap;

import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.Plugin;

import jp.dip.myuminecraft.takecore.Logger;
import jp.dip.myuminecraft.takecore.ManagedSign;
import jp.dip.myuminecraft.takecore.Messages;
import jp.dip.myuminecraft.takecore.SignTable;
import jp.dip.myuminecraft.takecore.SignTableListener;
import jp.dip.myuminecraft.takecore.TakeCore;

public class MinecartSpeedSignManager implements SignTableListener, Listener {

    static final String        header               = "[kr.maxspeed]";
    static final String        permNodeToCreateSign = "kularail.maxspeed.";
    static final double        baseSpeed            = 0.4;

    Plugin                     plugin;
    Logger                     logger;
    Messages                   messages;
    TakeCore                   takeCore;
    SignTable                  signTable;
    Map<String, Double>        speedTags;
    Map<Location, ManagedSign> attachedBlocks;

    public MinecartSpeedSignManager(Plugin plugin, Logger logger,
            Messages messages, ConfigurationSection config) {
        this.plugin = plugin;
        this.logger = logger;
        this.messages = messages;
        this.takeCore = (TakeCore) plugin.getServer().getPluginManager()
                .getPlugin("TakeCore");
        this.signTable = takeCore.getSignTable();
        this.speedTags = new HashMap<String, Double>();
        this.attachedBlocks = new HashMap<Location, ManagedSign>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        speedTags.clear();
        Map<String, Object> configValues = config.getValues(false);
        if (configValues != null) {
            for (Map.Entry<String, Object> entry : configValues.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Number)) {
                    logger.warning("%s.tags.%s is not a number",
                            config.getCurrentPath(), entry.getKey());
                }
                speedTags.put(entry.getKey(), ((Number) value).doubleValue());
            }
        }

        signTable.addListener(this);
    }

    public void onDisable() {
        signTable.removeListener(this);
        speedTags.clear();
        attachedBlocks.clear();
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Minecart cart = (Minecart) event.getVehicle();

        org.bukkit.util.Vector velocity = cart.getVelocity();
        double velocityLimit = cart.getMaxSpeed() * 1.5;
        double velocityLength = velocity.length();
        if (velocityLimit < velocityLength) {
            cart.setVelocity(
                    velocity.clone().multiply(velocityLimit / velocityLength));
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        int diffX = to.getBlockX() - from.getBlockX();
        int diffY = to.getBlockY() - from.getBlockY();
        int diffZ = to.getBlockZ() - from.getBlockZ();

        if ((diffX == 0 && diffY == 0 && diffZ == 0)
                || from.getWorld() != to.getWorld()) {
            return;
        }

        Location cursor = new Location(to.getWorld(), to.getBlockX(),
                to.getBlockY() - 1, to.getBlockZ());

        if (diffX == 0 && diffY == 0) {
            while (diffZ != 0 && !checkSpeedSign(cursor, cart)) {
                if (0 < diffZ) {
                    cursor.add(0, 0, -1);
                    --diffZ;
                } else {
                    cursor.add(0, 0, 1);
                    ++diffZ;
                }
            }
        } else if (diffY == 0 && diffZ == 0) {
            while (diffX != 0 && !checkSpeedSign(cursor, cart)) {
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
    public boolean mayCreate(Player player, Location location,
            Location attachedLocation, String[] lines) {
        if (!isSpeedSign(lines)) {
            return true;
        }

        String allPerm = permNodeToCreateSign + '*';
        String perm = permNodeToCreateSign + SignUtil.getLabel(lines);
        if (!player.hasPermission(allPerm) && !player.hasPermission(perm)) {
            messages.send(player, "signCreationPermDenied");
            return false;
        }

        if (attachedBlocks.containsKey(attachedLocation)) {
            messages.send(player, "multipleSpeedSigns");
            return false;
        }

        try {
            String label = SignUtil.getLabel(lines);
            if (speedTags.get(label) == null) {
                Double.valueOf(label);
            }
        } catch (NumberFormatException e) {
            messages.send(player, "invalidSpeed");
            return false;
        }

        return true;
    }

    @Override
    public ManagedSign create(Player player, Location location,
            Location attachedLocation, String[] signText) {
        if (!isSpeedSign(signText)) {
            return null;
        }
        SignUtil.setHeaderColor(signText, ChatColor.GREEN);
        ManagedSign result = new ManagedSign(this, location, attachedLocation);
        attachedBlocks.put(result.getAttachedLocation(), result);
        return result;
    }

    @Override
    public void destroy(Player player, ManagedSign sign) {
        Location location = sign.getAttachedLocation();
        if (attachedBlocks.get(location) == sign) {
            attachedBlocks.remove(location);
        }
    }

    public boolean isSpeedSign(String[] lines) {
        return ChatColor.stripColor(lines[0]).equalsIgnoreCase(header);
    }

    boolean checkSpeedSign(Location location, Minecart cart) {
        ManagedSign sign = attachedBlocks.get(location);
        if (sign == null) {
            return false;
        }

        Sign signState = (Sign) sign.getLocation().getBlock().getState();
        String[] lines = signState.getLines();
        if (!isSpeedSign(lines)) {
            return false;
        }

        try {
            String tag = SignUtil.getLabel(lines);
            Double speed = speedTags.get(tag);
            if (speed == null) {
                speed = Double.valueOf(tag);
            }

            cart.setMaxSpeed(baseSpeed * speed);
        } catch (NumberFormatException e) {
            return false;
        }

        double velocity = cart.getVelocity().length();
        if (cart.getMaxSpeed() < velocity) {
            cart.setVelocity(cart.getVelocity().clone()
                    .multiply(cart.getMaxSpeed() / velocity));
        }

        return true;
    }

}
