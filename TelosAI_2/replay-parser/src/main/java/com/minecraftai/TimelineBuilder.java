package com.minecraftai;

import com.minecraftai.models.CompositeEntity;
import com.minecraftai.models.EntityData;
import com.minecraftai.models.ParsedReplay;
import com.minecraftai.models.Metadata;

import java.util.List;

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
}
