package com.minecraftai.models;

import java.util.ArrayList;
import java.util.List;

/**
 * All entity changes that occurred at a single game tick.
 *
 * Only entities that had at least one change (position OR transform) at this
 * tick appear in the changes list. Unchanged entities are omitted — the
 * frontend holds the last known state and only updates what arrives.
 *
 * The ticks list in ParsedReplay should be sorted ascending by tick number
 * so the frontend can stream through them in order.
 */
public class TickData {

    /** Game tick number (20 ticks = 1 second). */
    public int tick;

    /**
     * All entity changes at this tick.
     * One TickChange per entity that changed — never more than one per entity per tick.
     */
    public List<TickChange> changes;

    public TickData() {
        this.changes = new ArrayList<>();
    }

    public TickData(int tick) {
        this.tick = tick;
        this.changes = new ArrayList<>();
    }
}
