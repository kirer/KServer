package com.genymobile.scrcpy;

import android.annotation.SuppressLint;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;

@SuppressLint("MissingPermission")
public final class FakeContext extends ContextWrapper {

    public static final String PACKAGE_NAME = "com.android.shell";
    public static final int ROOT_UID = 0; // Like android.os.Process.ROOT_UID, but before API 29

    private static final FakeContext INSTANCE = new FakeContext();

    public static FakeContext get() {
        return INSTANCE;
    }

    private FakeContext() {
        super(getSystemContext());
    }

    private static Context getSystemContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("systemMain").invoke(null);
            return (Context) activityThreadClass.getMethod("getSystemContext").invoke(activityThread);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String getOpPackageName() {
        return PACKAGE_NAME;
    }

    // @Override to be added on SDK upgrade for Android 14
    public AttributionSource getAttributionSource() {
        try {
            AttributionSource.Builder builder = new AttributionSource.Builder(Process.SHELL_UID);
            builder.setPackageName(PACKAGE_NAME);
            return builder.build();
        } catch (Exception e) {
            // Old Android version
            return null;
        }
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        // Return the ApplicationInfo of the system package
        try {
            return super.getPackageManager().getApplicationInfo("android", 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}