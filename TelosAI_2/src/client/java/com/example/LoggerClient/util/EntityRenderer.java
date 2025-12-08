package com.example.LoggerClient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Renders visual overlays to show tracked entities in the game world. Updated
 * for Minecraft 1.21.8+ which removed BufferRenderer.
 */
public class EntityRenderer {

	private final List<TrackedEntity> trackedEntities = new ArrayList<>();

	private static class TrackedEntity {
		Entity entity;
		double distance;

		TrackedEntity(Entity entity, double distance) {
			this.entity = entity;
			this.distance = distance;
		}
	}

	public static final RenderLayer ESP_LINES = RenderLayer.of("example:esp_lines", 1536, ShaderPipelines.ESP_LINES,
			RenderLayer.MultiPhaseParameters.builder().lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2)))
					.layering(RenderLayer.VIEW_OFFSET_Z_LAYERING).target(RenderLayer.ITEM_ENTITY_TARGET).build(false));

	public void addTrackedEntity(Entity entity, double distance) {
		// Synchronize modification to prevent race condition
		synchronized (trackedEntities) {
			trackedEntities.add(new TrackedEntity(entity, distance));
		}
	}

	public void clearTrackedEntities() {
		// Synchronize modification to prevent race condition
		synchronized (trackedEntities) {
			trackedEntities.clear();
		}
	}

	public void render(WorldRenderContext context) {
		if (trackedEntities.isEmpty()) {
			return;
		}

		MatrixStack matrices = context.matrixStack();
		Vec3d cameraPos = context.camera().getPos();

		matrices.push();

		VertexConsumerProvider.Immediate vertexConsumers = MinecraftClient.getInstance().getBufferBuilders()
				.getEntityVertexConsumers();
		VertexConsumer buffer = vertexConsumers.getBuffer(ESP_LINES);

		for (TrackedEntity tracked : trackedEntities) {
			renderEntityBox(buffer, matrices, tracked.entity, tracked.distance, cameraPos);
			renderEntityLine(buffer, matrices, tracked.entity, cameraPos);
		}

		vertexConsumers.draw();
		matrices.pop();
	}

	private void renderEntityBox(VertexConsumer buffer, MatrixStack matrices, Entity entity, double distance,
			Vec3d cameraPos) {
		// Subtract cameraPos from absolute bounding box.
		Box box = entity.getBoundingBox().offset(-cameraPos.x, -cameraPos.y, -cameraPos.z).expand(0.01);

		// Color based on distance
		float red = (float) Math.min(distance / 50.0, 1.0);
		float green = (float) (1.0 - red);
		float blue = 0.2f;
		float alpha = 0.8f;

		drawBox(buffer, matrices, box, red, green, blue, alpha);
	}

	private void renderEntityLine(VertexConsumer buffer, MatrixStack matrices, Entity entity, Vec3d cameraPos) {
		Vec3d pos = entity.getPos();
		Vec3d velocity = entity.getVelocity();

		if (velocity.length() < 0.01)
			return;

		Vec3d start = pos.subtract(cameraPos);
		Vec3d end = start.add(velocity.multiply(50));

		drawLine(buffer, matrices, start, end, 1.0f, 1.0f, 0.0f, 0.8f);
	}

	/**
	 * Draw a box using RenderLayer instead of BufferRenderer (1.21.5+ way)
	 */
	private void drawBox(VertexConsumer buffer, MatrixStack matrices, Box box, float r, float g, float b, float a) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();

		double minX = box.minX;
		double minY = box.minY;
		double minZ = box.minZ;
		double maxX = box.maxX;
		double maxY = box.maxY;
		double maxZ = box.maxZ;

		// Bottom square
		drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
		drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
		drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
		drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

		// Top square
		drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
		drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
		drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
		drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

		// Vertical lines
		drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
		drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
		drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
		drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);

	}

	/**
	 * Draw a line using RenderLayer
	 */
	// Overload for Vec3d - passes MatrixStack to get Matrix4f
	private void drawLine(VertexConsumer buffer, MatrixStack matrices, Vec3d start, Vec3d end, float r, float g,
			float b, float alpha) {
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, alpha).normal(0, 1, 0);
		buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, alpha).normal(0, 1, 0);
	}

	private void drawLine(VertexConsumer buffer, Matrix4f matrix, double x1, double y1, double z1, double x2, double y2,
			double z2, float r, float g, float b, float a) {
		buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(0, 1, 0);
		buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(0, 1, 0);
	}
}