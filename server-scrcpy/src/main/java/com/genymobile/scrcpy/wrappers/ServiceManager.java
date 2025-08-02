package com.genymobile.scrcpy.wrappers;

import android.annotation.SuppressLint;
import android.os.IBinder;

import java.lang.reflect.Method;

/**
 * scrcpy 简化实现
 * */
@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ServiceManager {

    private static DisplayManager displayManager;

    private ServiceManager() {
        // not instantiable
    }

    public static DisplayManager getDisplayManager() {
        if (displayManager == null) {
            displayManager = DisplayManager.create();
        }
        return displayManager;
    }
}