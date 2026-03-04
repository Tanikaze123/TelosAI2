package com.minecraftai;

import com.minecraftai.models.EntityData;
import com.minecraftai.models.EntitySnapshot;

import java.util.*;

public class EntityTracker {
    private final Map<Integer, EntityData> entities = new HashMap<>();
    private int nextEntityId = 1;

    /**
     * Maps Minecraft entity ID → list of Minecraft entity IDs that are passengers.
     * Populated by handleSetPassengers() in PacketProcessor.
     *
     * Key   = vehicle MC entity ID
     * Value = list of passenger MC entity IDs
     *
     * Updated every time a Set Passengers packet (0x??) is received.
     * If a vehicle's passenger list becomes empty, the key is removed.
     */
    private final Map<Integer, List<Integer>> vehicleToPassengers = new HashMap<>();

    /**
     * Reverse map: passenger MC entity ID → vehicle MC entity ID.
     * Kept in sync with vehicleToPassengers.
     */
    private final Map<Integer, Integer> passengerToVehicle = new HashMap<>();

    /**
     * Model names received before the entity's spawn packet arrived.
     * Applied to the EntityData when it is eventually created in trackEntity.
     */
    private final Map<Integer, String> pendingModelNames = new HashMap<>();

    /** Full model paths (e.g. "wumpus/head_1") pending spawn. */
    private final Map<Integer, String> pendingModelPaths = new HashMap<>();

    /**
     * Track a new entity or update existing
     */
    public void trackEntity(int entityId, UUID uuid, String type, int tick,
                           double x, double y, double z,
                           float yaw, float pitch) {
        EntityData entity = entities.get(entityId);

        if (entity == null) {
            // New entity
            entity = new EntityData(
                "e_" + String.format("%04d", nextEntityId++),
                uuid,
                type
            );
            entities.put(entityId, entity);
            // Apply any model name/path that arrived before the spawn packet
            String pending = pendingModelNames.remove(entityId);
            if (pending != null) entity.modelId = pending;
            String pendingPath = pendingModelPaths.remove(entityId);
            if (pendingPath != null) entity.modelPath = pendingPath;
        }

        // Add snapshot
        EntitySnapshot snapshot = new EntitySnapshot(tick, x, y, z);
        snapshot.yaw = yaw;
        snapshot.pitch = pitch;

        // Calculate velocity if we have previous position
        if (!entity.timeline.isEmpty()) {
            EntitySnapshot prev = entity.timeline.get(entity.timeline.size() - 1);
            int tickDelta = tick - prev.tick;
            if (tickDelta > 0) {
                snapshot.vx = (x - prev.x) / tickDelta;
                snapshot.vy = (y - prev.y) / tickDelta;
                snapshot.vz = (z - prev.z) / tickDelta;
            }
        }

        entity.addSnapshot(snapshot);
    }

    /**
     * Mark entity as removed
     */
    public void removeEntity(int entityId) {
        entities.remove(entityId);
    }

    /**
     * Get all tracked entities
     */
    public List<EntityData> getAllEntities() {
        return new ArrayList<>(entities.values());
    }

    /**
     * Get specific entity
     */
    public EntityData getEntity(int entityId) {
        return entities.get(entityId);
    }

    /**
     * Called when a Set Passengers packet is received.
     * 
     * @param vehicleMcId   Minecraft entity ID of the vehicle
     * @param passengerMcIds  List of Minecraft entity IDs of passengers (may be empty)
     */
    public void setPassengers(int vehicleId, List<Integer> passengerIds) {
        // implement passenger relationship tracking
        // Step 1: clear old entries for this vehicle
    	List<Integer> oldPassengers = vehicleToPassengers.get(vehicleId);
    	if (oldPassengers != null) {
    	    for (int pid : oldPassengers) passengerToVehicle.remove(pid);
    	}
        // Step 2: if passengerMcIds is empty, remove vehicle key and return
    	if (passengerIds.isEmpty()) {
    	    vehicleToPassengers.remove(vehicleId);
    	    return;
    	}
        // Step 3: store vehicleToPassengers.put(vehicleMcId, passengerMcIds)
    	vehicleToPassengers.put(vehicleId, passengerIds);
        // Step 4: for each passengerId, passengerToVehicle.put(passengerId, vehicleMcId)
    	for (int pid : passengerIds) passengerToVehicle.put(pid, vehicleId);
    	
    }

    /**
     * Returns the full vehicle→passengers map.
     * Used by ModelEngineDetector to reconstruct composite groups.
     *
     * Key   = vehicle MC entity ID
     * Value = list of passenger MC entity IDs
     */
    public Map<Integer, List<Integer>> getVehicleToPassengersMap() {
        return vehicleToPassengers;
    }

    /**
     * Looks up an EntityData by Minecraft entity ID (not internal e_XXXX ID).
     * Used by ModelEngineDetector when walking the passenger tree.
     *
     * @param mcEntityId The raw Minecraft network entity ID
     * @return EntityData or null if not tracked
     */
    public EntityData getEntityByMcId(int mcEntityId) {
        return entities.get(mcEntityId);
    }

    /**
     * Sets the modelId on an entity once extracted from entity_metadata.
     * Only sets if the entity exists and modelId not already populated.
     *
     * @param mcEntityId  Minecraft network entity ID
     * @param modelName   e.g. "golden_freddy" (extracted by EntityMetadataReader)
     */
    public void setModelName(int mcEntityId, String modelName) {
        EntityData entity = entities.get(mcEntityId);
        if (entity != null) {
            if (entity.modelId == null) entity.modelId = modelName;
        } else {
            pendingModelNames.putIfAbsent(mcEntityId, modelName);
        }
    }

    public void setModelPath(int mcEntityId, String modelPath) {
        EntityData entity = entities.get(mcEntityId);
        if (entity != null) {
            if (entity.modelPath == null) entity.modelPath = modelPath;
        } else {
            pendingModelPaths.putIfAbsent(mcEntityId, modelPath);
        }
    }
    
//    public void setTranslation(int mcEntityId, float[] translation) {
//        EntityData entity = entities.get(mcEntityId);
//        if (entity != null) {
//            if (entity.translation == null) entity.translation = translation;
//        }
//    }
//    
//    public void setScale(int mcEntityId, float[] scale) {
//        EntityData entity = entities.get(mcEntityId);
//        if (entity != null) {
//            if (entity.scale == null) entity.scale = scale;
//        }
//    }
//    
//    public void setLeftRotation(int mcEntityId, float[] leftRotation) {
//        EntityData entity = entities.get(mcEntityId);
//        if (entity != null) {
//            if (entity.leftRotation == null) entity.leftRotation = leftRotation;
//        }
//    }
//    
//    public void setRightRotation(int mcEntityId, float[] rightRotation) {
//        EntityData entity = entities.get(mcEntityId);
//        if (entity != null) {
//            if (entity.rightRotation == null) entity.rightRotation = rightRotation;
//        }
//    }

    /**
     * Clear all entities
     */
    public void clear() {
        entities.clear();
        vehicleToPassengers.clear();
        passengerToVehicle.clear();
        pendingModelNames.clear();
        pendingModelPaths.clear();
        nextEntityId = 1;
    }
}
