package com.github.kirer.server.touch;

import android.view.MotionEvent;

/**
 * 指针状态管理类
 * 复刻自 com.autogo.Touch.PointersState
 */
public class PointersState {
    private static final int MAX_POINTERS = 10;
    private Pointer[] pointers = new Pointer[MAX_POINTERS];
    
    public PointersState() {
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointers[i] = new Pointer();
        }
    }
    
    public int getPointerIndex(int pointerId) {
        // 简单映射：pointerId直接对应数组索引
        return Math.max(0, Math.min(pointerId, MAX_POINTERS - 1));
    }
    
    public Pointer get(int index) {
        if (index >= 0 && index < MAX_POINTERS) {
            return pointers[index];
        }
        return pointers[0];
    }
    
    public int update(MotionEvent.PointerProperties[] pointerProperties, 
                     MotionEvent.PointerCoords[] pointerCoords) {
        int activePointers = 0;
        
        for (int i = 0; i < MAX_POINTERS; i++) {
            Pointer pointer = pointers[i];
            if (!pointer.isUp()) {
                // 设置指针属性
                pointerProperties[activePointers].id = i;
                pointerProperties[activePointers].toolType = MotionEvent.TOOL_TYPE_FINGER;
                
                // 设置指针坐标
                Point point = pointer.getPoint();
                pointerCoords[activePointers].x = point.getX();
                pointerCoords[activePointers].y = point.getY();
                pointerCoords[activePointers].pressure = pointer.getPressure();
                pointerCoords[activePointers].size = 1.0f;
                pointerCoords[activePointers].touchMajor = 1.0f;
                pointerCoords[activePointers].touchMinor = 1.0f;
                pointerCoords[activePointers].toolMajor = 1.0f;
                pointerCoords[activePointers].toolMinor = 1.0f;
                pointerCoords[activePointers].orientation = 0.0f;
                
                activePointers++;
            }
        }
        
        return activePointers;
    }
}
