package com.minecraftai;

import java.util.HashMap;
import java.util.Map;

public class PacketTypes {
	private static final Map<Integer, String> TYPE_MAP = new HashMap<>();

	static {
		TYPE_MAP.put(0x01, "PKT_SPAWN_ENTITY");
		TYPE_MAP.put(0x33, "PKT_REL_ENTITY_MOVE");
		TYPE_MAP.put(0x34, "PKT_ENTITY_MOVE_LOOK");
		TYPE_MAP.put(0x36, "PKT_ENTITY_LOOK");
		TYPE_MAP.put(0x4B, "PKT_ENTITY_DESTROY");
		TYPE_MAP.put(0x7B, "PKT_ENTITY_TELEPORT");
		TYPE_MAP.put(0x23, "PKT_SYNC_ENTITY_POSITION");
		TYPE_MAP.put(0x69, "PKT_SET_PASSENGERS");  // Confirmed: proto.yml line 3084, position 105
		TYPE_MAP.put(0x61, "PKT_ENTITY_METADATA"); // Confirmed: proto.yml line 3076, position 97
	}

	/**
	 * Get the entity type name for a given type ID. Returns "unknown_<id>" for
	 * unmapped IDs. TODO: log unmapped ids.
	 */
	public static String getTypeName(int typeId) {
		return TYPE_MAP.getOrDefault(typeId, null);
	}

	/**
	 * Check if a type ID is known.
	 */
	public static boolean isKnown(int typeId) {
		return TYPE_MAP.containsKey(typeId);
	}
}
