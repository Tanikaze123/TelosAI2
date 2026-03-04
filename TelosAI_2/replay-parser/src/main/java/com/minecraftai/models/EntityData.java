package com.minecraftai.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class EntityData {
    public String id;                 // Entity ID (internal tracking)
    public UUID uuid;                 // Minecraft entity UUID
    public String type;               // Entity type (e.g., "armor_stand", "zombie")
    public String modelId;            // ModelEngine model name (e.g. "wumpus")
    public String modelPath;          // Full ModelEngine path (e.g. "wumpus/head_1")
    public String groupId;            // CompositeEntity group this entity belongs to
    // transient = excluded from Gson serialization.
    // These are populated during parsing and used by TimelineBuilder.buildTickStream()
    // to generate ParsedReplay.ticks, then discarded from the JSON output.
    public transient List<EntitySnapshot> timeline;
    
    public float[] translation = new float[3];   // [x, y, z]
    public float[] scale = new float[3];         // [x, y, z]
    public float[] leftRotation = new float[4];  // [x, y, z, w]  quaternion
    public float[] rightRotation = new float[4]; // [x, y, z, w]  quaternion

    /** Per-tick transform history. Only appended when values change. transient = excluded from JSON output. */
    public transient List<TransformSnapshot> transformTimeline = new ArrayList<>();

    public EntityData() {
        this.timeline = new ArrayList<>();
    }

    public EntityData(String id, UUID uuid, String type) {
        this.id = id;
        this.uuid = uuid;
        this.type = type;
        this.timeline = new ArrayList<>();
    }

    public void addSnapshot(EntitySnapshot snapshot) {
        this.timeline.add(snapshot);
    }

    /**
     * Snapshot the current transform fields into transformTimeline.
     * Only records a new entry when values change, keeping the list compact.
     * If the last entry already has this tick, it is overwritten.
     */
    public void recordTransform(int tick) {
        TransformSnapshot ts = new TransformSnapshot();
        ts.tick = tick;
        ts.translation = translation.clone();
        ts.scale = scale.clone();
        ts.leftRotation = leftRotation.clone();
        ts.rightRotation = rightRotation.clone();

        if (!transformTimeline.isEmpty()) {
            TransformSnapshot last = transformTimeline.get(transformTimeline.size() - 1);
            if (last.tick == tick) {
                transformTimeline.set(transformTimeline.size() - 1, ts);
                return;
            }
            if (Arrays.equals(last.translation, ts.translation)
                    && Arrays.equals(last.scale, ts.scale)
                    && Arrays.equals(last.leftRotation, ts.leftRotation)
                    && Arrays.equals(last.rightRotation, ts.rightRotation)) {
                return; // nothing changed
            }
        }
        transformTimeline.add(ts);
    }

    /** Replace any NaN/Infinity values in transform arrays with 0 so Gson can serialize them. */
    public void sanitizeTransforms() {
        sanitizeArray(translation);
        sanitizeArray(scale);
        sanitizeQuaternion(leftRotation);
        sanitizeQuaternion(rightRotation);
        for (TransformSnapshot ts : transformTimeline) {
            sanitizeArray(ts.translation);
            sanitizeArray(ts.scale);
            sanitizeQuaternion(ts.leftRotation);
            sanitizeQuaternion(ts.rightRotation);
        }
    }

    private static void sanitizeArray(float[] arr) {
        if (arr == null) return;
        for (int i = 0; i < arr.length; i++) {
            if (Float.isNaN(arr[i]) || Float.isInfinite(arr[i])) arr[i] = 0f;
        }
    }

    /** Zero out the quaternion if any component is NaN, Infinite, or outside [-1.5, 1.5] (garbage data). */
    private static void sanitizeQuaternion(float[] q) {
        if (q == null) return;
        for (float v : q) {
            if (Float.isNaN(v) || Float.isInfinite(v) || v > 1.5f || v < -1.5f) {
                java.util.Arrays.fill(q, 0f);
                return;
            }
        }
    }
}
