package com.minecraftai.models;

/**
 * A single entity's state change at one tick.
 */
public class TickChange {

    /** Internal entity ID (e_XXXX) — cross-reference against entities[] in ParsedReplay. */
    public String id;

    // ── Position (from EntitySnapshot) ──────────────────────────────────────
    // Leave as null/NaN if no position change at this tick.

    public Double x;
    public Double y;
    public Double z;
    public Float yaw;
    public Float pitch;

    // ── Display entity transform (from TransformSnapshot) ────────────────────
    // Leave as null if no transform change at this tick.
    // Order for rendering: world pos → translation → leftRotation → scale → rightRotation

    public float[] translation;   // [x, y, z]
    public float[] scale;         // [x, y, z]
    public float[] leftRotation;  // [x, y, z, w] quaternion
    public float[] rightRotation; // [x, y, z, w] quaternion
}
