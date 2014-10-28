package com.github.taketsuru.KulaRail;

import org.bukkit.entity.Player;

public interface SignedBlockListener {
    public boolean onCreate(SignedBlock block);
    public void onDestroy(SignedBlock block);
    public void onReset();
    public boolean mayCreate(Player player, SignedBlock block);
}
