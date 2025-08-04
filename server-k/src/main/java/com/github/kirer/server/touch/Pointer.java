package com.github.kirer.server.touch;

/**
 * 指针类
 * 复刻自 com.autogo.Touch.Pointer
 */
public class Pointer {
    private Point point;
    private float pressure;
    private boolean up;

    public Pointer() {
        this.point = new Point(0, 0);
        this.pressure = 0.0f;
        this.up = true;
    }

    public Point getPoint() {
        return point;
    }

    public void setPoint(Point point) {
        this.point = point;
    }

    public float getPressure() {
        return pressure;
    }

    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    public boolean isUp() {
        return up;
    }

    public void setUp(boolean up) {
        this.up = up;
    }

    @Override
    public String toString() {
        return "Pointer{" +
                "point=" + point +
                ", pressure=" + pressure +
                ", up=" + up +
                '}';
    }
}
