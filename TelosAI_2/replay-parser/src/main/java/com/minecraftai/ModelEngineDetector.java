package com.minecraftai;

import com.minecraftai.models.CompositeEntity;
import com.minecraftai.models.EntityData;
import com.minecraftai.models.EntitySnapshot;

import java.util.*;

/**
 * Detects ModelEngine composite entities from parsed replay data.
 *
 * ModelEngine works by spawning a group of sub-entities (item_display,
 * armor_stand, interaction, text_display, block_display) and linking them via
 * the Set Passengers packet. This class reconstructs those groups.
 *
 * Two detection strategies (use whichever works best): 1. PASSENGER-BASED: Use
 * vehicle→passenger relationships tracked in EntityTracker. A composite is one
 * vehicle + all its passengers (recursively). More accurate, requires Set
 * Passengers packet to be decoded.
 *
 * 2. PROXIMITY-BASED (fallback): Group entities that spawn within ±2 ticks of
 * each other AND within ~1.5 blocks (x/z distance) of each other. Less accurate
 * but works without passenger data.
 *
 * Usage (from TimelineBuilder after buildTimeline): ModelEngineDetector
 * detector = new ModelEngineDetector(); List<CompositeEntity> composites =
 * detector.detect(entityTracker);
 */
public class ModelEngineDetector {

	/**
	 * Entity types that ModelEngine uses as sub-parts. Interaction is typically the
	 * root; the display types are bones.
	 */
	private static final Set<String> ME_TYPES = new HashSet<>(
			Arrays.asList("interaction", "item_display", "text_display", "block_display", "armor_stand", "mannequin"));

	/**
	 * Max tick difference between two entities to be considered the same composite
	 * (proximity method).
	 */
	private static final int MAX_SPAWN_TICK_DIFF = 2;

	/**
	 * Max x/z distance (blocks) for two entities to be in the same composite
	 * (proximity method).
	 */
	private static final double MAX_SPAWN_DISTANCE = 1.5;

	// -----------------------------------------------------------------------
	// Passenger-based detection
	// -----------------------------------------------------------------------

	/**
	 * Detect composites using vehicle→passenger relationships from EntityTracker.
	 *
	 * @param tracker The EntityTracker after all packets are processed.
	 * @return List of detected composites.
	 */
	public List<CompositeEntity> detectByPassengers(EntityTracker tracker) {
		List<CompositeEntity> composites = new ArrayList<>();

		Map<Integer, List<Integer>> passMap = tracker.getVehicleToPassengersMap();
		for (Map.Entry<Integer, List<Integer>> entry : passMap.entrySet()) {
			int vehId = entry.getKey();
			List<Integer> passengerIds = entry.getValue();

			EntityData entity = tracker.getEntityByMcId(vehId);
			if (entity == null) continue;

			// Accept any vehicle type as root, as long as at least one direct passenger
			// is a ME display type. This handles area_effect_cloud, mobs, etc. as roots.
			boolean hasDisplayPassenger = false;
			for (int pid : passengerIds) {
				EntityData p = tracker.getEntityByMcId(pid);
				if (p != null && isMEType(p.type)) { hasDisplayPassenger = true; break; }
			}
			if (!hasDisplayPassenger) continue;
			List<String> partIds = new ArrayList<>();
			collectParts(vehId, passMap, tracker, partIds);
			
			if (partIds.size() < 2) continue;

			CompositeEntity composite = new CompositeEntity();
			composite.rootEntityId = entity.id;
			composite.rootType = entity.type;
			composite.partIds = partIds;

			// Spawn position from first snapshot
			if (!entity.timeline.isEmpty()) {
			    composite.spawnTick = entity.timeline.get(0).tick;
			    composite.spawnX = entity.timeline.get(0).x;
			    composite.spawnY = entity.timeline.get(0).y;
			    composite.spawnZ = entity.timeline.get(0).z;
			}
			
			composites.add(composite);
		}
		return composites;
	}

	private void collectParts(int mcId, Map<Integer, List<Integer>> passMap, EntityTracker tracker,
			List<String> partIds) {
		EntityData e = tracker.getEntityByMcId(mcId);
		if (e != null)
			partIds.add(e.id); // add the e_XXXX internal ID

		List<Integer> children = passMap.get(mcId);
		if (children != null) {
			for (int childId : children) {
				collectParts(childId, passMap, tracker, partIds); // recurse
			}
		}
	}

	// -----------------------------------------------------------------------
	// Proximity-based detection (fallback) TBD
	// -----------------------------------------------------------------------

	/**
	 * Detect composites by grouping entities that spawn near each other in time and
	 * space.
	 *
	 * @param tracker The EntityTracker after all packets are processed.
	 * @return List of detected composites.
	 */
	public List<CompositeEntity> detectByProximity(EntityTracker tracker) {
		List<CompositeEntity> composites = new ArrayList<>();
		// TODO: implement proximity-based detection
		return composites;
	}

	// -----------------------------------------------------------------------
	// Main entry point - calls both strategies and merges results
	// -----------------------------------------------------------------------

	/**
	 * Run detection. Tries passenger-based first; falls back to proximity if
	 * passenger data is empty.
	 *
	 * @param tracker The EntityTracker after all packets are processed.
	 * @return List of detected CompositeEntity groups.
	 */
	public List<CompositeEntity> detect(EntityTracker tracker) {
		List<CompositeEntity> composites = detectByPassengers(tracker);
		
		if (composites.isEmpty()) {
	        composites = detectByProximity(tracker);
	        System.err.println("detectByPassengers Empty");
	    }
		
		//give each modelengine entity group ids
		for (int i = 0; i < composites.size(); i++) {
	        composites.get(i).groupId = "composite_" + String.format("%04d", i + 1);
	    }
		
		return composites;
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	/**
	 * Calculates horizontal (x/z only) distance between two snapshots. Used by
	 * proximity detection. Ignore Y — ME parts have different Y offsets (nameplate
	 * above, hitbox below, etc.).
	 */
	private double xzDistance(EntitySnapshot a, EntitySnapshot b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Checks if an entity type is a ModelEngine sub-part type.
	 */
	private boolean isMEType(String type) {
		return type != null && ME_TYPES.contains(type);
	}
}
