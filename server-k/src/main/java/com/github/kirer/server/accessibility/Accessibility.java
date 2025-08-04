package com.github.kirer.server.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 无障碍服务主类
 * 复刻自 com.autogo.Accessibility.Accessibility
 */
public class Accessibility {
    private static final String HANDLER_THREAD_NAME = "AutoGo-UiDumpHandlerThread-" + SystemClock.uptimeMillis();
    private HandlerThread mHandlerThread;
    private UiAutomation mUiAutomation;
    Map<String, String> selectorMap;

    public Accessibility() {
        HandlerThread handlerThread = new HandlerThread(HANDLER_THREAD_NAME);
        this.mHandlerThread = handlerThread;
        if (!handlerThread.isAlive()) {
            this.mHandlerThread.start();
            try {
                // 使用反射创建UiAutomationConnection
                Class<?> connectionClass = Class.forName("android.app.UiAutomationConnection");
                Object connection = connectionClass.newInstance();

                // 创建UiAutomation实例
                Constructor<UiAutomation> constructor = UiAutomation.class.getConstructor(
                    android.os.Looper.class, connectionClass);
                UiAutomation uiAutomation = constructor.newInstance(this.mHandlerThread.getLooper(), connection);
                this.mUiAutomation = uiAutomation;

                // 连接UiAutomation
                uiAutomation.getClass().getMethod("connect").invoke(uiAutomation);
            } catch (Throwable unused) {
                System.err.println("设备中有其他无障碍服务正在运行导致出现冲突");
                System.exit(1);
            }
        }
        this.selectorMap = new HashMap();
        AccessibilityServiceInfo serviceInfo = this.mUiAutomation.getServiceInfo();
        serviceInfo.eventTypes = -1;
        serviceInfo.feedbackType = -1;
        serviceInfo.notificationTimeout = 100L;
        serviceInfo.flags = 122;
        serviceInfo.packageNames = null;
        this.mUiAutomation.setServiceInfo(serviceInfo);
    }

    public void close() {
        try {
            this.mUiAutomation.getClass().getMethod("disconnect").invoke(this.mUiAutomation);
        } catch (Throwable unused) {
            System.err.println("设备中有其他无障碍服务正在运行导致出现冲突");
            System.exit(1);
        }
    }

    public void selector(String str) {
        for (String str2 : str.split("&&")) {
            String[] strArrSplit = str2.split("@@");
            if (strArrSplit.length == 2) {
                this.selectorMap.put(strArrSplit[0], strArrSplit[1]);
            } else {
                this.selectorMap.put(strArrSplit[0], "");
            }
        }
    }

    public List<UiObject> find() {
        List<AccessibilityNodeInfo> allNodes = getAllNodes();
        ArrayList arrayList = new ArrayList();
        try {
            ArrayList arrayList2 = new ArrayList();
            for (int i = 0; i < allNodes.size(); i++) {
                if (hasNode(allNodes.get(i))) {
                    arrayList2.add(allNodes.get(i));
                }
            }
            allNodes.clear();
            this.selectorMap.clear();
            Iterator it = arrayList2.iterator();
            while (it.hasNext()) {
                arrayList.add(new UiObject((AccessibilityNodeInfo) it.next()));
            }
        } catch (NullPointerException unused) {
            allNodes.clear();
            this.selectorMap.clear();
        }
        return arrayList;
    }

    public UiObject findOnce() {
        List<UiObject> listFind = find();
        if (listFind != null && !listFind.isEmpty()) {
            return listFind.get(0);
        }
        return new UiObject();
    }

    public List<AccessibilityNodeInfo> getAllNodes() {
        ArrayList arrayList = new ArrayList();
        List<AccessibilityWindowInfo> windows = this.mUiAutomation.getWindows();
        if (!windows.isEmpty()) {
            Iterator<AccessibilityWindowInfo> it = windows.iterator();
            while (it.hasNext()) {
                AccessibilityNodeInfo root = it.next().getRoot();
                if (root != null) {
                    traverseNode(root, arrayList);
                }
            }
        }
        return arrayList;
    }

    public void traverseNode(AccessibilityNodeInfo accessibilityNodeInfo, List<AccessibilityNodeInfo> list) {
        if (accessibilityNodeInfo == null) {
            return;
        }
        list.add(accessibilityNodeInfo);
        for (int i = 0; i < accessibilityNodeInfo.getChildCount(); i++) {
            traverseNode(accessibilityNodeInfo.getChild(i), list);
        }
    }

    private boolean hasNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }
        
        for (Map.Entry<String, String> entry : selectorMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            switch (key) {
                case "text":
                    if (!safeCharSeqToString(node.getText()).equals(value)) {
                        return false;
                    }
                    break;
                case "textContains":
                    if (!safeCharSeqToString(node.getText()).contains(value)) {
                        return false;
                    }
                    break;
                case "textStartsWith":
                    if (!safeCharSeqToString(node.getText()).startsWith(value)) {
                        return false;
                    }
                    break;
                case "textEndsWith":
                    if (!safeCharSeqToString(node.getText()).endsWith(value)) {
                        return false;
                    }
                    break;
                case "desc":
                    if (!safeCharSeqToString(node.getContentDescription()).equals(value)) {
                        return false;
                    }
                    break;
                case "descContains":
                    if (!safeCharSeqToString(node.getContentDescription()).contains(value)) {
                        return false;
                    }
                    break;
                case "descStartsWith":
                    if (!safeCharSeqToString(node.getContentDescription()).startsWith(value)) {
                        return false;
                    }
                    break;
                case "descEndsWith":
                    if (!safeCharSeqToString(node.getContentDescription()).endsWith(value)) {
                        return false;
                    }
                    break;
                case "id":
                    if (!getId(node).equals(value)) {
                        return false;
                    }
                    break;
                case "idContains":
                    if (!getId(node).contains(value)) {
                        return false;
                    }
                    break;
                case "idStartsWith":
                    if (!getId(node).startsWith(value)) {
                        return false;
                    }
                    break;
                case "idEndsWith":
                    if (!getId(node).endsWith(value)) {
                        return false;
                    }
                    break;
                case "className":
                    if (!safeCharSeqToString(node.getClassName()).equals(value)) {
                        return false;
                    }
                    break;
                case "classNameContains":
                    if (!safeCharSeqToString(node.getClassName()).contains(value)) {
                        return false;
                    }
                    break;
                case "packageName":
                    if (!safeCharSeqToString(node.getPackageName()).equals(value)) {
                        return false;
                    }
                    break;
                case "packageNameContains":
                    if (!safeCharSeqToString(node.getPackageName()).contains(value)) {
                        return false;
                    }
                    break;
                case "clickable":
                    if (node.isClickable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "longClickable":
                    if (node.isLongClickable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "checkable":
                    if (node.isCheckable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "checked":
                    if (node.isChecked() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "selected":
                    if (node.isSelected() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "enabled":
                    if (node.isEnabled() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "scrollable":
                    if (node.isScrollable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "editable":
                    if (node.isEditable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "multiLine":
                    if (node.isMultiLine() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "focusable":
                    if (node.isFocusable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "focused":
                    if (node.isFocused() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "accessibilityFocused":
                    if (node.isAccessibilityFocused() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "dismissable":
                    if (node.isDismissable() != Boolean.parseBoolean(value)) {
                        return false;
                    }
                    break;
                case "depth":
                    // 深度匹配需要额外实现
                    break;
                case "indexInParent":
                    // 索引匹配需要额外实现
                    break;
                default:
                    // 未知属性，忽略
                    break;
            }
        }
        return true;
    }

    private static String getId(AccessibilityNodeInfo accessibilityNodeInfo) {
        int iIndexOf;
        String viewIdResourceName = accessibilityNodeInfo.getViewIdResourceName();
        return (viewIdResourceName == null || (iIndexOf = viewIdResourceName.indexOf(":id/")) == -1) ? "" : viewIdResourceName.substring(iIndexOf + 4);
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

    private static String safeCharSeqToString(CharSequence charSequence) {
        return charSequence == null ? "" : String.valueOf(charSequence);
    }
}
