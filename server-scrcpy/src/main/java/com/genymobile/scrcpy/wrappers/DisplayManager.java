package com.genymobile.scrcpy.wrappers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.Ln;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * scrcpy 简化实现
 */
@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class DisplayManager {

    private final Object manager; // instance of hidden class android.hardware.display.DisplayManagerGlobal
    private Method getDisplayInfoMethod;
    private Method createVirtualDisplayMethod;

    static DisplayManager create() {
        try {
            Class<?> clazz = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Method getInstanceMethod = clazz.getDeclaredMethod("getInstance");
            Object dmg = getInstanceMethod.invoke(null);
            return new DisplayManager(dmg);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private DisplayManager(Object manager) {
        this.manager = manager;
    }

    // getDisplayInfo() may be used from both the Controller thread and the video (main) thread
    private synchronized Method getGetDisplayInfoMethod() throws NoSuchMethodException {
        if (getDisplayInfoMethod == null) {
            getDisplayInfoMethod = manager.getClass().getMethod("getDisplayInfo", int.class);
        }
        return getDisplayInfoMethod;
    }

    public DisplayInfo getDisplayInfo(int displayId) {
        try {
            Method method = getGetDisplayInfoMethod();
            Object displayInfo = method.invoke(manager, displayId);
            if (displayInfo == null) {
                return null;
            }
            Class<?> cls = displayInfo.getClass();
            // width and height already take the rotation into account
            int width = cls.getDeclaredField("logicalWidth").getInt(displayInfo);
            int height = cls.getDeclaredField("logicalHeight").getInt(displayInfo);
            int rotation = cls.getDeclaredField("rotation").getInt(displayInfo);
            int layerStack = cls.getDeclaredField("layerStack").getInt(displayInfo);
            int flags = cls.getDeclaredField("flags").getInt(displayInfo);
            int dpi = cls.getDeclaredField("logicalDensityDpi").getInt(displayInfo);
            String uniqueId = (String) cls.getDeclaredField("uniqueId").get(displayInfo);
            return new DisplayInfo(displayId, new Size(width, height), rotation, layerStack, flags, dpi, uniqueId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public int[] getDisplayIds() {
        try {
            return (int[]) manager.getClass().getMethod("getDisplayIds").invoke(manager);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Method getCreateVirtualDisplayMethod() throws NoSuchMethodException {
        if (createVirtualDisplayMethod == null) {
            createVirtualDisplayMethod = android.hardware.display.DisplayManager.class.getMethod("createVirtualDisplay", String.class, int.class, int.class, int.class, Surface.class);
        }
        return createVirtualDisplayMethod;
    }

    public VirtualDisplay createVirtualDisplay(String name, int width, int height, int displayIdToMirror, Surface surface) throws Exception {
        Method method = getCreateVirtualDisplayMethod();
        return (VirtualDisplay) method.invoke(null, name, width, height, displayIdToMirror, surface);
    }

}