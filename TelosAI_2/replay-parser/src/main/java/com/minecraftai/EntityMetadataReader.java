package com.minecraftai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.minecraftai.models.EntityData;

/**
 * Parses entity_metadata packets (0x61) to extract model/texture info from
 * item_display entities.
 *
 * Entity metadata format: VarInt entityId Loop until index == 0xFF: VarInt
 * index VarInt type value (size depends on type)
 *
 * For item_display entities, we scan for a type-7 (ItemStack) entry, read the
 * item's components, and look for the item_model component. That component
 * contains a string like "modelengine:golden_freddy/head" from which we extract
 * the entity name "golden_freddy".
 */
public class EntityMetadataReader {

	// Metadata end sentinel
	private static final int META_END = 0xFF;

	// Item component type IDs (confirmed from proto.yml)
	// custom_data=0 at line 69, item_model=7 at line 76 → 76-69=7
	private static final int COMPONENT_ITEM_MODEL = 7; // resource location string: "modelengine:name/part"

	// Metadata value types (protocol 773)
	private static final int TYPE_BYTE = 0;
	private static final int TYPE_VARINT = 1;
	private static final int TYPE_VARLONG = 2;
	private static final int TYPE_FLOAT = 3;
	private static final int TYPE_STRING = 4;
	private static final int TYPE_TEXT_COMP = 5; // NbtCompound (skip as NBT)
	private static final int TYPE_OPT_TEXT = 6; // boolean + optional TYPE_TEXT_COMP
	private static final int TYPE_SLOT = 7; // ItemStack — this is what we want
	private static final int TYPE_BOOLEAN = 8;
	private static final int TYPE_ROTATIONS = 9; // 3 floats
	private static final int TYPE_BLOCKPOS = 10; // Long (8 bytes)
	private static final int TYPE_OPT_BLOCKPOS = 11; // boolean + optional Long
	private static final int TYPE_DIRECTION = 12; // VarInt
	private static final int TYPE_OPT_UUID = 13; // boolean + optional 16 bytes
	private static final int TYPE_BLOCKSTATE = 14; // VarInt
	private static final int TYPE_OPT_BLOCKSTATE = 15; // VarInt
	private static final int TYPE_PARTICLE = 16; // complex — skip (too variable)
	private static final int TYPE_PARTICLES = 17; // VarInt count + TYPE_PARTICLE[]
	private static final int TYPE_VILLAGER = 18; // 3 VarInts
	private static final int TYPE_OPT_VARINT = 19; // VarInt (0 = absent) — confirmed line 1042
	private static final int TYPE_POSE = 20; // VarInt — line 1043 (after opt_varint)
	// Types 21-27: VarInts (cat, wolf, frog, painting variants, global_pos,
	// sniffer, armadillo)
	private static final int TYPE_VECTOR3 = 34; // 3 floats - proto.yml 1.21.9 (1.21.10 same)
	private static final int TYPE_QUATERNION = 35; // 4 floats - proto.yml 1.21.9 (1.21.10 same)

	/**
	 * Reads the entity ID from the start of a metadata packet.
	 */
	public int readEntityId(byte[] data, int[] cursor) {
		return PacketReader.readVarIntFromBytes(data, cursor);
	}

	/**
	 * Scans metadata entries for display entity related data and returns model
	 * render data. Only call this for item_display entities.
	 */
	public void readTransforms(byte[] data, int[] cursor, EntityData entity) {
		// metadata scan loop
		// Loop: read index, if 0xFF (end) break, read type, if TYPE_SLOT call
		// readItemModelFromSlot
		while (cursor[0] < data.length) {
			// int index = PacketReader.readVarIntFromBytes(data, cursor);
			int index = data[cursor[0]++] & 0xFF;
			if (index == META_END)
				break;
			int type = PacketReader.readVarIntFromBytes(data, cursor);

			// Looking at Display Entity Metadata here
			switch (index) {
			case 11: // vector 3 translation — type must be TYPE_VECTOR3 (35)
				if (type == TYPE_VECTOR3) {
					entity.translation = new float[] { PacketReader.readFloatFromBytes(data, cursor),
							PacketReader.readFloatFromBytes(data, cursor), PacketReader.readFloatFromBytes(data, cursor) };
				} else {
					if (!skipMetadataValue(type, data, cursor)) return;
				}
				break;
			case 12: // vector 3 scale — type must be TYPE_VECTOR3 (35)
				if (type == TYPE_VECTOR3) {
					entity.scale = new float[] { PacketReader.readFloatFromBytes(data, cursor),
							PacketReader.readFloatFromBytes(data, cursor), PacketReader.readFloatFromBytes(data, cursor) };
				} else {
					if (!skipMetadataValue(type, data, cursor)) return;
				}
				break;
			case 13: // quaternion left rotation — type must be TYPE_QUATERNION (36)
				if (type == TYPE_QUATERNION) {
					entity.leftRotation = new float[] { PacketReader.readFloatFromBytes(data, cursor),
							PacketReader.readFloatFromBytes(data, cursor), PacketReader.readFloatFromBytes(data, cursor),
							PacketReader.readFloatFromBytes(data, cursor) };
				} else {
					if (!skipMetadataValue(type, data, cursor)) return;
				}
				break;
			case 14: // quaternion right rotation — type must be TYPE_QUATERNION (36)
				if (type == TYPE_QUATERNION) {
					entity.rightRotation = new float[] { PacketReader.readFloatFromBytes(data, cursor),
							PacketReader.readFloatFromBytes(data, cursor), PacketReader.readFloatFromBytes(data, cursor),
							PacketReader.readFloatFromBytes(data, cursor) };
				} else {
					if (!skipMetadataValue(type, data, cursor)) return;
				}
				break;

			default:
				if (!skipMetadataValue(type, data, cursor))
					return;
			}
		}
	}

	/**
	 * Reads an ItemStack from the current cursor position and extracts the
	 * item_model component string.
	 *
	 * ItemStack format (protocol 773): VarInt count — if 0, empty stack (return
	 * null) VarInt itemTypeId — registry ID of the item (e.g. minecraft:paper)
	 * VarInt numAdded — number of added/overridden components For each added
	 * component: VarInt componentTypeId value (depends on component type) VarInt
	 * numRemoved — number of removed components (just VarInts, skip) For each
	 * removed: VarInt componentTypeId
	 *
	 * Component type for item_model: TODO: look up "item_model" in protocol.json
	 * components registry. It's a resource location string: VarInt length + UTF-8
	 * bytes. The value looks like: "modelengine:golden_freddy/head"
	 *
	 * Steps: 1. Read count. If 0, return null. 2. Read itemTypeId (skip, we don't
	 * need it). 3. Read numAdded. 4. For each added component: a. Read
	 * componentTypeId. b. If componentTypeId == COMPONENT_ITEM_MODEL: - Read the
	 * resource location string - Return it c. Otherwise: call
	 * skipComponent(componentTypeId, data, cursor) 5. Read numRemoved, skip each
	 * (just VarInts). 6. Return null if item_model component not found.
	 */
	public String readItemModelFromSlot(byte[] data, int[] cursor) {
		int count = PacketReader.readVarIntFromBytes(data, cursor);
		if (count == 0)
			return null;

		PacketReader.readVarIntFromBytes(data, cursor); // itemTypeId (unused)
		int numAdded = PacketReader.readVarIntFromBytes(data, cursor);

		for (int i = 0; i < numAdded; i++) {
			int componentTypeId = PacketReader.readVarIntFromBytes(data, cursor);
			if (componentTypeId == COMPONENT_ITEM_MODEL) {
				int len = PacketReader.readVarIntFromBytes(data, cursor);
				String result = new String(data, cursor[0], len, StandardCharsets.UTF_8);
				cursor[0] += len;
				return result;
			} else if (componentTypeId == 0) {
				// custom_data — NBT compound, scan raw bytes for "modelengine:" string
				return extractModelenginePathFromCustomData(data, cursor[0]);
			} else {
				if (!skipComponent(componentTypeId, data, cursor))
					return null;
			}
		}

		int numRemoved = PacketReader.readVarIntFromBytes(data, cursor);
		for (int i = 0; i < numRemoved; i++) {
			PacketReader.readVarIntFromBytes(data, cursor);
		}

		return null;
	}

	/**
	 * Scans the full packet byte range for any "modelengine:name/part" strings.
	 * Returns the first entity name that does NOT start with "internal_". Falls
	 * back to the first "internal_" name if nothing else is found. Returns null if
	 * no "modelengine:" strings are present at all.
	 */
	public static String scanForModelName(byte[] data, int fromOffset) {
		byte[] prefix = "modelengine:".getBytes(StandardCharsets.UTF_8);
		String firstInternal = null;
		for (int i = fromOffset; i <= data.length - prefix.length; i++) {
			boolean match = true;
			for (int j = 0; j < prefix.length; j++) {
				if (data[i + j] != prefix[j]) {
					match = false;
					break;
				}
			}
			if (match) {
				int end = i + prefix.length;
				while (end < data.length && data[end] >= 0x20 && data[end] < 0x7F)
					end++;
				String path = new String(data, i, end - i, StandardCharsets.UTF_8);
				String name = extractEntityName(path);
				if (name == null) {
					i = end - 1;
					continue;
				}
				if (!name.startsWith("internal_"))
					return name;
				if (firstInternal == null)
					firstInternal = name;
				i = end - 1;
			}
		}
		return firstInternal;
	}

	/**
	 * Same scan as scanForModelName but returns the full resource location string,
	 * e.g. "modelengine:jump_pad_sky/right_mid_wing_tip". Skips "internal_" paths,
	 * falls back to first internal if nothing else found.
	 */
	public static String scanForModelPath(byte[] data, int fromOffset) {
		byte[] prefix = "modelengine:".getBytes(StandardCharsets.UTF_8);
		String firstInternalPath = null;
		for (int i = fromOffset; i <= data.length - prefix.length; i++) {
			boolean match = true;
			for (int j = 0; j < prefix.length; j++) {
				if (data[i + j] != prefix[j]) {
					match = false;
					break;
				}
			}
			if (match) {
				int end = i + prefix.length;
				while (end < data.length && data[end] >= 0x20 && data[end] < 0x7F)
					end++;
				String full = new String(data, i, end - i, StandardCharsets.UTF_8);
				String name = extractEntityName(full);
				if (name == null) {
					i = end - 1;
					continue;
				}
				if (!name.startsWith("internal_"))
					return full;
				if (firstInternalPath == null)
					firstInternalPath = full;
				i = end - 1;
			}
		}
		return firstInternalPath;
	}

	private String extractModelenginePathFromCustomData(byte[] data, int start) {
		byte[] prefix = "modelengine:".getBytes(StandardCharsets.UTF_8);
		List<String> results = new ArrayList<>();
		for (int i = start; i <= data.length - prefix.length; i++) {
			boolean match = true;
			for (int j = 0; j < prefix.length; j++) {
				if (data[i + j] != prefix[j]) {
					match = false;
					break;
				}
			}
			if (match) {
				int end = i;
				while (end < data.length && data[end] >= 0x20 && data[end] < 0x7F)
					end++;
				results.add(new String(data, i, end - i, StandardCharsets.UTF_8));
				i = end - 1; // skip past this match
			}
		}
		return results.isEmpty() ? null : String.join(" | ", results);
	}

	private boolean skipComponent(int componentTypeId, byte[] data, int[] cursor) {
		switch (componentTypeId) {
		case 7: // item_model — resource location string (VarInt len + bytes)
			int len = PacketReader.readVarIntFromBytes(data, cursor);
			cursor[0] += len;
			return true;
		default:
			return false;
		}
	}

	/**
	 * Skips past a metadata value of the given type, advancing the cursor. Required
	 * so we can iterate through entries to find what we need.
	 *
	 * TODO: implement all cases. Most are straightforward fixed sizes. NBT (type
	 * 16) and Particle (type 17) are the hardest — skip for now by returning false
	 * (caller should abort on those).
	 *
	 * Returns true if successfully skipped, false if type is unrecognised or too
	 * complex to skip (NBT, Particle).
	 */
	public boolean skipMetadataValue(int type, byte[] data, int[] cursor) {
		switch (type) {
		case TYPE_BYTE:
		case TYPE_BOOLEAN:
			cursor[0] += 1;
			return true;
		case TYPE_VARINT:
		case TYPE_DIRECTION:
		case TYPE_BLOCKSTATE:
		case TYPE_OPT_BLOCKSTATE:
		case TYPE_POSE:
		case TYPE_OPT_VARINT:
			PacketReader.readVarIntFromBytes(data, cursor);
			return true;
		case TYPE_VILLAGER: // 3 VarInts: type, profession, level
			PacketReader.readVarIntFromBytes(data, cursor);
			PacketReader.readVarIntFromBytes(data, cursor);
			PacketReader.readVarIntFromBytes(data, cursor);
			return true;
		case TYPE_FLOAT:
			cursor[0] += 4;
			return true;
		case TYPE_VARLONG:
		case TYPE_BLOCKPOS:
			cursor[0] += 8;
			return true;
		case TYPE_STRING:
		case TYPE_TEXT_COMP: {
			// VarInt length + bytes
			int len = PacketReader.readVarIntFromBytes(data, cursor);
			cursor[0] += len;
			return true;
		}
		case TYPE_OPT_TEXT: {
			// boolean, then optional string
			boolean present = data[cursor[0]++] != 0;
			if (present) {
				int len = PacketReader.readVarIntFromBytes(data, cursor);
				cursor[0] += len;
			}
			return true;
		}
		case TYPE_ROTATIONS:
		case TYPE_VECTOR3:
			cursor[0] += 12; // 3 floats
			return true;
		case TYPE_QUATERNION:
			cursor[0] += 16; // 4 floats
			return true;
		case TYPE_OPT_BLOCKPOS: {
			boolean present = data[cursor[0]++] != 0;
			if (present)
				cursor[0] += 8;
			return true;
		}
		case TYPE_OPT_UUID: {
			boolean present = data[cursor[0]++] != 0;
			if (present)
				cursor[0] += 16;
			return true;
		}
		case TYPE_SLOT:
			// ItemStack — complex. For skipping, read and discard.
			readItemModelFromSlot(data, cursor);
			return true;
		case TYPE_PARTICLE:
		case TYPE_PARTICLES:
			// Too complex to skip reliably without full NBT/particle parsers
			return false;
		default:
			return false;
		}
	}

	/**
	 * Parses the ModelEngine entity name from an item_model path string.
	 *
	 * Input: "modelengine:golden_freddy/head" Output: "golden_freddy"
	 *
	 * Pattern: everything after ":" and before the first "/"
	 */
	public static String extractEntityName(String itemModelPath) {
		if (itemModelPath == null)
			return null;
		int colonIdx = itemModelPath.indexOf(':');
		int slashIdx = itemModelPath.indexOf('/');
		if (colonIdx < 0 || slashIdx < 0 || slashIdx <= colonIdx)
			return null;
		return itemModelPath.substring(colonIdx + 1, slashIdx);
	}

	/**
	 * Converts an entity name to the texture path used in the resource pack.
	 *
	 * Input: "golden_freddy" Output: "modelengine:entity/golden_freddy" →
	 * assets/modelengine/textures/entity/golden_freddy.png
	 */
	public static String entityNameToTexturePath(String entityName) {
		if (entityName == null)
			return null;
		return "modelengine:entity/" + entityName;
	}
}
