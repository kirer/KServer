package com.github.kirer.server.accessibility;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import com.github.kirer.server.touch.Touch;

import java.util.ArrayList;
import java.util.List;

/**
 * UI对象封装类
 * 复刻自 com.autogo.Accessibility.UiObject
 */
public class UiObject {
    AccessibilityNodeInfo nodeInfo;

    public UiObject(AccessibilityNodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    public UiObject() {
        this.nodeInfo = null;
    }

    public boolean exists() {
        return this.nodeInfo != null;
    }

    public void recycle() {
        if (this.nodeInfo != null) {
            this.nodeInfo.recycle();
        }
    }

    public boolean click() {
        return this.nodeInfo != null && this.nodeInfo.performAction(16);
    }

    public boolean clickCenter() throws InterruptedException {
        if (this.nodeInfo == null) {
            return false;
        }
        
        Rect rect = new Rect();
        this.nodeInfo.getBoundsInScreen(rect);
        int x = (rect.right + rect.left) / 2;
        int y = (rect.bottom + rect.top) / 2;
        
        if (x <= 0 || y <= 0) {
            return false;
        }
        
        Touch.down(x, y, 0);
        sleep(20L);
        Touch.up(x, y, 0);
        return true;
    }

    public boolean longClick() {
        return this.nodeInfo != null && this.nodeInfo.performAction(32);
    }

    public boolean copy() {
        return this.nodeInfo != null && this.nodeInfo.performAction(16384);
    }

    public boolean cut() {
        return this.nodeInfo != null && this.nodeInfo.performAction(65536);
    }

    public boolean paste() {
        return this.nodeInfo != null && this.nodeInfo.performAction(32768);
    }

    public boolean scrollForward() {
        return this.nodeInfo != null && this.nodeInfo.performAction(4096);
    }

    public boolean scrollBackward() {
        return this.nodeInfo != null && this.nodeInfo.performAction(8192);
    }

    public boolean collapse() {
        if (this.nodeInfo == null) {
            return false;
        }
        if (this.nodeInfo.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE)) {
            return this.nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.getId());
        }
        return false;
    }

    public boolean expand() {
        if (this.nodeInfo == null) {
            return false;
        }
        if (this.nodeInfo.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)) {
            return this.nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.getId());
        }
        return false;
    }

    public boolean show() {
        if (this.nodeInfo == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23 || !this.nodeInfo.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN)) {
            return false;
        }
        return this.nodeInfo.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
    }

    public boolean select() {
        return this.nodeInfo != null && this.nodeInfo.performAction(4);
    }

    public boolean clearSelect() {
        return this.nodeInfo != null && this.nodeInfo.performAction(8);
    }

    public boolean setText(String text) {
        if (this.nodeInfo == null) {
            return false;
        }
        Bundle bundle = new Bundle();
        bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text);
        return this.nodeInfo.performAction(2097152, bundle);
    }

    public boolean setSelection(int start, int end) {
        if (this.nodeInfo == null || !this.nodeInfo.isEditable()) {
            return false;
        }
        this.nodeInfo.setTextSelection(start, end);
        return true;
    }

    public List<UiObject> getChildren() {
        ArrayList<UiObject> children = new ArrayList<>();
        if (this.nodeInfo == null) {
            return children;
        }
        
        int childCount = this.nodeInfo.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = this.nodeInfo.getChild(i);
            if (child != null) {
                children.add(new UiObject(child));
            }
        }
        return children;
    }

    public UiObject getChild(int index) {
        if (this.nodeInfo == null || index >= this.nodeInfo.getChildCount()) {
            return new UiObject();
        }
        return new UiObject(this.nodeInfo.getChild(index));
    }

    public UiObject getParent() {
        if (this.nodeInfo == null) {
            return new UiObject();
        }
        return new UiObject(this.nodeInfo.getParent());
    }

    public String getBounds() {
        if (this.nodeInfo == null) {
            return "0,0,0,0";
        }
        Rect rect = new Rect();
        this.nodeInfo.getBoundsInScreen(rect);
        return rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
    }

    public String getBoundsInParent() {
        if (this.nodeInfo == null) {
            return "0,0,0,0";
        }
        Rect rect = new Rect();
        this.nodeInfo.getBoundsInParent(rect);
        return rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom;
    }

    public String getId() {
        if (this.nodeInfo == null) {
            return "";
        }
        String viewIdResourceName = this.nodeInfo.getViewIdResourceName();
        if (viewIdResourceName == null) {
            return "";
        }
        int index = viewIdResourceName.indexOf(":id/");
        return index == -1 ? "" : viewIdResourceName.substring(index + 4);
    }

    public String getText() {
        if (this.nodeInfo == null) {
            return "";
        }
        return String.valueOf(this.nodeInfo.getText());
    }

    public String getDesc() {
        if (this.nodeInfo == null) {
            return "";
        }
        return String.valueOf(this.nodeInfo.getContentDescription());
    }

    public String getPackageName() {
        if (this.nodeInfo == null) {
            return "";
        }
        return String.valueOf(this.nodeInfo.getPackageName());
    }

    public String getClassName() {
        if (this.nodeInfo == null) {
            return "";
        }
        return String.valueOf(this.nodeInfo.getClassName());
    }

    // 各种属性获取方法
    public boolean getClickAble() {
        return this.nodeInfo != null && this.nodeInfo.isClickable();
    }

    public boolean getLongClickAble() {
        return this.nodeInfo != null && this.nodeInfo.isLongClickable();
    }

    public boolean getCheckable() {
        return this.nodeInfo != null && this.nodeInfo.isCheckable();
    }

    public boolean getSelected() {
        return this.nodeInfo != null && this.nodeInfo.isSelected();
    }

    public boolean getEnabled() {
        return this.nodeInfo != null && this.nodeInfo.isEnabled();
    }

    public boolean getScrollAble() {
        return this.nodeInfo != null && this.nodeInfo.isScrollable();
    }

    public boolean getEditable() {
        return this.nodeInfo != null && this.nodeInfo.isEditable();
    }

    public boolean getMultiLine() {
        return this.nodeInfo != null && this.nodeInfo.isMultiLine();
    }

    public boolean getChecked() {
        return this.nodeInfo != null && this.nodeInfo.isChecked();
    }

    public boolean getFocusable() {
        return this.nodeInfo != null && this.nodeInfo.isFocused();
    }

    public boolean getDismissable() {
        return this.nodeInfo != null && this.nodeInfo.isDismissable();
    }

    public boolean getContextClickable() {
        if (this.nodeInfo == null || Build.VERSION.SDK_INT < 23) {
            return false;
        }
        return this.nodeInfo.isContextClickable();
    }

    public boolean getAccessibilityFocused() {
        return this.nodeInfo != null && this.nodeInfo.isAccessibilityFocused();
    }

    public int getChildCound() {
        return this.nodeInfo != null ? this.nodeInfo.getChildCount() : 0;
    }

    public int getDrawingOrder() {
        if (this.nodeInfo == null || Build.VERSION.SDK_INT < 24) {
            return 0;
        }
        return this.nodeInfo.getDrawingOrder();
    }

    public int getIndexInParent() {
        if (this.nodeInfo == null) {
            return 0;
        }
        AccessibilityNodeInfo parent = this.nodeInfo.getParent();
        if (parent == null) {
            return 0;
        }
        
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child != null && child.equals(this.nodeInfo)) {
                child.recycle();
                return i;
            }
        }
        return 0;
    }

    private static String safeCharSeqToString(CharSequence charSequence) {
        return charSequence == null ? "" : String.valueOf(charSequence);
    }

    @Override
    public String toString() {
        return String.valueOf(System.identityHashCode(this));
    }

    public String toStr() {
        if (this.nodeInfo == null) {
            return "";
        }
        return String.format("id=%s|text=%s|desc=%s|className=%s|packageName=%s|bounds=%s|clickable=%s|enabled=%s",
            getId(), getText(), getDesc(), getClassName(), getPackageName(), getBounds(),
            getClickAble(), getEnabled());
    }

    private static void sleep(long millis) throws InterruptedException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 忽略中断异常
        }
    }
}
