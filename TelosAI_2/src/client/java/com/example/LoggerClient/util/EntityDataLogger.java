package com.example.LoggerClient.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

/**
 * Handles logging entity data to both the console and to files. This class
 * creates detailed logs of entity information for analysis.
 */
public class EntityDataLogger {

	private static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");

	// File logging
	private BufferedWriter fileWriter;
	private File logFile;
	private boolean fileLoggingEnabled = true;
	private boolean consoleLoggingEnabled = true;

	// Formatting
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	private EntityModel entityModel;

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
			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
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
	 * @param entity   The entity to log
	 * @param player   The player (for reference)
	 * @param distance Distance from player to entity
	 */
	public void logEntity(Entity entity, Player player, double distance) {

		// Build the log message with all entity data
		StringBuilder log = new StringBuilder();
		EntityModel entityModel = new EntityModel();

		// Basic identification
		log.append("Entity: ").append(entity.getName().getString()).append("\n");
		log.append("  Type: ").append(entity.getType().toString()).append("\n");
		log.append("  UUID: ").append(entity.getUUID()).append("\n");
		log.append("  Entity ID: ").append(entity.getId()).append("\n");

		// Position data
		Vec3 pos = entity.position();
		log.append("  Position: ").append(String.format("X=%.2f, Y=%.2f, Z=%.2f", pos.x, pos.y, pos.z)).append("\n");

		// Distance from player
		log.append("  Distance from player: ").append(String.format("%.2f blocks", distance)).append("\n");

		// Velocity (movement vector)
		Vec3 velocity = entity.getDeltaMovement();
		double speed = velocity.length(); // Total speed
		log.append("  Velocity: ").append(
				String.format("X=%.3f, Y=%.3f, Z=%.3f (Speed: %.3f)", velocity.x, velocity.y, velocity.z, speed))
				.append("\n");

		// Rotation (yaw and pitch)
		log.append("  Rotation: ").append(String.format("Yaw=%.1f°, Pitch=%.1f°", entity.getYRot(), entity.getXRot()))
				.append("\n");

		// Bounding box (hitbox size)
		log.append("  Bounding Box: ")
				.append(String.format("Width=%.2f, Height=%.2f", entity.getBbWidth(), entity.getBbHeight()))
				.append("\n");

		// Additional data for living entities (mobs, players, etc.)
		if (entity instanceof LivingEntity) {
			LivingEntity living = (LivingEntity) entity;

			// Health
			log.append("  Health: ").append(String.format("%.1f/%.1f", living.getHealth(), living.getMaxHealth()))
					.append("\n");

			// Is entity on ground?
			log.append("  On Ground: ").append(living.onGround()).append("\n");

			// Is entity in air (jumping/falling)?
			log.append("  Is Airborne: ").append(!living.onGround()).append("\n");

			// Body yaw (direction body is facing, different from head)
			log.append("  Body Yaw: ").append(String.format("%.1f°", living.yBodyRot)).append("\n");

			// Head yaw (direction head is facing)
			log.append("  Head Yaw: ").append(String.format("%.1f°", living.yHeadRot)).append("\n");
		}

		// State flags
		log.append("  Is Alive: ").append(entity.isAlive()).append("\n");
		log.append("  Is On Fire: ").append(entity.isOnFire()).append("\n");
		log.append("  Is Invisible: ").append(entity.isInvisible()).append("\n");
		log.append("  Is Glowing: ").append(entity.isCurrentlyGlowing()).append("\n");
		log.append("  Is Sneaking: ").append(entity.isCrouching()).append("\n");
		log.append("  Is Sprinting: ").append(entity.isSprinting()).append("\n");

		// Age (ticks entity has existed)
		log.append("  Age: ").append(entity.tickCount).append(" ticks\n");

		// Custom name (if entity has one)
		if (entity.hasCustomName()) {
			log.append("  Custom Name: ").append(entity.getCustomName().getString()).append("\n");
		}

		// ===== CUSTOM MODEL DATA & TEXTURE LOGGING (using EntityModel) =====

		if (entityModel.hasCustomModelData(entity)) {
			log.append("\n  === Custom Model Data ===\n");

			// Get texture info using EntityModel
			EntityModel.TextureInfo textureInfo = entityModel.getTextureInfo(entity);

			if (textureInfo != null) {
				log.append("  Item: ").append(textureInfo.getItemId()).append("\n");
				log.append("  Custom Model Data ID: ").append(textureInfo.getCustomModelData()).append("\n");

				// Log texture filename(s)
				List<String> textureFiles = textureInfo.getPossibleTexturePaths();
				if (!textureFiles.isEmpty()) {
					log.append("  Texture File(s):\n");
					for (String textureFile : textureFiles) {
						log.append("    - ").append(textureFile).append("\n");
					}
				} else {
					log.append("  Texture File: <Unable to determine>\n");
				}

				// Log model path
				String modelPath = textureInfo.getModelPath();
				if (modelPath != null) {
					log.append("  Model Path: ").append(modelPath).append("\n");
				}

				// Log full ItemStack for debugging
				log.append("  Full ItemStack: ").append(textureInfo.getItemStack().toString()).append("\n");
			}
		}
		
		// Check all passengers (ModelEngine uses passenger display entities)
        List<Entity> passengers = entity.getPassengers();
        if (!passengers.isEmpty()) {
            log.append("\n  === Passengers (Display Entities) ===\n");
            log.append("  Passenger Count: ").append(passengers.size()).append("\n");
            
            // Analyze all passengers using EntityModel
            List<EntityModel.TextureInfo> passengerTextures = entityModel.analyzePassengers(entity);
            
            if (!passengerTextures.isEmpty()) {
                log.append("  Passengers with Custom Models: ").append(passengerTextures.size()).append("\n");
                
                int index = 1;
                for (EntityModel.TextureInfo passengerInfo : passengerTextures) {
                    log.append("  Passenger ").append(index++).append(":\n");
                    log.append("    Item: ").append(passengerInfo.getItemId()).append("\n");
                    log.append("    Custom Model Data: ").append(passengerInfo.getCustomModelData()).append("\n");
                    
                    String primaryTexture = passengerInfo.getPrimaryTexturePath();
                    if (primaryTexture != null) {
                        log.append("    Texture: ").append(primaryTexture).append("\n");
                    }
                }
            } else {
                log.append("  (No passengers with custom model data)\n");
            }
        }

		log.append("---");

		// Write to console and file
		String logMessage = log.toString();
		if (consoleLoggingEnabled) {
			LOGGER.info("\n" + logMessage);
		}

		ItemStack textureStack = entityModel.getModelEngineTexture(entity);
		if (textureStack != null) {
			LOGGER.info("\n" + textureStack.toString());
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