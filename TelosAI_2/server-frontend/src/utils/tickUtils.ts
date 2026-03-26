import type { TickData } from "../types/replay";



export interface EntityState {
    tick: number;

    x?: number;
    y?: number;
    z?: number;
    yaw?: number;
    pitch?: number;

    translation?: number[];   // [x, y, z]
    scale?: number[];         // [x, y, z]
    leftRotation?: number[];  // [x, y, z, w]
    rightRotation?: number[]; // [x, y, z, w]
}

export function buildEntityTimelines(ticks: TickData[]): Map<string, EntityState[]> | null {
    const current = new Map<string, EntityState>();
    const timelines = new Map<string, EntityState[]>();

    for (const tickdata of ticks) {
        for (const change of tickdata.changes) {
            const prev: EntityState = current.get(change.id) ?? { tick: tickdata.tick };
            const next: EntityState = {
                tick: tickdata.tick,
                x: change.x ?? prev.x,
                y: change.y ?? prev.y,
                z: change.z ?? prev.z,
                yaw: change.yaw ?? prev.yaw,
                pitch: change.pitch ?? prev.pitch,
                translation: change.translation ?? prev.translation,
                scale: change.scale ?? prev.scale,
                leftRotation: change.leftRotation ?? prev.leftRotation,
                rightRotation: change.rightRotation ?? prev.rightRotation
            }

            current.set(change.id, next);

            const tl = timelines.get(change.id) ?? [];
            tl.push(next);
            timelines.set(change.id, tl);
        }
    }
    return timelines;
}

export function getStateAtTick(tl: EntityState[], targetTick: number): EntityState | null {
    if (!tl.length || tl[0].tick > targetTick) return null;
    let lo = 0, hi = tl.length - 1, result = 0;
    while (lo <= hi) {
        const mid = Math.floor((lo + hi) / 2);
        if (tl[mid].tick <= targetTick) {
            result = mid;
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return tl[result]
}