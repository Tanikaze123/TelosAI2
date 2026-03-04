package com.minecraftai;

import com.minecraftai.models.CompositeEntity;
import com.minecraftai.models.EntityData;
import com.minecraftai.models.ParsedReplay;
import com.minecraftai.models.Metadata;
import com.minecraftai.models.TickChange;
import com.minecraftai.models.TickData;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class TimelineBuilder {

	/**
	 * Build complete timeline from entity tracker
	 */
	public ParsedReplay buildTimeline(EntityTracker entityTracker, Metadata metadata, int duration) {
		ParsedReplay replay = new ParsedReplay();

		// Set metadata
		replay.metadata = metadata;
		replay.duration = duration;

		// Get all entities
		List<EntityData> entities = entityTracker.getAllEntities();
		replay.entities = entities;

		// Sort entities by ID for consistent output
		replay.entities.sort((a, b) -> a.id.compareTo(b.id));

		// Detect ModelEngine composite entities
		ModelEngineDetector detector = new ModelEngineDetector();
		List<CompositeEntity> composites = detector.detect(entityTracker);
		replay.composites = composites;
		System.err.println("Detected " + composites.size() + " ModelEngine composites");

		// Write groupId back onto each EntityData so the JSON entity objects carry it
		java.util.Map<String, EntityData> entityById = new java.util.HashMap<>();
		for (EntityData e : entities) entityById.put(e.id, e);
		for (CompositeEntity composite : composites) {
			for (String partId : composite.partIds) {
				EntityData e = entityById.get(partId);
				if (e != null) e.groupId = composite.groupId;
			}
		}

		return replay;
	}

	/**
	 * Filter out entities with minimal data
	 */
	public void filterEmptyEntities(ParsedReplay replay) {
		replay.entities.removeIf(entity -> entity.timeline == null || entity.timeline.isEmpty());
	}

	/**
	 * Merges all per-entity timelines into a tick-indexed list and writes it to
	 * replay.ticks.
	 */
	public void buildTickStream(ParsedReplay replay) {
		TreeMap<Integer, TickData> tickMap = new TreeMap<>();

		for (var entity : replay.entities) {
			for (var snapshot : entity.timeline) {
				var tickData = tickMap.computeIfAbsent(snapshot.tick, k -> new TickData(k));
				TickChange change = new TickChange();
				change.id = entity.id;
				change.x = snapshot.x;
				change.y = snapshot.y;
				change.z = snapshot.z;
				change.yaw = snapshot.yaw;
				change.pitch = snapshot.pitch;
				
				tickData.changes.add(change);
			}
			
			for (var transSnapshot : entity.transformTimeline) {
				var tickData = tickMap.computeIfAbsent(transSnapshot.tick, k -> new TickData(k));
				TickChange change = getOrCreateChange(tickData, entity.id);
				
				change.translation = transSnapshot.translation;
				change.scale = transSnapshot.scale;
				change.leftRotation = transSnapshot.leftRotation;
				change.rightRotation = transSnapshot.rightRotation;
			}
		}
		replay.ticks = new ArrayList<>(tickMap.values());
	}
	
	private TickChange getOrCreateChange(TickData tickData, String entityId) {
		//check if change is alr inside tickdata
		for (var change : tickData.changes) {
			if(change.id.equals(entityId)) return change;
		}
		//not inside tickdata
		TickChange change = new TickChange();
		change.id = entityId;
		tickData.changes.add(change);
		return change;
	}
}
