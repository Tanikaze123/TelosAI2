package com.minecraftai.models;

public class EntitySnapshot {
    public int tick;                  // Tick number
    public double x;                  // X position
    public double y;                  // Y position
    public double z;                  // Z position
    public double vx;                 // X velocity
    public double vy;                 // Y velocity
    public double vz;                 // Z velocity
    public float yaw;                 // Yaw rotation
    public float pitch;               // Pitch rotation
    public String animation;          // Animation state (if available)

    public EntitySnapshot() {}

    public EntitySnapshot(int tick, double x, double y, double z) {
        this.tick = tick;
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = 0;
        this.vy = 0;
        this.vz = 0;
    }
}
