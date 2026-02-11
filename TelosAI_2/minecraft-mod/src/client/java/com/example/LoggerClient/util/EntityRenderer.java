package com.example.LoggerClient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import com.example.LoggerClient.LoggerClient;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Renders visual overlays to show tracked entities in the game world. Updated
 * for Minecraft 1.21.8+ which removed BufferRenderer.
 */
public class EntityRenderer {

	protected static final Minecraft MC = LoggerClient.getInstance();

	private final List<TrackedEntity> trackedEntities = new ArrayList<>();

	private static class TrackedEntity {
		Entity entity;
		double distance;

		TrackedEntity(Entity entity, double distance) {
			this.entity = entity;
			this.distance = distance;
		}
	}

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

	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines
			.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(ResourceLocation.fromNamespaceAndPath("modid", "pipeline/debug_filled_box_through_walls"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());

	private static final RenderPipeline LINE_THROUGH_WALLS = RenderPipelines
			.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
					.withLocation(ResourceLocation.fromNamespaceAndPath("modid", "pipeline/debug_lines_through_walls"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());

	private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private BufferBuilder buffer;

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final float LINE_WIDTH = 2.0f;
	private MappableRingBuffer vertexBuffer;

	private void renderFilledBox(Matrix4f positionMatrix, BufferBuilder buffer, float minX, float minY, float minZ,
			float maxX, float maxY, float maxZ, float r, float g, float b, float alpha) {
		// Front Face
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(r, g, b, alpha);

		// Back face
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(r, g, b, alpha);

		// Left face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(r, g, b, alpha);

		// Right face
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(r, g, b, alpha);

		// Top face
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(r, g, b, alpha);

		// Bottom face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(r, g, b, alpha);
	}

	private void renderFilledBox(Matrix4f positionMatrix, BufferBuilder buffer, AABB box, float r, float g, float b,
			float alpha) {
		float minX = (float) box.minX;
		float minY = (float) box.minY;
		float minZ = (float) box.minZ;
		float maxX = (float) box.maxX;
		float maxY = (float) box.maxY;
		float maxZ = (float) box.maxZ;

		// Front Face
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(r, g, b, alpha);

		// Back face
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(r, g, b, alpha);

		// Left face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(r, g, b, alpha);

		// Right face
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(r, g, b, alpha);

		// Top face
		buffer.addVertex(positionMatrix, minX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, maxY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, maxY, minZ).setColor(r, g, b, alpha);

		// Bottom face
		buffer.addVertex(positionMatrix, minX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, minZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, maxX, minY, maxZ).setColor(r, g, b, alpha);
		buffer.addVertex(positionMatrix, minX, minY, maxZ).setColor(r, g, b, alpha);
	}

	private void drawFilledThroughWalls(Minecraft client,
			@SuppressWarnings("SameParameterValue") RenderPipeline pipeline) {
		// Build the buffer
		MeshData builtBuffer = buffer.buildOrThrow();
		MeshData.DrawState drawParameters = builtBuffer.drawState();
		VertexFormat format = drawParameters.format();

		GpuBuffer vertices = upload(drawParameters, format, builtBuffer);

		draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

		// Rotate the vertex buffer so we are less likely to use buffers that the GPU is
		// using
		vertexBuffer.rotate();
		buffer = null;
	}

	private GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
		// Calculate the size needed for the vertex buffer
		int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

		// Initialize or resize the vertex buffer as needed
		if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
			vertexBuffer = new MappableRingBuffer(() -> "modid" + " example render pipeline",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
		}

		// Copy vertex data into the vertex buffer
		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

		try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
				vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
		}

		return vertexBuffer.currentBuffer();
	}

	private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer,
			MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
		GpuBuffer indices;
		VertexFormat.IndexType indexType;

		if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			// Sort the quads if there is translucency
			builtBuffer.sortQuads(allocator, RenderSystem.getProjectionType().vertexSorting());
			// Upload the index buffer
			indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
			indexType = builtBuffer.drawState().indexType();
		} else {
			// Use the general shape index buffer for non-quad draw modes
			RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem
					.getSequentialBuffer(pipeline.getVertexFormatMode());
			indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
			indexType = shapeIndexBuffer.type();
		}

		// Actually execute the draw
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX, LINE_WIDTH);
		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "modid" + " example render pipeline rendering",
				client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			renderPass.setPipeline(pipeline);

			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			// Bind texture if applicable:
			// Sampler0 is used for texture inputs in vertices
			// renderPass.bindTexture("Sampler0", textureSetup.texure0(),
			// textureSetup.sampler0());

			renderPass.setVertexBuffer(0, vertices);
			renderPass.setIndexBuffer(indices, indexType);

			// The base vertex is the starting index when we copied the data into the vertex
			// buffer divided by vertex size
			// noinspection ConstantValue
			renderPass.drawIndexed(0 / format.getVertexSize(), 0, drawParameters.indexCount(), 1);
		}

		builtBuffer.close();
	}

	public void render(WorldRenderContext context) {
		if (trackedEntities.isEmpty())
			return;

		float partialTicks = MC.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		PoseStack matrices = context.matrices();
		Vec3 camera = context.worldState().cameraRenderState.pos;

		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);

		// ========== DRAW Bounding Box ==========
		this.buffer = new BufferBuilder(allocator, FILLED_THROUGH_WALLS.getVertexFormatMode(),
				FILLED_THROUGH_WALLS.getVertexFormat());

		for (TrackedEntity tracked : trackedEntities) {
			AABB box = getLerpedBox(tracked.entity, partialTicks);
			float[] entityColor = getColor(tracked.entity);
			renderFilledBox((Matrix4f) matrices.last().pose(), buffer, box, entityColor[0], entityColor[1], entityColor[2], 0.5f);
		}

		drawFilledThroughWalls(Minecraft.getInstance(), FILLED_THROUGH_WALLS);

		// ========== DRAW Velocity Lines ==========
		this.buffer = new BufferBuilder(allocator, LINE_THROUGH_WALLS.getVertexFormatMode(),
				LINE_THROUGH_WALLS.getVertexFormat());

		for (TrackedEntity tracked : trackedEntities) {
			float[] entityColor = getColor(tracked.entity);
			renderVelocityLine(matrices, buffer, tracked.entity, partialTicks, entityColor);
		}

		drawFilledThroughWalls(Minecraft.getInstance(), LINE_THROUGH_WALLS);

		// END DRAWS

		matrices.popPose();
	}

	public void renderVelocityLine(PoseStack matrices, BufferBuilder buffer, Entity entity, float partialTicks,
			float[] color) {
		// 1. Get the current interpolated position
		Vec3 startPos = getLerpedPos(entity, partialTicks);

		// 2. Get the velocity
		Vec3 velocity = entity.getDeltaMovement();

		// 3. Calculate absolute end coordinates
		double endX = startPos.x + velocity.x;
		double endY = startPos.y + velocity.y;
		double endZ = startPos.z + velocity.z;

		// 4. Draw the line using the matrix
		// IMPORTANT: Since your matrices are already translated by -camera,
		// these absolute world coords will now render in the correct spot.
		drawLine(matrices.last().pose(), buffer, (float) startPos.x, (float) startPos.y, (float) startPos.z,
				(float) endX, (float) endY, (float) endZ, color[0], color[1], color[2], 1.0f);
	}

	public static void drawLine(Matrix4f positionMatrix, BufferBuilder buffer, float x1, float y1, float z1, float x2,
			float y2, float z2, float r, float g, float b, float alpha) {
		// A simple normal vector for the line
		float dx = x2 - x1;
		float dy = y2 - y1;
		float dz = z2 - z1;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		dx /= len;
		dy /= len;
		dz /= len;

		// First Point
		buffer.addVertex(positionMatrix, x1, y1, z1).setColor(r, g, b, alpha).setNormal(dx, dy, dz);
		// Second Point
		buffer.addVertex(positionMatrix, x2, y2, z2).setColor(r, g, b, alpha).setNormal(dx, dy, dz);
	}

	private float[] getColor(Entity e) // OR LivingEntity
	{
		float f = MC.player.distanceTo(e) / 20F;
		float r = Mth.clamp(2 - f, 0, 1);
		float g = Mth.clamp(f, 0, 1);
		float[] rgb = { r, g, 0 };

		return rgb;
	}

	public static AABB getLerpedBox(Entity e, float partialTicks) {
		// When an entity is removed, it stops moving and its lastRenderX/Y/Z
		// values are no longer updated.
		if (e.isRemoved())
			return e.getBoundingBox();

		Vec3 offset = getLerpedPos(e, partialTicks).subtract(e.position());
		return e.getBoundingBox().move(offset);
	}

	public static Vec3 getLerpedPos(Entity e, float partialTicks) {
		// When an entity is removed, it stops moving and its lastRenderX/Y/Z
		// values are no longer updated.
		if (e.isRemoved())
			return e.position();

		double x = Mth.lerp(partialTicks, e.xOld, e.getX());
		double y = Mth.lerp(partialTicks, e.yOld, e.getY());
		double z = Mth.lerp(partialTicks, e.zOld, e.getZ());
		return new Vec3(x, y, z);
	}
}