package com.github.kirer.server;

import com.genymobile.scrcpy.util.Ln;

public class Utils {

    private static final String TAG = "Utils";
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Ln.e("[" + TAG + "] 线程睡眠时发生错误", e);
        }
    }
}
