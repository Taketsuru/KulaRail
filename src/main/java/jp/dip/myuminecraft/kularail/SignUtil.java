package jp.dip.myuminecraft.kularail;

import org.bukkit.ChatColor;
import org.bukkit.Location;

import jp.dip.myuminecraft.takecore.ManagedSign;

public class SignUtil {

    public static String getLabel(String[] lines) {
        StringBuffer result = new StringBuffer();
        for (int i = 1; i < lines.length; ++i) {
                if (lines[i].isEmpty()) {
                        continue;
                }
                if (result.length() != 0) {
                        result.append('_');
                }
                result.append(lines[i]);
        }

        return result.toString();
    }

    public static void setHeaderColor(String[] lines, ChatColor color) {
        lines[0] = color + ChatColor.stripColor(lines[0]);
    }
    
    public static double distanceSquared(ManagedSign s1, ManagedSign s2) {
        Location l1 = s1.getAttachedLocation();
        Location l2 = s2.getAttachedLocation();
        return l1.getWorld() == l2.getWorld()
                ? l1.distanceSquared(l2) : Double.MAX_VALUE / 2;
    }

}
