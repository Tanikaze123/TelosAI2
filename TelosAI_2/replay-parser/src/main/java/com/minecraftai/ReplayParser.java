package com.minecraftai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecraftai.models.Metadata;
import com.minecraftai.models.ParsedReplay;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ReplayParser {

    /**
     * Usage: java -jar replay-parser.jar <replay.mcpr> [output.json]
     *
     * If output.json is provided, JSON is written to that file (no stdout size limit).
     * If omitted, JSON is written to stdout (legacy mode, limited by maxBuffer).
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar replay-parser.jar <replay.mcpr> [output.json]");
            System.exit(1);
        }

        String filePath = args[0];
        String outputPath = args.length >= 2 ? args[1] : null;
        File replayFile = new File(filePath);

        if (!replayFile.exists()) {
            System.err.println("Error: File not found: " + filePath);
            System.exit(1);
        }

        if (!replayFile.getName().endsWith(".mcpr")) {
            System.err.println("Error: File must be a .mcpr file");
            System.exit(1);
        }

        try {
            ParsedReplay parsedReplay = parseReplay(replayFile);

            if (parsedReplay.entities != null) {
                parsedReplay.entities.forEach(e -> e.sanitizeTransforms());
            }

            // Compact JSON — no pretty printing saves ~3x space
            Gson gson = new GsonBuilder().create();
            String json = gson.toJson(parsedReplay);

            if (outputPath != null) {
                // Write to file — no buffer limits
                try (FileWriter fw = new FileWriter(outputPath)) {
                    fw.write(json);
                }
                System.err.println("Output written to: " + outputPath + " (" + json.length() + " bytes)");
            } else {
                System.out.println(json);
            }

        } catch (Exception e) {
            System.err.println("Error parsing replay: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Parse a .mcpr replay file
     */
    private static ParsedReplay parseReplay(File file) throws IOException {
        ParsedReplay replay = new ParsedReplay();

        // .mcpr files are ZIP archives
        try (ZipFile zipFile = new ZipFile(file)) {

            // Extract metadata from metaData.json
            ZipEntry metaDataEntry = zipFile.getEntry("metaData.json");
            if (metaDataEntry != null) {
                try (InputStreamReader reader = new InputStreamReader(zipFile.getInputStream(metaDataEntry))) {
                    JsonObject metaJson = JsonParser.parseReader(reader).getAsJsonObject();

                    Metadata metadata = new Metadata();
                    metadata.duration = metaJson.has("duration") ? metaJson.get("duration").getAsInt() : 0;
                    metadata.mcVersion = metaJson.has("mcversion") ? metaJson.get("mcversion").getAsString() : "unknown";
                    metadata.serverName = metaJson.has("serverName") ? metaJson.get("serverName").getAsString() : "unknown";
                    metadata.recordedAt = metaJson.has("date") ? metaJson.get("date").getAsLong() : System.currentTimeMillis();
                    metadata.fileFormat = "MCPR";
                    metadata.protocolVersion = metaJson.has("protocol") ? metaJson.get("protocol").getAsInt() : 0;

                    replay.metadata = metadata;
                    replay.duration = metadata.duration;
                }
            } else {
                throw new IOException("metaData.json not found in .mcpr file");
            }

            // Phase 2: Decode entity packets from recording.tmcpr
            ZipEntry tmcprEntry = zipFile.getEntry("recording.tmcpr");
            if (tmcprEntry != null) {
                System.err.println("Found recording.tmcpr (" + tmcprEntry.getSize() + " bytes)");

                PacketProcessor processor = new PacketProcessor();
                try (InputStream tmcprStream = zipFile.getInputStream(tmcprEntry)) {
                    processor.processReplay(tmcprStream);
                }

                processor.printPacketStats();

                // Build entity data from tracker
                TimelineBuilder builder = new TimelineBuilder();
                replay = builder.buildTimeline(processor.getEntityTracker(),
                    replay.metadata, processor.getMaxTick());
                builder.filterEmptyEntities(replay);

                // Merge per-entity timelines into tick stream, then discard them
                // (transient fields are already excluded from Gson output)
                builder.buildTickStream(replay);
            } else {
                System.err.println("Warning: recording.tmcpr not found in .mcpr file");
                System.err.println("Only metadata will be available");
            }

        } catch (Exception e) {
            throw new IOException("Failed to parse replay file", e);
        }

        return replay;
    }
}
