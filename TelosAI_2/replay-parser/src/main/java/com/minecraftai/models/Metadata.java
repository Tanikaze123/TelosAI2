package com.minecraftai.models;

public class Metadata {
    public int duration;              // Total duration in ticks
    public String mcVersion;          // Minecraft version
    public String serverName;         // Server name
    public long recordedAt;           // Timestamp when recorded
    public String fileFormat;         // MCPR format version
    public int protocolVersion;       // Minecraft protocol version

    public Metadata() {}

    public Metadata(int duration, String mcVersion, String serverName, long recordedAt) {
        this.duration = duration;
        this.mcVersion = mcVersion;
        this.serverName = serverName;
        this.recordedAt = recordedAt;
    }
}
