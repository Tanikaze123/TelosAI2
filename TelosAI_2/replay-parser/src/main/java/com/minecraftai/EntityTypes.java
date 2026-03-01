package com.minecraftai;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft entity type registry for protocol 773 (MC 1.21.4/1.21.10).
 * Maps numeric type IDs from Add Entity packets to human-readable names.
 *
 * Source: https://wiki.vg/Entity_metadata#Entities (1.21.4)
 */
public class EntityTypes {
    private static final Map<Integer, String> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put(3, "area_effect_cloud");
        TYPE_MAP.put(5, "armor_stand");
        TYPE_MAP.put(6, "arrow");
        TYPE_MAP.put(15, "block_display");
        TYPE_MAP.put(45, "evoker"); //unknown use
        TYPE_MAP.put(68, "interaction");
        TYPE_MAP.put(70, "item");
        TYPE_MAP.put(71, "item_display");
        TYPE_MAP.put(82, "mannequin"); //unknown use
        TYPE_MAP.put(91, "painting"); //unknown use
        TYPE_MAP.put(95, "parrot"); //unknown use
        TYPE_MAP.put(100, "pillager"); //unknown use
        TYPE_MAP.put(114, "slime"); //unknown use
        TYPE_MAP.put(128, "text_display");
        TYPE_MAP.put(137, "vindicator"); //unknown use
        TYPE_MAP.put(145, "slime"); //unknown use
        TYPE_MAP.put(151, "player");
    }

    /**
     * Get the entity type name for a given type ID.
     * Returns "unknown_<id>" for unmapped IDs.
     * TODO: log unmapped ids.
     */
    public static String getTypeName(int typeId) {
        return TYPE_MAP.getOrDefault(typeId, "unknown_" + typeId);
    }

    /**
     * Check if a type ID is known.
     */
    public static boolean isKnown(int typeId) {
        return TYPE_MAP.containsKey(typeId);
    }
}
