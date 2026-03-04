package com.minecraftai.models;

import java.util.ArrayList;
import java.util.List;

public class ParsedReplay {
    public Metadata metadata;

    /**
     * Static entity registry — one entry per entity, containing only identity
     * and model info. Timelines have been moved to the ticks list below.
     *
     * Note: EntityData.timeline and EntityData.transformTimeline are marked
     * transient so Gson excludes them from serialization — they are only used
     * in memory during parsing to build the tick stream.
     */
    public List<EntityData> entities;

    public int duration;              // Convenience: same as metadata.duration

    /**
     * ModelEngine composite groups detected by ModelEngineDetector.
     * Each CompositeEntity references the internal IDs (e_XXXX) of its parts,
     * which can be cross-referenced against the entities list.
     *
     * Populated after buildTimeline() by calling ModelEngineDetector.detect().
     */
    public List<CompositeEntity> composites;

    /**
     * Tick-indexed stream of all entity state changes.
     * Sorted ascending by tick. Only ticks where at least one entity changed
     * are included. Frontend consumes this in order to animate the replay.
     */
    public List<TickData> ticks;

    public ParsedReplay() {
        this.metadata = new Metadata();
        this.entities = new ArrayList<>();
        this.composites = new ArrayList<>();
        this.ticks = new ArrayList<>();
    }
}
