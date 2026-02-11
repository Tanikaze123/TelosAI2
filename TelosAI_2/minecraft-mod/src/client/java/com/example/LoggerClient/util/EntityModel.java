package com.example.LoggerClient.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

public class EntityModel {

	/**
	 * Gets the ItemStack that contains the ModelEngine texture/model data IMPROVED
	 * VERSION: Checks multiple sources including passengers
	 * 
	 * @param entity The entity to extract the texture from
	 * @return ItemStack with custom model data, or ItemStack.EMPTY if none found
	 */
	public ItemStack getModelEngineTexture(Entity entity) {
		// Strategy 1: Check if the entity itself is a display entity
		if (entity instanceof ItemDisplay itemDisplay) {
			ItemStack stack = itemDisplay.getItemStack();
			if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
				return stack;
			}
		}

		// Strategy 2: Check if entity is an ArmorStand (check ALL slots, not just head)
		if (entity instanceof ArmorStand armorStand) {
			// Check head slot first (most common)
			ItemStack headStack = armorStand.getItemBySlot(EquipmentSlot.HEAD);
			if (!headStack.isEmpty() && headStack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
				return headStack;
			}

			// Check other equipment slots
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = armorStand.getItemBySlot(slot);
				if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
					return stack;
				}
			}
		}

		// Strategy 3: Check ItemFrame
		if (entity instanceof ItemFrame itemFrame) {
			ItemStack stack = itemFrame.getItem();
			if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
				return stack;
			}
		}

		// Strategy 4: Check passengers (MOST COMMON FOR MODELENGINE)
		// ModelEngine typically attaches ItemDisplay entities as passengers
		List<Entity> passengers = entity.getPassengers();
		if (!passengers.isEmpty()) {
			ItemStack passengerStack = getModelEngineTextureFromPassengers(passengers);
			if (passengerStack != null && !passengerStack.isEmpty()) {
				return passengerStack;
			}
		}

		return ItemStack.EMPTY;
	}

	/**
	 * Recursively searches through passengers to find custom model data ModelEngine
	 * uses nested passenger structures for multi-part models
	 */
	private ItemStack getModelEngineTextureFromPassengers(List<Entity> passengers) {
		for (Entity passenger : passengers) {
			// Check if this passenger is an ItemDisplay (ModelEngine v4+)
			if (passenger instanceof ItemDisplay itemDisplay) {
				ItemStack stack = itemDisplay.getItemStack();
				if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
					return stack;
				}
			}

			// Check if passenger is an ArmorStand (ModelEngine v3/legacy)
			if (passenger instanceof ArmorStand armorStand) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack stack = armorStand.getItemBySlot(slot);
					if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
						return stack;
					}
				}
			}

			// Recursively check this passenger's passengers
			// ModelEngine models can have deep nesting (root -> bone -> sub-bone)
			if (!passenger.getPassengers().isEmpty()) {
				ItemStack recursiveStack = getModelEngineTextureFromPassengers(passenger.getPassengers());
				if (recursiveStack != null && !recursiveStack.isEmpty()) {
					return recursiveStack;
				}
			}
		}

		return null;
	}

	/**
	 * Gets ALL ItemStacks with custom model data from an entity and its passengers
	 * Useful for complex ModelEngine models with multiple parts/bones
	 * 
	 * @param entity The entity to analyze
	 * @return List of all ItemStacks with custom model data
	 */
	public List<ItemStack> getAllModelEngineTextures(Entity entity) {
		List<ItemStack> stacks = new ArrayList<>();

		// Check the entity itself
		ItemStack mainStack = getModelEngineTexture(entity);
		if (mainStack != null && !mainStack.isEmpty()) {
			stacks.add(mainStack);
		}

		// Check all passengers recursively
		collectStacksFromPassengers(entity.getPassengers(), stacks);

		return stacks;
	}

	/**
	 * Helper method to recursively collect all stacks with custom model data
	 */
	private void collectStacksFromPassengers(List<Entity> passengers, List<ItemStack> collector) {
		for (Entity passenger : passengers) {
			// ItemDisplay (ModelEngine v4+)
			if (passenger instanceof ItemDisplay itemDisplay) {
				ItemStack stack = itemDisplay.getItemStack();
				if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
					collector.add(stack);
				}
			}

			// ArmorStand (ModelEngine v3/legacy) - check all slots
			if (passenger instanceof ArmorStand armorStand) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					ItemStack stack = armorStand.getItemBySlot(slot);
					if (!stack.isEmpty() && stack.get(DataComponents.CUSTOM_MODEL_DATA) != null) {
						collector.add(stack);
					}
				}
			}

			// Recurse into nested passengers
			if (!passenger.getPassengers().isEmpty()) {
				collectStacksFromPassengers(passenger.getPassengers(), collector);
			}
		}
	}

	/**
	 * Checks if an ItemStack has valid custom model data In 1.21.1, CustomModelData
	 * contains lists, so we check if any data exists
	 */
	private boolean hasValidCustomModelData(ItemStack stack) {
		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd == CustomModelData.EMPTY) {
			return false;
		}

		// Check if any of the lists have data
		return !cmd.floats().isEmpty() || !cmd.flags().isEmpty() || !cmd.strings().isEmpty() || !cmd.colors().isEmpty();
	}

	/**
	 * Gets the ItemStack being displayed by an entity ModelEngine-specific: only
	 * checks ItemDisplay and ArmorStand
	 * 
	 * @param entity The entity to check
	 * @return ItemStack being displayed, or null if none found
	 */
	public ItemStack getDisplayedItem(Entity entity) {
		if (entity instanceof ItemDisplay itemDisplay) {
			return itemDisplay.getItemStack();
		} else if (entity instanceof ArmorStand armorStand) {
			// Check head slot first (most common for ModelEngine)
			ItemStack headItem = armorStand.getItemBySlot(EquipmentSlot.HEAD);
			if (!headItem.isEmpty())
				return headItem;

			// Check other slots
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack item = armorStand.getItemBySlot(slot);
				if (!item.isEmpty()) {
					CustomModelData cmd = item.get(DataComponents.CUSTOM_MODEL_DATA);
					if (cmd != null) {
						return item;
					}
				}
			}
		}

		return null;
	}

	/**
	 * Gets custom model data ID from an entity's texture stack
	 * 
	 * @param entity The entity to check
	 * @return Custom model data value, or -1 if not present
	 */
	public int getCustomModelData(Entity entity) {
		ItemStack stack = getModelEngineTexture(entity);
		return getCustomModelData(stack);
	}

	/**
	 * Gets custom model data ID from an ItemStack
	 * 
	 * @param stack The ItemStack to check
	 * @return Custom model data value, or -1 if not present
	 */
	public int getCustomModelData(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return -1;
		}

		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd == CustomModelData.EMPTY) {
			return -1;
		}

		// Try to extract an ID from the CustomModelData
		// ModelEngine likely uses the first color value as the model ID
		Integer color = cmd.getColor(0);
		if (color != null) {
			return color;
		}

		// Fallback: try floats (might be stored as a float)
		Float floatValue = cmd.getFloat(0);
		if (floatValue != null) {
			return floatValue.intValue();
		}

		// If we have any data, return a hash as identifier
		if (hasValidCustomModelData(stack)) {
			// Return a hash of the CustomModelData for identification
			return cmd.hashCode();
		}

		return -1;
	}

	/**
	 * Attempts to determine texture filename(s) from custom model data Returns list
	 * of likely paths based on common ModelEngine patterns
	 * 
	 * @param stack           The ItemStack with custom model data
	 * @param customModelData The custom model data ID
	 * @return List of probable texture file paths
	 */
	public List<String> getTextureFilenames(ItemStack stack, int customModelData) {
		List<String> textureFiles = new ArrayList<>();

		// Get the item's registry name
		String itemId = stack.getItem().toString();

		// Common ModelEngine texture patterns:

		// Pattern 1: Direct CMD mapping (most common)
		textureFiles.add(String.format("modelengine:textures/model/custom_%d.png", customModelData));

		// Pattern 2: Assets folder path
		textureFiles.add(String.format("assets/modelengine/textures/model/cmd_%d.png", customModelData));

		// Pattern 3: Item-based path
		String itemName = itemId.replace("minecraft:", "").replace("_", "");
		textureFiles.add(String.format("modelengine:textures/entity/%s/%d.png", itemName, customModelData));

		// Pattern 4: Simple numbered pattern
		textureFiles.add(String.format("modelengine:model/%d.png", customModelData));

		return textureFiles;
	}

	/**
	 * Gets texture filenames for an entity
	 * 
	 * @param entity The entity to get textures for
	 * @return List of probable texture file paths
	 */
	public List<String> getTextureFilenames(Entity entity) {
		ItemStack stack = getModelEngineTexture(entity);
		if (stack == null || stack.isEmpty()) {
			return new ArrayList<>();
		}

		int cmd = getCustomModelData(stack);
		if (cmd == -1) {
			return new ArrayList<>();
		}

		return getTextureFilenames(stack, cmd);
	}

	/**
	 * Gets the primary (most likely) texture filename for an entity
	 * 
	 * @param entity The entity to check
	 * @return The most likely texture filename, or null if not found
	 */
	public String getPrimaryTextureFilename(Entity entity) {
		List<String> textures = getTextureFilenames(entity);
		return textures.isEmpty() ? null : textures.get(0);
	}

	/**
	 * Gets the primary (most likely) texture filename for an ItemStack
	 * 
	 * @param stack The ItemStack to check
	 * @return The most likely texture filename, or null if not found
	 */
	public String getPrimaryTextureFilename(ItemStack stack) {
		int cmd = getCustomModelData(stack);
		if (cmd == -1) {
			return null;
		}

		List<String> textures = getTextureFilenames(stack, cmd);
		return textures.isEmpty() ? null : textures.get(0);
	}

	/**
	 * Gets the model path that would be defined in the item model JSON
	 * 
	 * @param stack           The ItemStack with custom model data
	 * @param customModelData The custom model data ID
	 * @return The model JSON file path
	 */
	public String getModelPath(ItemStack stack, int customModelData) {
		String itemId = stack.getItem().toString().replace("minecraft:", "");

		return String.format("assets/minecraft/models/item/%s.json (override: %d)", itemId, customModelData);
	}

	/**
	 * Gets the model path for an entity
	 * 
	 * @param entity The entity to check
	 * @return The model JSON file path, or null if not found
	 */
	public String getModelPath(Entity entity) {
		ItemStack stack = getModelEngineTexture(entity);
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		int cmd = getCustomModelData(stack);
		if (cmd == -1) {
			return null;
		}

		return getModelPath(stack, cmd);
	}

	/**
	 * Gets all custom model information for an entity as a formatted string
	 * 
	 * @param entity The entity to analyze
	 * @return Formatted string with model info, or descriptive message if no model
	 *         found
	 */
	public String getCustomModelInfo(Entity entity) {
		ItemStack stack = getModelEngineTexture(entity);
		if (stack == null || stack.isEmpty()) {
			return "No custom model data";
		}

		CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (cmd == null || cmd == CustomModelData.EMPTY) {
			return "Item: " + stack.getItem().toString() + " (no custom model data)";
		}

		int cmdValue = getCustomModelData(stack);
		List<String> textures = getTextureFilenames(stack, cmdValue);

		StringBuilder info = new StringBuilder();
		info.append("Item: ").append(stack.getItem().toString());
		info.append(", CMD ID: ").append(cmdValue);

		// Show CustomModelData details
		if (!cmd.colors().isEmpty()) {
			info.append(", Colors: ").append(cmd.colors());
		}
		if (!cmd.floats().isEmpty()) {
			info.append(", Floats: ").append(cmd.floats());
		}
		if (!cmd.flags().isEmpty()) {
			info.append(", Flags: ").append(cmd.flags());
		}
		if (!cmd.strings().isEmpty()) {
			info.append(", Strings: ").append(cmd.strings());
		}

		if (!textures.isEmpty()) {
			info.append(", Texture: ").append(textures.get(0));
		}

		return info.toString();
	}

	/**
	 * Checks if an entity has custom model data
	 * 
	 * @param entity The entity to check
	 * @return true if the entity has custom model data, false otherwise
	 */
	public boolean hasCustomModelData(Entity entity) {
		return getCustomModelData(entity) != -1;
	}

	/**
	 * Checks if an ItemStack has custom model data
	 * 
	 * @param stack The ItemStack to check
	 * @return true if the stack has custom model data, false otherwise
	 */
	public boolean hasCustomModelData(ItemStack stack) {
		return getCustomModelData(stack) != -1;
	}

	/**
	 * Gets detailed texture information including all possible paths
	 * 
	 * @param entity The entity to analyze
	 * @return TextureInfo object with all details, or null if no custom model
	 */
	public TextureInfo getTextureInfo(Entity entity) {
		ItemStack stack = getModelEngineTexture(entity);
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		int cmd = getCustomModelData(stack);
		if (cmd == -1) {
			return null;
		}

		return new TextureInfo(stack, cmd);
	}

	/**
	 * Container class for texture information
	 */
	public class TextureInfo {
		private final ItemStack itemStack;
		private final int customModelData;
		private final List<String> possibleTexturePaths;
		private final String modelPath;

		public TextureInfo(ItemStack stack, int cmd) {
			this.itemStack = stack;
			this.customModelData = cmd;
			this.possibleTexturePaths = EntityModel.this.getTextureFilenames(stack, cmd);
			this.modelPath = EntityModel.this.getModelPath(stack, cmd);
		}

		public ItemStack getItemStack() {
			return itemStack;
		}

		public int getCustomModelData() {
			return customModelData;
		}

		public List<String> getPossibleTexturePaths() {
			return possibleTexturePaths;
		}

		public String getPrimaryTexturePath() {
			return possibleTexturePaths.isEmpty() ? null : possibleTexturePaths.get(0);
		}

		public String getModelPath() {
			return modelPath;
		}

		public String getItemId() {
			return itemStack.getItem().toString();
		}
		
		public CustomModelData getFullCustomModelData() {
			return itemStack.get(DataComponents.CUSTOM_MODEL_DATA);
		}

		@Override
		public String toString() {
			return String.format("TextureInfo{item=%s, cmd=%d, texture=%s}", getItemId(), customModelData,
					getPrimaryTexturePath());
		}
	}

	/**
	 * Analyzes all passengers of an entity for custom model data Useful for
	 * ModelEngine entities that use multiple display entities
	 * 
	 * @param entity The entity whose passengers to analyze
	 * @return List of TextureInfo objects for each passenger with custom model data
	 */
	public List<TextureInfo> analyzePassengers(Entity entity) {
		List<TextureInfo> passengerTextures = new ArrayList<>();

		for (Entity passenger : entity.getPassengers()) {
			ItemStack passengerStack = getDisplayedItem(passenger);
			if (passengerStack != null && !passengerStack.isEmpty()) {
				int cmd = getCustomModelData(passengerStack);
				if (cmd != -1) {
					passengerTextures.add(new TextureInfo(passengerStack, cmd));
				}
			}

			// Recursively check passenger's passengers
			passengerTextures.addAll(analyzePassengers(passenger));
		}

		return passengerTextures;
	}
}