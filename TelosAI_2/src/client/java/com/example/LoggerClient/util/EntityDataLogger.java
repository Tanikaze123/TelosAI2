package com.example.LoggerClient.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles logging entity data to both the console and to files.
 * This class creates detailed logs of entity information for analysis.
 */
public class EntityDataLogger {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");
    
    // File logging
    private BufferedWriter fileWriter;
    private File logFile;
    private boolean fileLoggingEnabled = true;
    private boolean consoleLoggingEnabled = false;
    
    // Formatting
    private static final DateTimeFormatter TIME_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Constructor - sets up the log file
     */
    public EntityDataLogger() {
        setupLogFile();
    }
    
    /**
     * Creates a log file in the Minecraft directory
     */
    private void setupLogFile() {
        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("entitylogger");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            // Create a new log file with timestamp
            String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            );
            logFile = new File(logsDir, "entities_" + timestamp + ".log");
            fileWriter = new BufferedWriter(new FileWriter(logFile, true));
            
            LOGGER.info("Created log file: " + logFile.getAbsolutePath());
            
            // Write header
            writeToFile("=== Entity Logger Started ===");
            writeToFile("Timestamp: " + LocalDateTime.now().format(TIME_FORMAT));
            writeToFile("============================\n");
            
        } catch (IOException e) {
            LOGGER.error("Failed to create log file", e);
            fileLoggingEnabled = false;
        }
    }
    
    /**
     * Called at the start of each logging cycle
     */
    public void startLogSession() {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        writeToFile("\n--- Scan at " + timestamp + " ---");
    }
    
    /**
     * Logs detailed information about a single entity
     * 
     * @param entity The entity to log
     * @param player The player (for reference)
     * @param distance Distance from player to entity
     */
    public void logEntity(Entity entity, PlayerEntity player, double distance) {
        
        // Build the log message with all entity data
        StringBuilder log = new StringBuilder();
        
        // Basic identification
        log.append("Entity: ").append(entity.getName().getString()).append("\n");
        log.append("  Type: ").append(entity.getType().toString()).append("\n");
        log.append("  UUID: ").append(entity.getUuidAsString()).append("\n");
        log.append("  Entity ID: ").append(entity.getId()).append("\n");
        
        // Position data
        Vec3d pos = entity.getPos();
        log.append("  Position: ")
           .append(String.format("X=%.2f, Y=%.2f, Z=%.2f", pos.x, pos.y, pos.z))
           .append("\n");
        
        // Distance from player
        log.append("  Distance from player: ")
           .append(String.format("%.2f blocks", distance))
           .append("\n");
        
        // Velocity (movement vector)
        Vec3d velocity = entity.getVelocity();
        double speed = velocity.length(); // Total speed
        log.append("  Velocity: ")
           .append(String.format("X=%.3f, Y=%.3f, Z=%.3f (Speed: %.3f)", 
                   velocity.x, velocity.y, velocity.z, speed))
           .append("\n");
        
        // Rotation (yaw and pitch)
        log.append("  Rotation: ")
           .append(String.format("Yaw=%.1f°, Pitch=%.1f°", 
                   entity.getYaw(), entity.getPitch()))
           .append("\n");
        
        // Bounding box (hitbox size)
        log.append("  Bounding Box: ")
           .append(String.format("Width=%.2f, Height=%.2f", 
                   entity.getWidth(), entity.getHeight()))
           .append("\n");
        
        // Additional data for living entities (mobs, players, etc.)
        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            
            // Health
            log.append("  Health: ")
               .append(String.format("%.1f/%.1f", 
                       living.getHealth(), living.getMaxHealth()))
               .append("\n");
            
            // Is entity on ground?
            log.append("  On Ground: ").append(living.isOnGround()).append("\n");
            
            // Is entity in air (jumping/falling)?
            log.append("  Is Airborne: ").append(!living.isOnGround()).append("\n");
            
            // Body yaw (direction body is facing, different from head)
            log.append("  Body Yaw: ")
               .append(String.format("%.1f°", living.bodyYaw))
               .append("\n");
            
            // Head yaw (direction head is facing)
            log.append("  Head Yaw: ")
               .append(String.format("%.1f°", living.headYaw))
               .append("\n");
        }
        
        // State flags
        log.append("  Is Alive: ").append(entity.isAlive()).append("\n");
        log.append("  Is On Fire: ").append(entity.isOnFire()).append("\n");
        log.append("  Is Invisible: ").append(entity.isInvisible()).append("\n");
        log.append("  Is Glowing: ").append(entity.isGlowing()).append("\n");
        log.append("  Is Sneaking: ").append(entity.isSneaking()).append("\n");
        log.append("  Is Sprinting: ").append(entity.isSprinting()).append("\n");
        
        // Age (ticks entity has existed)
        log.append("  Age: ").append(entity.age).append(" ticks\n");
        
        // Custom name (if entity has one)
        if (entity.hasCustomName()) {
            log.append("  Custom Name: ")
               .append(entity.getCustomName().getString())
               .append("\n");
        }
        
        log.append("---");
        
        // Write to console and file
        String logMessage = log.toString();
        if (consoleLoggingEnabled) {
        	LOGGER.info("\n" + logMessage);
        }
        writeToFile(logMessage);
    }
    
    /**
     * Called at the end of each logging cycle
     * 
     * @param entityCount Number of entities that were logged
     */
    public void endLogSession(int entityCount) {
        String summary = String.format("Total entities logged: %d\n", entityCount);
        writeToFile(summary);
    }
    
    /**
     * Writes a string to the log file
     * 
     * @param message The message to write
     */
    private void writeToFile(String message) {
        if (!fileLoggingEnabled || fileWriter == null) {
            return;
        }
        
        try {
            fileWriter.write(message);
            fileWriter.write("\n");
            fileWriter.flush(); // Immediately write to disk (important for real-time logging)
        } catch (IOException e) {
            LOGGER.error("Failed to write to log file", e);
            fileLoggingEnabled = false;
        }
    }
    
    /**
     * Closes the log file when the mod shuts down
     */
    public void close() {
        if (fileWriter != null) {
            try {
                writeToFile("\n=== Entity Logger Stopped ===");
                writeToFile("Timestamp: " + LocalDateTime.now().format(TIME_FORMAT));
                fileWriter.close();
                LOGGER.info("Closed log file");
            } catch (IOException e) {
                LOGGER.error("Failed to close log file", e);
            }
        }
    }
}