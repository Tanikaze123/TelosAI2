package com.example.LoggerClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.LoggerClient.util.EntityDataLogger;
import com.example.LoggerClient.util.EntityRenderer;

/**
 * Main client initialization class for the Entity Logger mod.
 * This is the entry point that Fabric calls when the mod loads.
 */
public class LoggerClient implements ClientModInitializer {
    
    // Logger for debugging - shows up in the Minecraft log
    public static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");
    
    // Mod ID - used for identification
    public static final String MOD_ID = "entitylogger";
    
    // Configuration - how far to scan for entities (in blocks)
    public static final double SCAN_RADIUS = 50.0;
    
    // How often to log (every N ticks, 20 ticks = 1 second)
    private static final int LOG_INTERVAL = 10; // Log twice per second
    private int tickCounter = 0;
    
    // Toggle for logging
    private boolean loggingEnabled = true;
    
    // Helper classes
    private EntityDataLogger dataLogger;
    private EntityRenderer entityRenderer;
    
    /**
     * This method is called by Fabric when the client starts.
     * We use it to set up our mod's functionality.
     */
    @Override
    public void onInitializeClient() {
        LOGGER.info("Entity Logger mod initializing...");
        
        // Initialize our helper classes
        dataLogger = new EntityDataLogger();
        entityRenderer = new EntityRenderer();
        
        // Register our tick event - this runs every game tick
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        // Register rendering event - this runs every frame for visual overlay
        WorldRenderEvents.AFTER_ENTITIES.register(entityRenderer::render);
        
        LOGGER.info("Entity Logger mod initialized successfully!");
    }
    
    /**
     * Called every game tick (20 times per second).
     * We use this to scan for entities and log their data.
     * 
     * @param client The Minecraft client instance
     */
    private void onClientTick(MinecraftClient client) {
        // Safety check - make sure we're in a world and have a player
        if (client.world == null || client.player == null) {
            return;
        }
        
//        // Only log every LOG_INTERVAL ticks to avoid spam
        tickCounter++;
        if (tickCounter < LOG_INTERVAL) {
            return;
        }
        tickCounter = 0;
        
        // Only log if enabled
        if (!loggingEnabled) {
            return;
        }
        
        // Scan for entities near the player
        scanNearbyEntities(client);
    }
    
    /**
     * Scans for all entities within SCAN_RADIUS of the player
     * and logs their data.
     * 
     * @param client The Minecraft client instance
     */
    private void scanNearbyEntities(MinecraftClient client) {
    	entityRenderer.clearTrackedEntities();
    	
        PlayerEntity player = client.player;
        Vec3d playerPos = player.getPos();
        
        // Start logging session
        dataLogger.startLogSession();
        
        int entityCount = 0;
        
        // Iterate through all entities in the world
        for (Entity entity : client.world.getEntities()) {
            
            // Skip the player themselves
            if (entity == player) {
                continue;
            }
            
            // Calculate distance to entity
            Vec3d entityPos = entity.getPos();
            double distance = playerPos.distanceTo(entityPos);
            
            // Only process entities within our scan radius
            if (distance <= SCAN_RADIUS) {
                // Log this entity's data
                dataLogger.logEntity(entity, player, distance);
                entityCount++;
                
                // Add to renderer so we can see it visually
                entityRenderer.addTrackedEntity(entity, distance);
            }
        }
        
        // Finish logging session
        dataLogger.endLogSession(entityCount);
    }
    
    /**
     * Toggle logging on/off
     */
    public void toggleLogging() {
        loggingEnabled = !loggingEnabled;
        LOGGER.info("Logging " + (loggingEnabled ? "enabled" : "disabled"));
    }
    
    /**
     * Check if logging is enabled
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }
}