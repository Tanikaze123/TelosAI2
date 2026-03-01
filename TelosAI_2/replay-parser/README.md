# Minecraft Replay Parser

Java-based parser for ReplayMod `.mcpr` files. Extracts entity data, timelines, and animations.

## Requirements

- Java 17 or higher
- Gradle 8.5+ (wrapper included)

## Build

```bash
./gradlew shadowJar
```

Output: `build/libs/replay-parser.jar`

## Usage

```bash
java -jar build/libs/replay-parser.jar <replay.mcpr>
```

Example:
```bash
java -jar build/libs/replay-parser.jar ~/.minecraft/replay_recordings/2026_02_14_15_30_45.mcpr
```

## Output

Outputs JSON to stdout:
```json
{
  "metadata": {
    "duration": 6000,
    "mcVersion": "1.21.10",
    "serverName": "Telos Realms",
    "recordedAt": 1708012800000,
    "fileFormat": "MCPR",
    "protocolVersion": 767
  },
  "entities": [
    {
      "id": "e_0001",
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "type": "armor_stand",
      "timeline": [
        {
          "tick": 0,
          "x": 100.5,
          "y": 64.0,
          "z": 200.5,
          "vx": 0.0,
          "vy": 0.0,
          "vz": 0.0,
          "yaw": 0.0,
          "pitch": 0.0
        }
      ]
    }
  ],
  "duration": 6000
}
```

## Integration with Backend

After building, copy JAR to backend:
```bash
cp build/libs/replay-parser.jar ../server-backend/src/parsers/
```

## Development

### Build and Test
```bash
# Build
./gradlew build

# Test with sample file
java -jar build/libs/replay-parser.jar test.mcpr

# Clean build
./gradlew clean build
```

### Project Structure
```
src/main/java/com/minecraftai/
├── ReplayParser.java        # Main entry point
├── PacketProcessor.java     # Process packets
├── EntityTracker.java       # Track entities over time
├── TimelineBuilder.java     # Build output structure
└── models/
    ├── ParsedReplay.java    # Output data model
    ├── EntityData.java      # Entity information
    ├── EntitySnapshot.java  # Position at tick
    └── Metadata.java        # Replay metadata
```

## Notes

**Current Implementation:**
- ✅ Basic .mcpr file reading
- ✅ Metadata extraction
- ✅ JSON output structure
- ⚠️ Packet decoding (basic placeholder)

**Phase 2 Enhancements:**
- Full packet decoding (spawn, position, metadata)
- ModelEngine entity detection
- Animation state extraction
- Particle tracking

The current version establishes the architecture and can be tested immediately.
Full packet decoding will be added after testing with actual .mcpr files.
