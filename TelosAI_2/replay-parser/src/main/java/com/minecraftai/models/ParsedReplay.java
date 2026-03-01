package com.minecraftai.models;

import java.util.ArrayList;
import java.util.List;

public class ParsedReplay {
    public Metadata metadata;
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

    public ParsedReplay() {
        this.metadata = new Metadata();
        this.entities = new ArrayList<>();
        this.composites = new ArrayList<>();
    }
}
