package com.minecraftai;

import com.minecraftai.PacketReader.RawPacket;
import com.minecraftai.models.EntityData;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Processes .tmcpr packet stream and extracts entity data.
 *
 * Protocol 773 (MC 1.21.4) clientbound play packet IDs. Packet IDs are logged
 * on each run so we can verify/adjust.
 */
public class PacketProcessor {
	private final EntityTracker entityTracker;
	private final EntityMetadataReader entityMetadataReader;
	private int maxTick = 0;
	private int totalPackets = 0;
	private int entityPackets = 0;

	// Protocol 773 clientbound play packet IDs
	private static final int PKT_SPAWN_ENTITY = 0x01;
	private static final int PKT_REL_ENTITY_MOVE = 0x33;
	private static final int PKT_ENTITY_MOVE_LOOK = 0x34;
	private static final int PKT_ENTITY_LOOK = 0x36;
	private static final int PKT_ENTITY_DESTROY = 0x4B;
	private static final int PKT_ENTITY_TELEPORT = 0x7B;
	private static final int PKT_SYNC_ENTITY_POSITION = 0x23;

	/**
	 * Packet format: VarInt vehicleEntityId, VarInt passengerCount, VarInt[]
	 * passengerIds ModelEngine fires this constantly during animation to
	 * attach/detach bone entities.
	 */
	private static final int PKT_SET_PASSENGERS = 0x69;

	/**
	 * Entity Metadata (0x61) — proto.yml line 3076, position 97. Carries item stack
	 * data for item_display entities, which contains the item_model component =
	 * ModelEngine model path (e.g. "modelengine:golden_freddy/head").
	 */
	private static final int PKT_ENTITY_METADATA = 0x61;

	// Track packet ID frequencies for debugging
	private final Map<Integer, Integer> packetIdCounts = new TreeMap<>();

	public PacketProcessor() {
		this.entityTracker = new EntityTracker();
		this.entityMetadataReader = new EntityMetadataReader();
	}

	/**
	 * Process all packets from a .tmcpr input stream
	 */
	public void processReplay(InputStream tmcprStream) throws IOException {
		PacketReader reader = new PacketReader(tmcprStream);

		while (reader.hasNext()) {
			RawPacket packet = reader.readNext();
			if (packet == null)
				break;
			if (packet.packetId < 0)
				continue;

			totalPackets++;
			maxTick = Math.max(maxTick, packet.getTick());

			// Count packet IDs
			packetIdCounts.merge(packet.packetId, 1, Integer::sum);

			// Process entity-related packets
			try {
				processPacket(packet);
			} catch (Exception e) {
				// Don't fail on individual packet errors
			}
		}

		reader.close();

		System.err.println("Processed " + totalPackets + " packets, " + entityPackets + " entity-related, "
				+ entityTracker.getAllEntities().size() + " unique entities");
	}

	private void processPacket(RawPacket packet) {
		switch (packet.packetId) {
		case PKT_SPAWN_ENTITY:
			handleAddEntity(packet);
			break;
		case PKT_REL_ENTITY_MOVE:
			handleMoveEntityPos(packet);
			break;
		case PKT_ENTITY_MOVE_LOOK:
			handleMoveEntityPosRot(packet);
			break;
		case PKT_ENTITY_LOOK:
			handleMoveEntityRot(packet);
			break;
		case PKT_ENTITY_TELEPORT:
			handleTeleportEntity(packet);
			break;
		case PKT_SYNC_ENTITY_POSITION:
			handleSyncEntityPos(packet);
			break;
		case PKT_ENTITY_DESTROY:
			handleRemoveEntities(packet);
			break;
		case PKT_SET_PASSENGERS:
			handleSetPassengers(packet);
			break;
		case PKT_ENTITY_METADATA:
			handleEntityMetadata(packet);
			break;
		}
	}

	/**
	 * Spawn Entity (0x01) VarInt entityId, UUID uuid, VarInt type, double x, double
	 * y, double z, byte pitch, byte yaw, byte headYaw, VarInt data, short vx, short
	 * vy, short vz
	 */
	private void handleAddEntity(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			UUID uuid = PacketReader.readUUID(data, cursor);
			int typeId = PacketReader.readVarIntFromBytes(data, cursor);
			double x = PacketReader.readDouble(data, cursor);
			double y = PacketReader.readDouble(data, cursor);
			double z = PacketReader.readDouble(data, cursor);
			float pitch = (PacketReader.readByte(data, cursor) * 360f) / 256f;
			float yaw = (PacketReader.readByte(data, cursor) * 360f) / 256f;

			String typeName = EntityTypes.getTypeName(typeId);

			entityTracker.trackEntity(entityId, uuid, typeName, packet.getTick(), x, y, z, yaw, pitch);
			entityPackets++;
		} catch (Exception e) {
			// Packet format mismatch
		}
	}

	/**
	 * Relative Entity Move (0x33) VarInt entityId, short dx, short dy, short dz,
	 * boolean onGround Deltas are in 1/4096 of a block
	 */
	private void handleMoveEntityPos(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			short dx = PacketReader.readShort(data, cursor);
			short dy = PacketReader.readShort(data, cursor);
			short dz = PacketReader.readShort(data, cursor);

			var entity = entityTracker.getEntity(entityId);
			if (entity != null && !entity.timeline.isEmpty()) {
				var last = entity.timeline.get(entity.timeline.size() - 1);
				double newX = last.x + (dx / 4096.0);
				double newY = last.y + (dy / 4096.0);
				double newZ = last.z + (dz / 4096.0);

				entityTracker.trackEntity(entityId, entity.uuid, entity.type, packet.getTick(), newX, newY, newZ,
						last.yaw, last.pitch);
				entityPackets++;
			}
		} catch (Exception e) {
			// Skip
		}
	}

	/**
	 * Entity Move + Look (0x34) VarInt entityId, short dx, short dy, short dz, byte
	 * yaw, byte pitch, boolean onGround
	 */
	private void handleMoveEntityPosRot(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			short dx = PacketReader.readShort(data, cursor);
			short dy = PacketReader.readShort(data, cursor);
			short dz = PacketReader.readShort(data, cursor);
			float yaw = (PacketReader.readByte(data, cursor) * 360f) / 256f;
			float pitch = (PacketReader.readByte(data, cursor) * 360f) / 256f;

			var entity = entityTracker.getEntity(entityId);
			if (entity != null && !entity.timeline.isEmpty()) {
				var last = entity.timeline.get(entity.timeline.size() - 1);
				double newX = last.x + (dx / 4096.0);
				double newY = last.y + (dy / 4096.0);
				double newZ = last.z + (dz / 4096.0);

				entityTracker.trackEntity(entityId, entity.uuid, entity.type, packet.getTick(), newX, newY, newZ, yaw,
						pitch);
				entityPackets++;
			}
		} catch (Exception e) {
			// Skip
		}
	}

	/**
	 * Entity Look (0x36) VarInt entityId, byte yaw, byte pitch, boolean onGround
	 */
	private void handleMoveEntityRot(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			float yaw = (PacketReader.readByte(data, cursor) * 360f) / 256f;
			float pitch = (PacketReader.readByte(data, cursor) * 360f) / 256f;

			var entity = entityTracker.getEntity(entityId);
			if (entity != null && !entity.timeline.isEmpty()) {
				var last = entity.timeline.get(entity.timeline.size() - 1);
				entityTracker.trackEntity(entityId, entity.uuid, entity.type, packet.getTick(), last.x, last.y, last.z,
						yaw, pitch);
				entityPackets++;
			}
		} catch (Exception e) {
			// Skip
		}
	}

	/**
	 * Entity Teleport (0x7B) VarInt entityId, double x, double y, double z, double
	 * vx, double vy, double vz, float yaw, float pitch, int relativeFlags, boolean
	 * onGround
	 */
	private void handleTeleportEntity(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			double x = PacketReader.readDouble(data, cursor);
			double y = PacketReader.readDouble(data, cursor);
			double z = PacketReader.readDouble(data, cursor);
			// Skip velocity (3 doubles)
			cursor[0] += 24;
			float yaw = PacketReader.readFloat(data, cursor);
			float pitch = PacketReader.readFloat(data, cursor);

			var entity = entityTracker.getEntity(entityId);
			if (entity != null) {
				entityTracker.trackEntity(entityId, entity.uuid, entity.type, packet.getTick(), x, y, z, yaw, pitch);
				entityPackets++;
			}
		} catch (Exception e) {
			// Skip
		}
	}

	/**
	 * Sync Entity Position (0x23) VarInt entityId, double x, double y, double z,
	 * double vx, double vy, double vz, float yaw, float pitch
	 */
	private void handleSyncEntityPos(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			double x = PacketReader.readDouble(data, cursor);
			double y = PacketReader.readDouble(data, cursor);
			double z = PacketReader.readDouble(data, cursor);
			// Skip velocity (3 doubles)
			cursor[0] += 24;
			float yaw = PacketReader.readFloat(data, cursor);
			float pitch = PacketReader.readFloat(data, cursor);

			var entity = entityTracker.getEntity(entityId);
			if (entity != null) {
				entityTracker.trackEntity(entityId, entity.uuid, entity.type, packet.getTick(), x, y, z, yaw, pitch);
				entityPackets++;
			}
		} catch (Exception e) {
			// Skip
		}
	}

	/**
	 * Entity Destroy (0x4B) VarInt count, VarInt[] entityIds
	 */
	private void handleRemoveEntities(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int count = PacketReader.readVarIntFromBytes(data, cursor);
			for (int i = 0; i < count && i < 1000; i++) {
				PacketReader.readVarIntFromBytes(data, cursor);
				entityPackets++;
			}
		} catch (Exception e) {
			// Skip
		}
	}

	/**
	 * Set Passengers (0x61 — verify ID!)
	 *
	 * Packet format: VarInt vehicleEntityId, VarInt passengerCount, VarInt[]
	 * passengerIds ModelEngine fires this constantly during animation to
	 * attach/detach bone entities. If passengerCount == 0, the vehicle has been
	 * emptied (all dismounted).
	 *
	 */
	private void handleSetPassengers(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			List<Integer> passList = new ArrayList<>();

			int vehicleId = PacketReader.readVarIntFromBytes(data, cursor);
			int passCount = PacketReader.readVarIntFromBytes(data, cursor);
			for (int i = 0; i < passCount && i < 1000; i++) {
				passList.add(PacketReader.readVarIntFromBytes(data, cursor));
			}

			entityTracker.setPassengers(vehicleId, passList);
			entityPackets++;
		} catch (Exception e) {
			// Skip malformed packets
		}
	}

	/**
	 * Entity Metadata (0x61)
	 *
	 * Only processes item_display entities (we need their item_model component).
	 * Uses EntityMetadataReader to scan metadata entries for type-7 (ItemStack),
	 * then extracts the item_model string to get the ModelEngine entity name.
	 *
	 * TODO: 1. Read entityId with readVarIntFromBytes 2. Look up the entity:
	 * tracker.getEntityByMcId(entityId) 3. If entity type != "item_display", return
	 * (skip non-display entities) 4. Create EntityMetadataReader, call
	 * extractItemModelPath(data, cursor) 5. If result non-null: a. entityName =
	 * EntityMetadataReader.extractEntityName(result) b.
	 * tracker.setModelName(entityId, entityName) 6. Increment entityPackets
	 *
	 * Note: implement EntityMetadataReader.extractItemModelPath() and
	 * readItemModelFromSlot() first — they contain the hard parsing logic. You'll
	 * also need the component type ID for "item_model" from protocol.json.
	 */
	private void handleEntityMetadata(RawPacket packet) {
		try {
			int[] cursor = { packet.dataOffset };
			byte[] data = packet.data;

			int entityId = PacketReader.readVarIntFromBytes(data, cursor);
			EntityData entity = entityTracker.getEntityByMcId(entityId);
			// If entity exists and is not item_display, skip (not a bone).
			// If entity is null, scan anyway — spawn packet may arrive later;
			// EntityTracker.setModelName() stores it as a pending entry.
			if (entity != null && !"item_display".equals(entity.type)) {
				return;
			}
			if (entity != null) {
				entityMetadataReader.readTransforms(data, cursor, entity);
				entity.recordTransform(packet.getTick());
			}
			String entityName = EntityMetadataReader.scanForModelName(data, packet.dataOffset);
			if (entityName == null) return;
			entityTracker.setModelName(entityId, entityName);
			String modelPath = EntityMetadataReader.scanForModelPath(data, packet.dataOffset);
			if (modelPath != null) entityTracker.setModelPath(entityId, modelPath);
			
			
			entityPackets++;
		} catch (Exception e) {
			// Skip malformed packets
			System.err.println("metadata err: " + e.getMessage());
		}
	}

	public EntityTracker getEntityTracker() {
		return entityTracker;
	}

	public int getMaxTick() {
		return maxTick;
	}

	public int getTotalPackets() {
		return totalPackets;
	}

	/**
	 * Print top packet IDs for debugging
	 */
	public void printPacketStats() {
		System.err.println("\n=== Packet Statistics ===");
		System.err.println("Total packets: " + totalPackets);
		System.err.println("Entity packets: " + entityPackets);
		System.err.println("Unique entities: " + entityTracker.getAllEntities().size());
		System.err.println("Duration: " + maxTick + " ticks (" + (maxTick / 20) + "s)");
		System.err.println("\nTop 20 packet IDs:");

		packetIdCounts.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()).limit(20)
				.forEach(entry -> {
					String name = PacketTypes.getTypeName(entry.getKey());
					System.err.printf("  0x%02X: %,d packets (%s) %n", entry.getKey(), entry.getValue(), name);
				});
	}
}
