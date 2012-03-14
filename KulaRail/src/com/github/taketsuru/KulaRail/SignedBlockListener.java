package com.github.taketsuru.KulaRail;

public interface SignedBlockListener {
    public boolean onCreate(SignedBlock block);
    public void onDestroy(SignedBlock block);
    public void onReset();
}
