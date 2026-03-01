package com.minecraftai.models;

/**
 * Records the display-entity transform (translation, scale, left/right rotation)
 * at a specific game tick, so the frontend can animate transforms over time.
 */
public class TransformSnapshot {
    public int tick;
    public float[] translation;   // [x, y, z]
    public float[] scale;         // [x, y, z]
    public float[] leftRotation;  // [x, y, z, w] quaternion
    public float[] rightRotation; // [x, y, z, w] quaternion
}
