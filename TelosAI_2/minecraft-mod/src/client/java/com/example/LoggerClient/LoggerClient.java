package com.example.LoggerClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.LoggerClient.util.EntityDataLogger;
import com.example.LoggerClient.util.EntityModel;
import com.example.LoggerClient.util.EntityRenderer;
import com.example.LoggerClient.util.KeybindingHandler;
import com.example.LoggerClient.util.APIHandler.BackendLink;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Main client initialization class for the Entity Logger mod.
 * This is the entry point that Fabric calls when the mod loads.
 */
public class LoggerClient implements ClientModInitializer {
    
    // Logger for debugging - shows up in the Minecraft log
    public static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");
    
    // Mod ID - used for identification
    public static final String MOD_ID = "modid";
    
    // Configuration - how far to scan for entities (in blocks)
    public static final double SCAN_RADIUS = 50.0;
    
    // How often to log (every N ticks, 20 ticks = 1 second)
    private static final int LOG_INTERVAL = 10; // Log twice per second
    private int tickCounter = 0;
    
    // Toggle for logging (Affects scanning of entities)
    private boolean loggingEnabled = true;
    
    // Helper classes
    private EntityDataLogger dataLogger;
    private EntityRenderer entityRenderer;
    private EntityModel entityModel;
    private KeybindingHandler keybindingHandler;
    private BackendLink backendLink;
    
    protected static final Minecraft MC = Minecraft.getInstance();
    
    /**
     * This method is called by Fabric when the client starts.
     * We use it to set up our mod's functionality.
     */
    @Override
    public void onInitializeClient() {
        LOGGER.info("Entity Logger mod initializing...");
        
        // Initialize our helper classes
        entityModel = new EntityModel();
        dataLogger = new EntityDataLogger(backendLink);
        entityRenderer = new EntityRenderer();
        backendLink = new BackendLink();
        keybindingHandler = new KeybindingHandler(entityModel, backendLink);
        
        
        // Register keybindings
        keybindingHandler.register();
        
        // Register our tick event - this runs every game tick
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        // Register rendering event - this runs every frame for visual overlay
        WorldRenderEvents.AFTER_ENTITIES.register(entityRenderer::render);
        
        LOGGER.info("Entity Logger mod initialized successfully!");
        LOGGER.info("Press 'M' to copy custom model data of entity you're looking at");
    }
    
    /**
     * Called every game tick (20 times per second).
     * We use this to scan for entities and log their data.
     * 
     * @param client The Minecraft client instance
     */
    private void onClientTick(Minecraft client) {
        // Safety check - make sure we're in a world and have a player
        if (client.level == null || client.player == null) {
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
    private void scanNearbyEntities(Minecraft client) {
    	entityRenderer.clearTrackedEntities();
    	
        LocalPlayer player = client.player;
        Vec3 playerPos = player.position();
        
        // Start logging session
        dataLogger.startLogSession();
        
        int entityCount = 0;
        
        // Iterate through all entities in the world
        for (Entity entity : client.level.entitiesForRendering()) {
            
            // Skip the player themselves
            if (entity == player) {
                continue;
            }
            
            // Calculate distance to entity
            Vec3 entityPos = entity.position();
            double distance = playerPos.distanceTo(entityPos);
            
            // Only process entities within our scan radius
            if (distance <= SCAN_RADIUS) {
                // Log this entity's data
                dataLogger.logEntity(entity, player, distance);
                entityCount++;
                
                
                
                // Add to renderer so we can see it visually
                entityRenderer.addTrackedEntity(entity, distance);
                
                // Get entity Model Data
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
    
    /**
     * Get the keybinding handler (for external access if needed)
     */
    public KeybindingHandler getKeybindingHandler() {
        return keybindingHandler;
    }
    
    /**
     * Get the backend link (for external access if needed)
     */
    public BackendLink getBackendLink() {
        return backendLink;
    }
    
    /**
     * Get the entity model handler (for external access if needed)
     */
    public EntityModel getEntityModel() {
        return entityModel;
    }
    
    public static Minecraft getInstance() {
    	return MC;
    }
}