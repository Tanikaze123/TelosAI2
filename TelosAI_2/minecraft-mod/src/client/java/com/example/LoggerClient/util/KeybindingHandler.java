package com.example.LoggerClient.util;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.CustomModelData;
import com.example.LoggerClient.util.APIHandler.BackendLink;

/**
 * Handles keybindings for copying custom model data to chat
 */
public class KeybindingHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");

	private final EntityModel entityModel;
	private final BackendLink backendLink;
	private final Minecraft client;

	// Keybinding for copying model data
	private KeyMapping copyModelDataKey;
	private KeyMapping copyAllModelDataKey;
	private KeyMapping sendPingToBackendKey;

	/**
	 * Constructor
	 */
	public KeybindingHandler(EntityModel entityModel, BackendLink backendLink) {
		this.entityModel = entityModel;
		this.backendLink = backendLink;
		this.client = Minecraft.getInstance();
	}

	// Use ResourceLocation to define custom category
	public static final KeyMapping.Category LOGGER_CATEGORY = KeyMapping.Category
			.register(ResourceLocation.fromNamespaceAndPath("entitylogger", "main"));

	/**
	 * Registers the keybindings
	 */
	public void register() {
		// Create keybinding - Default key is 'M' (for Model)
		copyModelDataKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.entitylogger.copy_model_data", // Translation
																													// key
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, // Default: M key
				LOGGER_CATEGORY // Category in controls menu
		));

		copyAllModelDataKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.entitylogger.debug_entity", // Translation
																													// key
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, // Default: N key
				LOGGER_CATEGORY // Category in controls menu
		));

		sendPingToBackendKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.entitylogger.backend_ping", // Translation
																													// key
				InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, // Default: B key
				LOGGER_CATEGORY // Category in controls menu
		));

		// Register tick event to check for key presses
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		LOGGER.info("Keybindings registered - Press 'M' to copy model data");
	}

	/**
	 * Called every tick to check for key presses
	 */
	private void onClientTick(Minecraft client) {
		// Check if the copy key was pressed
		if (copyModelDataKey.consumeClick()) {
			onCopyModelDataPressed();
		}

		// Check if the alla key was pressed
		if (copyAllModelDataKey.consumeClick()) {
			onDebugEntityPressed();
		}

		// Check if the ping key was pressed
		if (sendPingToBackendKey.consumeClick()) {
			backendLink.testConnection("modid");;
		}
	}

	/**
	 * Called when the copy key is pressed
	 */
	private void onDebugEntityPressed() {
		if (client.player == null || client.level == null) {
			return;
		}

		Entity targetEntity = client.crosshairPickEntity;

		if (targetEntity == null) {
			sendClientMessage("§cNot looking at any entity", false);
			return;
		}

		debugEntityStructure(targetEntity);
	}

	private void onCopyModelDataPressed() {
		// Safety checks
		if (client.player == null || client.level == null) {
			return;
		}

		// Get the entity the player is looking at
		Entity targetEntity = client.crosshairPickEntity;

		if (targetEntity == null) {
			// No entity being looked at
			sendClientMessage("§cNot looking at any entity", false);
			return;
		}

		// Try to get custom model data
		if (!entityModel.hasCustomModelData(targetEntity)) {
			sendClientMessage("§e" + targetEntity.getName().getString() + " §chas no custom model data", false);
			return;
		}

		// Get the model info
		copyAndDisplayModelData(targetEntity);
	}

	/**
	 * Copies model data and displays it in chat
	 */
	private void copyAndDisplayModelData(Entity entity) {
		try {
			// Get texture info
			EntityModel.TextureInfo textureInfo = entityModel.getTextureInfo(entity);

			if (textureInfo == null) {
				sendClientMessage("§cFailed to extract model data", false);
				return;
			}

			// Get full custom model data
			CustomModelData fullData = textureInfo.getFullCustomModelData();

			// Build the message
			StringBuilder message = new StringBuilder();
			message.append("§a§l=== Custom Model Data ===§r\n");
			message.append("§bEntity: §f").append(entity.getName().getString()).append("\n");
			message.append("§bType: §f").append(entity.getType().toString()).append("\n");
			message.append("§bItem: §f").append(textureInfo.getItemId()).append("\n");
			message.append("§bModel ID: §f").append(textureInfo.getCustomModelData()).append("\n");

			// Add CustomModelData details
			if (fullData != null) {
				if (!fullData.colors().isEmpty()) {
					message.append("§bColors: §f").append(fullData.colors()).append("\n");
				}
				if (!fullData.floats().isEmpty()) {
					message.append("§bFloats: §f").append(fullData.floats()).append("\n");
				}
				if (!fullData.flags().isEmpty()) {
					message.append("§bFlags: §f").append(fullData.flags()).append("\n");
				}
				if (!fullData.strings().isEmpty()) {
					message.append("§bStrings: §f").append(fullData.strings()).append("\n");
				}
			}

			// Add texture paths
			message.append("§bTexture Path: §f").append(textureInfo.getPrimaryTexturePath()).append("\n");
			message.append("§bModel Path: §f").append(textureInfo.getModelPath());

			// Send to chat (client-side only)
			sendClientMessage(message.toString(), false);

			// Also copy to clipboard
			copyToClipboard(textureInfo);

			// Success sound/message
			sendClientMessage("§a✓ Model data copied to clipboard!", false);

		} catch (Exception e) {
			LOGGER.error("Error copying model data", e);
			sendClientMessage("§cError: " + e.getMessage(), false);
		}
	}

	/**
	 * Sends a message to the client chat (does NOT send to server)
	 * 
	 * @param message   The message to display
	 * @param actionBar If true, shows in action bar instead of chat
	 */
	private void sendClientMessage(String message, boolean actionBar) {
		if (client.player == null)
			return;

		Component textComponent = Component.literal(message);

		if (actionBar) {
			// Display in action bar (above hotbar)
			client.player.displayClientMessage(textComponent, true);
		} else {
			// Display in chat (client-side only, doesn't send to server)
			client.player.displayClientMessage(textComponent, false);
		}
	}

	/**
	 * Debug: Shows the entire entity structure Useful for understanding how
	 * ModelEngine structures entities
	 */
	public void debugEntityStructure(Entity rootEntity) {
		sendClientMessage("§e§l=== Entity Structure Debug ===§r", false);
		sendClientMessage("§bTarget Entity: §f" + rootEntity.getType() + " §7(UUID: "
				+ rootEntity.getUUID().toString().substring(0, 8) + "...)", false);
		sendClientMessage(
				"§bPosition: §f"
						+ String.format("%.1f, %.1f, %.1f", rootEntity.getX(), rootEntity.getY(), rootEntity.getZ()),
				false);
		sendClientMessage("§bPassengers: §f" + rootEntity.getPassengers().size(), false);

		if (entityModel.hasCustomModelData(rootEntity)) {
			int modelId = entityModel.getCustomModelData(rootEntity);
			sendClientMessage("  §a✓ Target has custom model data: " + modelId, false);
		} else {
			sendClientMessage("  §7Target has no custom model data", false);
		}

		// Show passengers
		if (!rootEntity.getPassengers().isEmpty()) {
			sendClientMessage("\n§e=== Passengers ===", false);
			debugPassengers(rootEntity.getPassengers(), 1);
		}

		// Show nearby entities
		sendClientMessage("\n§e=== Nearby Entities (within 3 blocks) ===", false);
		debugNearbyEntities(rootEntity, 3.0);
	}

	/**
	 * Helper for debugEntityStructure - recursively shows passengers
	 */
	private void debugPassengers(java.util.List<Entity> passengers, int depth) {
		String indent = "  ".repeat(depth);

		for (int i = 0; i < passengers.size(); i++) {
			Entity passenger = passengers.get(i);
			StringBuilder line = new StringBuilder();
			line.append(indent).append("§7Passenger ").append(i + 1).append(": §f");
			line.append(passenger.getType());

			if (entityModel.hasCustomModelData(passenger)) {
				int modelId = entityModel.getCustomModelData(passenger);
				line.append(" §a✓ CMD: ").append(modelId);
			}

			if (!passenger.getPassengers().isEmpty()) {
				line.append(" §7(").append(passenger.getPassengers().size()).append(" sub-passengers)");
			}

			sendClientMessage(line.toString(), false);

			// Recurse
			if (!passenger.getPassengers().isEmpty()) {
				debugPassengers(passenger.getPassengers(), depth + 1);
			}
		}
	}

	/**
	 * Shows all entities near the target
	 */
	private void debugNearbyEntities(Entity centerEntity, double radius) {
		if (client.level == null)
			return;

		int count = 0;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == centerEntity)
				continue;

			double distance = entity.distanceTo(centerEntity);
			if (distance > radius)
				continue;

			count++;
			StringBuilder line = new StringBuilder();
			line.append("  §7").append(count).append(". §f");
			line.append(entity.getType());
			line.append(" §7(").append(String.format("%.2fm", distance)).append(")");

			if (entityModel.hasCustomModelData(entity)) {
				int modelId = entityModel.getCustomModelData(entity);
				line.append(" §a✓ CMD: ").append(modelId);
			}

			if (!entity.getPassengers().isEmpty()) {
				line.append(" §7[").append(entity.getPassengers().size()).append(" passengers]");
			}

			sendClientMessage(line.toString(), false);
		}

		if (count == 0) {
			sendClientMessage("  §7No nearby entities found", false);
		} else {
			sendClientMessage("§7Total: " + count + " nearby entities", false);
		}
	}

	/**
	 * Copies model data to system clipboard
	 */
	private void copyToClipboard(EntityModel.TextureInfo textureInfo) {
		try {
			CustomModelData fullData = textureInfo.getFullCustomModelData();

			// Format for clipboard (plain text)
			StringBuilder clipboardText = new StringBuilder();
			clipboardText.append("=== Custom Model Data ===\n");
			clipboardText.append("Item: ").append(textureInfo.getItemId()).append("\n");
			clipboardText.append("Model ID: ").append(textureInfo.getCustomModelData()).append("\n");

			if (fullData != null) {
				if (!fullData.colors().isEmpty()) {
					clipboardText.append("Colors: ").append(fullData.colors()).append("\n");
				}
				if (!fullData.floats().isEmpty()) {
					clipboardText.append("Floats: ").append(fullData.floats()).append("\n");
				}
				if (!fullData.flags().isEmpty()) {
					clipboardText.append("Flags: ").append(fullData.flags()).append("\n");
				}
				if (!fullData.strings().isEmpty()) {
					clipboardText.append("Strings: ").append(fullData.strings()).append("\n");
				}
			}

			clipboardText.append("Texture: ").append(textureInfo.getPrimaryTexturePath()).append("\n");
			clipboardText.append("Model Path: ").append(textureInfo.getModelPath()).append("\n");

			// Add all possible texture paths
			clipboardText.append("\nPossible Texture Paths:\n");
			for (String path : textureInfo.getPossibleTexturePaths()) {
				clipboardText.append("  - ").append(path).append("\n");
			}

			// Copy to system clipboard
			client.keyboardHandler.setClipboard(clipboardText.toString());

		} catch (Exception e) {
			LOGGER.error("Failed to copy to clipboard", e);
		}
	}

	/**
	 * Alternative: Copy just the model ID to clipboard (simpler)
	 */
	public void copyModelIdOnly(Entity entity) {
		int modelId = entityModel.getCustomModelData(entity);
		if (modelId != -1) {
			client.keyboardHandler.setClipboard(String.valueOf(modelId));
			sendClientMessage("§aCopied model ID: §f" + modelId, true);
		}
	}

	/**
	 * Alternative: Copy just the texture path to clipboard
	 */
	public void copyTexturePathOnly(Entity entity) {
		String texturePath = entityModel.getPrimaryTextureFilename(entity);
		if (texturePath != null) {
			client.keyboardHandler.setClipboard(texturePath);
			sendClientMessage("§aCopied texture: §f" + texturePath, true);
		}
	}
}