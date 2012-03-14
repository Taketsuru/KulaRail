package com.github.taketsuru.KulaRail;

import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class SignedBlock {

    SignedBlock(Location location, Location signLocation, String[] signText) {
	this.location = location;
	this.signLocation = signLocation;
	this.headerColor = ChatColor.BLACK;
	this.signText = signText;
	isChangingSign = true;
    }

    SignedBlock(Server server, Map<String, Object> data) {
	String worldName = (String)data.get("world");
	@SuppressWarnings("unchecked")
	List<Integer> coord = (List<Integer>)data.get("location");
	location = new Location(server.getWorld(worldName), coord.get(0), coord.get(1), coord.get(2));
	BlockFace face = BlockFace.valueOf((String)data.get("face"));
	signLocation = location.clone().add(face.getModX(), face.getModY(), face.getModZ());
	headerColor = ChatColor.BLACK;
	signText = ((org.bukkit.block.Sign)signLocation.getBlock().getState()).getLines();
    }
    
    void setIsChangingSign(boolean value) {
	isChangingSign = value;
    }

    public Location getLocation() {
	return location;
    }

    public Location getSignLocation() {
	return location;
    }

    public Block getBlock() {
	return location.getBlock();
    }

    public Block getSignBlock() {
	return signLocation.getBlock();
    }

    public String[] getSignText() {
	return signText;
    }

    public String readHeader() {
	return ChatColor.stripColor(signText[0]);
    }
    
    public String readLabel() {
	StringBuffer result = new StringBuffer();
	for (int i = 1; i < signText.length; ++i) {
	    if (signText[i].isEmpty()) {
		continue;
	    }
	    if (result.length() != 0) {
		result.append('_');
	    }
	    result.append(signText[i]);
	}

	return result.toString();
    }

    public void setHeaderColor(ChatColor color) {
	headerColor = color;
	signText[0] = color + ChatColor.stripColor(signText[0]);
	if (! isChangingSign) {
	    org.bukkit.block.Sign state = (org.bukkit.block.Sign)getSignBlock().getState();
	    state.setLine(0, signText[0]);
	    state.update();
	}
    }
    
    void saveState(Map<String, Object> data) {
	data.put("world", location.getWorld().getName());

	Vector<Integer> coord = new Vector<Integer>();
	coord.add((int)location.getX());
	coord.add((int)location.getY());
	coord.add((int)location.getZ());
	data.put("location", coord);

	data.put("face", getBlock().getFace(getSignBlock()).toString());
    }

    Location location;
    Location signLocation;
    ChatColor headerColor;
    String[] signText;
    boolean isChangingSign;
}
