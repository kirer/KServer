package com.github.kirer.server.touch;

import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * 触摸输入功能类
 * 复刻自 com.autogo.Touch.Touch
 */
public class Touch {
    static long lastTouchDown;
    private static final Object manager;
    static Method sInjectInputEventMethod;
    private static final PointersState pointersState = new PointersState();
    private static final MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[10];
    private static final MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[10];

    static {
        try {
            Object objInvoke = getInputManagerClass().getDeclaredMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            manager = objInvoke;
            sInjectInputEventMethod = objInvoke.getClass().getMethod("injectInputEvent", InputEvent.class, Integer.TYPE);

            for (int i = 0; i < 10; i++) {
                MotionEvent.PointerProperties pointerProperties2 = new MotionEvent.PointerProperties();
                pointerProperties2.toolType = 1;
                MotionEvent.PointerCoords pointerCoords2 = new MotionEvent.PointerCoords();
                pointerCoords2.orientation = 0.0f;
                pointerCoords2.size = 0.0f;
                pointerProperties[i] = pointerProperties2;
                pointerCoords[i] = pointerCoords2;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> getInputManagerClass() {
        try {
            return Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            return InputManager.class;
        }
    }

    public static void up(int i, int i2, int i3) {
        injectTouch(1, i, i2, i3, 0.0f);
    }

    public static void move(int i, int i2, int i3) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        injectTouch(2, i, i2, i3, 1.0f);
    }

    public static void down(int i, int i2, int i3) {
        injectTouch(0, i, i2, i3, 1.0f);
    }

    public static void keyCode(int i) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        long jUptimeMillis = SystemClock.uptimeMillis();
        injectKeyEvent(new KeyEvent(jUptimeMillis, jUptimeMillis, 0, i, 0, 0, -1, 0, 0, 257));
        injectKeyEvent(new KeyEvent(jUptimeMillis, jUptimeMillis, 1, i, 0, 0, -1, 0, 0, 257));
    }

    private static void injectKeyEvent(KeyEvent keyEvent) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        try {
            sInjectInputEventMethod.invoke(manager, keyEvent, 0);
        } catch (Exception unused) {
        }
    }

    public static void swipe(int startX, int startY, int endX, int endY, int duration) 
            throws IllegalAccessException, InterruptedException, IllegalArgumentException, InvocationTargetException {
        
        down(startX, startY, 0);
        int steps = Math.max(duration / 10, 1);
        float stepX = (float)(endX - startX) / steps;
        float stepY = (float)(endY - startY) / steps;
        long stepDuration = (duration * 1000000L) / steps;
        long startTime = System.nanoTime();
        
        for (int i = 0; i < steps; i++) {
            int currentStep = i + 1;
            long expectedTime = currentStep * stepDuration - (System.nanoTime() - startTime);
            if (expectedTime > 0) {
                try {
                    Thread.sleep(expectedTime / 1000000, (int)(expectedTime % 1000000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            float currentX = currentStep * stepX;
            float currentY = currentStep * stepY;
            move((int)(currentX + startX), (int)(currentY + startY), 0);
        }
        up(endX, endY, 0);
    }

    public static void swipeWithBezier(int startX, int startY, int endX, int endY, int duration) 
            throws IllegalAccessException, InterruptedException, IllegalArgumentException, InvocationTargetException {
        
        down(startX, startY, 0);
        Random random = new Random();
        int steps = Math.max(duration / 10, 1);
        long stepDuration = (duration * 1000000L) / steps;
        
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float distance = Math.min((float)Math.sqrt(deltaX * deltaX + deltaY * deltaY), 200.0f) * 0.4f;
        float angle = (float)Math.atan2(deltaY, deltaX) + 1.5707964f;
        float randomOffset = (random.nextFloat() - 0.5f) * distance;
        
        double angleRad = angle;
        float controlX = (startX + endX) / 2 + ((float)Math.cos(angleRad) * randomOffset);
        float controlY = (startY + endY) / 2 + (randomOffset * (float)Math.sin(angleRad));
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i <= steps; i++) {
            float t = (float)i / steps;
            float oneMinusT = 1.0f - t;
            float oneMinusTSquared = oneMinusT * oneMinusT;
            float twoTOneMinusT = 2.0f * t * oneMinusT;
            float tSquared = t * t;
            
            float x = (startX * oneMinusTSquared) + (twoTOneMinusT * controlX) + (endX * tSquared);
            float y = (oneMinusTSquared * startY) + (twoTOneMinusT * controlY) + (tSquared * endY);
            
            int nextStep = i + 1;
            long expectedTime = nextStep * stepDuration - (System.nanoTime() - startTime);
            if (expectedTime > 0) {
                try {
                    Thread.sleep(expectedTime / 1000000, (int)(expectedTime % 1000000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            move((int)x, (int)y, 0);
        }
        up(endX, endY, 0);
    }

    private static void injectTouch(int action, int x, int y, int pointerId, float pressure) {
        try {
            long eventTime = SystemClock.uptimeMillis();
            Point point = new Point(x, y);
            PointersState pointersState = Touch.pointersState;
            int pointerIndex = pointersState.getPointerIndex(pointerId);
            Pointer pointer = pointersState.get(pointerIndex);
            pointer.setPoint(point);
            pointer.setPressure(pressure);
            pointer.setUp(action == MotionEvent.ACTION_UP || action == ((pointerIndex << 8) | MotionEvent.ACTION_POINTER_UP));
            
            MotionEvent.PointerProperties[] properties = Touch.pointerProperties;
            MotionEvent.PointerCoords[] coords = Touch.pointerCoords;
            int activePointers = pointersState.update(properties, coords);
            
            int finalAction = action;
            int finalPointerCount = activePointers;
            
            if (activePointers == 1) {
                if (action == MotionEvent.ACTION_DOWN) {
                    lastTouchDown = eventTime;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                finalAction = (pointerIndex << 8) | MotionEvent.ACTION_POINTER_UP;
            } else if (action == MotionEvent.ACTION_DOWN) {
                finalAction = (pointerIndex << 8) | MotionEvent.ACTION_POINTER_DOWN;
            }
            
            if (finalAction == MotionEvent.ACTION_POINTER_UP && activePointers == 0) {
                finalPointerCount = 1;
                finalAction = MotionEvent.ACTION_UP;
            }
            
            MotionEvent motionEvent = MotionEvent.obtain(
                lastTouchDown, eventTime, finalAction, finalPointerCount, 
                properties, coords, 0, 0, 1.0f, 1.0f, 0, 0, 4098, 0
            );
            
            sInjectInputEventMethod.invoke(manager, motionEvent, 0);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
