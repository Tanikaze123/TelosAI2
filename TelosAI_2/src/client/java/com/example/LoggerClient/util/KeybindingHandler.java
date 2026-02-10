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

/**
 * Handles keybindings for copying custom model data to chat
 */
public class KeybindingHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("entitylogger");
    
    private final EntityModel entityModel;
    private final Minecraft client;
    
    // Keybinding for copying model data
    private KeyMapping copyModelDataKey;
    
    /**
     * Constructor
     */
    public KeybindingHandler(EntityModel entityModel) {
        this.entityModel = entityModel;
        this.client = Minecraft.getInstance();
    }
    
 // Use ResourceLocation to define your custom category
    public static final KeyMapping.Category LOGGER_CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath("entitylogger", "main")
        );
    
    /**
     * Registers the keybindings
     */
    public void register() {
        // Create keybinding - Default key is 'M' (for Model)
        copyModelDataKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.entitylogger.copy_model_data",  // Translation key
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,                     // Default: M key
            LOGGER_CATEGORY              // Category in controls menu
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
    }
    
    /**
     * Called when the copy model data key is pressed
     */
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
     * @param message The message to display
     * @param actionBar If true, shows in action bar instead of chat
     */
    private void sendClientMessage(String message, boolean actionBar) {
        if (client.player == null) return;
        
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