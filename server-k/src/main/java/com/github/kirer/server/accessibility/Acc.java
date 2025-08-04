package com.github.kirer.server.accessibility;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accessibility 命令处理类
 * 完全复刻自 com.autogo.Accessibility.Acc
 */
public class Acc {
    private static final int MAX_ENTRIES = 1000;
    private static Accessibility acc;
    private static final Object lock = new Object();
    private static Map<String, UiObject> uiObjectMap = new LinkedHashMap<String, UiObject>(1001, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, UiObject> entry) {
            if (size() <= Acc.MAX_ENTRIES) {
                return false;
            }
            UiObject value = entry.getValue();
            if (value == null) {
                return true;
            }
            value.recycle();
            return true;
        }
    };

    private static String b2s(boolean z) {
        return z ? "true" : "false";
    }

    public static void call(BufferedWriter bufferedWriter, String[] split) throws IOException {
        if (split.length < 2) {
            return;
        }

        String command = split[1];
        String result = "";

        try {
            switch (command) {
                case "newAccessibility":
                    result = b2s(newAccessibility());
                    break;
                case "find":
                    if (split.length >= 3) {
                        result = find(split[2]);
                    }
                    break;
                case "findS":
                    if (split.length >= 3) {
                        result = findS(split[2]);
                    }
                    break;
                case "findOnce":
                    if (split.length >= 3) {
                        result = findOnce(split[2]);
                    }
                    break;
                case "uiObjectToString":
                    if (split.length >= 3) {
                        result = uiObjectToString(split[2]);
                    }
                    break;
                case "uiObjectClick":
                    if (split.length >= 3) {
                        result = b2s(uiObjectClick(split[2]));
                    }
                    break;
                case "uiObjectGetText":
                    if (split.length >= 3) {
                        result = uiObjectGetText(split[2]);
                    }
                    break;
                case "uiObjectGetId":
                    if (split.length >= 3) {
                        result = uiObjectGetId(split[2]);
                    }
                    break;
                default:
                    // 未知命令，返回空字符串
                    break;
            }

            writer(bufferedWriter, split[0], result);
        } catch (Exception e) {
            // 静默处理异常
        }
    }

    private static void writer(BufferedWriter bufferedWriter, String str, String str2) throws IOException {
        try {
            bufferedWriter.write(str + String.format("%06d", Integer.valueOf(str2.getBytes("UTF-8").length)) + str2);
            bufferedWriter.flush();
        } catch (IOException unused) {
        }
    }

    public static boolean newAccessibility() {
        acc = new Accessibility();
        return true;
    }

    public static String find(String str) {
        synchronized (lock) {
            acc.selector(str);
            List<UiObject> listFind = acc.find();
            if (listFind.size() == 0) {
                return "";
            }
            String str2 = "";
            for (UiObject uiObject : listFind) {
                String string = uiObject.toString();
                uiObjectMap.put(string, uiObject);
                str2 = str2 + string + "\n";
            }
            return str2;
        }
    }

    public static String findS(String str) {
        synchronized (lock) {
            acc.selector(str);
            List<UiObject> listFind = acc.find();
            if (listFind.size() == 0) {
                return "";
            }
            String str2 = "";
            Iterator<UiObject> it = listFind.iterator();
            while (it.hasNext()) {
                str2 = str2 + it.next().toStr() + "\n";
            }
            return str2;
        }
    }

    public static String findOnce(String str) {
        synchronized (lock) {
            acc.selector(str);
            UiObject uiObjectFindOnce = acc.findOnce();
            if (!uiObjectFindOnce.exists()) {
                return "";
            }
            String string = uiObjectFindOnce.toString();
            uiObjectMap.put(string, uiObjectFindOnce);
            return string;
        }
    }

    public static String uiObjectToString(String str) {
        UiObject uiobject = getUiobject(str);
        return uiobject != null ? uiobject.toStr() : "";
    }

    public static boolean uiObjectClick(String str) {
        UiObject uiobject = getUiobject(str);
        if (uiobject != null) {
            return uiobject.click();
        }
        return false;
    }

    public static String uiObjectGetText(String str) {
        UiObject uiobject = getUiobject(str);
        return uiobject != null ? uiobject.getText() : "";
    }

    public static String uiObjectGetId(String str) {
        UiObject uiobject = getUiobject(str);
        return uiobject != null ? uiobject.getId() : "";
    }

    private static UiObject getUiobject(String str) {
        UiObject uiObject;
        synchronized (lock) {
            uiObject = uiObjectMap.get(str);
        }
        if (uiObject == null || !uiObject.exists()) {
            return null;
        }
        return uiObject;
    }

    private static String i2s(int i) {
        return String.valueOf(i);
    }

    private static int s2i(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException unused) {
            return 0;
        }
    }
}
