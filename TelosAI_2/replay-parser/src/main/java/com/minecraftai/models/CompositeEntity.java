package com.minecraftai.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a ModelEngine composite entity - a group of sub-entities
 * (item_display, armor_stand, interaction, text_display, block_display)
 * that together form a single visual boss/mob model.
 *
 * ModelEngine spawns multiple entities parented via the Set Passengers packet.
 * The "root" is usually an interaction entity; the rest are bones/parts.
 */
public class CompositeEntity {

    /** Generated ID for this composite group, e.g. "composite_0001" */
    public String groupId;

    /**
     * The internal entity ID (e_XXXX) of the root entity.
     * Usually the interaction or armor_stand entity that other parts ride.
     */
    public String rootEntityId;

    /**
     * Type of the root entity (e.g. "interaction", "armor_stand").
     * This is the entity that "owns" the composite.
     */
    public String rootType;

    /**
     * Internal entity IDs (e_XXXX) of ALL sub-entities in this composite,
     * including the root. The rest are bones/display parts.
     */
    public List<String> partIds;

    /**
     * ModelEngine entity name extracted from the item_model component.
     * e.g. "golden_freddy" (from "modelengine:golden_freddy/head")
     * Populated by EntityMetadataReader after parsing entity_metadata packets.
     * Null if metadata parsing hasn't found an item_display part yet.
     */
    public String modelName;

    /**
     * Texture path for rendering in the frontend resource pack.
     * e.g. "modelengine:entity/golden_freddy"
     * → assets/modelengine/textures/entity/golden_freddy.png
     */
    public String texturePath;

    /** Tick when the root entity was first seen (spawn tick). */
    public int spawnTick;

    /** Spawn position of the root entity. */
    public double spawnX;
    public double spawnY;
    public double spawnZ;

    public CompositeEntity() {
        this.partIds = new ArrayList<>();
    }
}
