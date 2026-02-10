package com.example.LoggerClient.util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;

public enum ShaderPipelines {
	;
	/**
	 * Similar to the RENDERTYPE_LINES Snippet, but without fog.
	 */
	public static final Snippet FOGLESS_LINES_SNIPPET = RenderPipeline
			.builder(RenderPipelines.FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
			.withVertexShader(ResourceLocation.parse("modid:core/fogless_lines"))
			.withFragmentShader(ResourceLocation.parse("modid:core/fogless_lines")).withBlend(BlendFunction.TRANSLUCENT)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES)
			.buildSnippet();
//	/**
//	 * Similar to the LINES ShaderPipeline, but with no fog.
//	 */
//	public static final RenderPipeline DEPTH_TEST_LINES =
//		RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
//			.withLocation(
//				Identifier.of("wurst:pipeline/wurst_depth_test_lines"))
//			.build());

	/**
	 * Similar to the LINES ShaderPipeline, but with no depth test or fog.
	 */
	public static final RenderPipeline ESP_LINES = RenderPipelines.register(RenderPipeline
			.builder(FOGLESS_LINES_SNIPPET).withLocation(ResourceLocation.parse("modid:core/example_esp_lines"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());

//	/**
//	 * Similar to the LINE_STRIP ShaderPipeline, but with no fog.
//	 */
//	public static final RenderPipeline DEPTH_TEST_LINE_STRIP =
//		RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
//			.withLocation(
//				Identifier.of("wurst:pipeline/wurst_depth_test_line_strip"))
//			.withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL,
//				DrawMode.LINE_STRIP)
//			.build());
//	
//	/**
//	 * Similar to the LINE_STRIP ShaderPipeline, but with no depth test or fog.
//	 */
//	public static final RenderPipeline ESP_LINE_STRIP =
//		RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
//			.withLocation(Identifier.of("wurst:pipeline/wurst_esp_line_strip"))
//			.withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL,
//				DrawMode.LINE_STRIP)
//			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
//	
//	/**
//	 * Similar to the DEBUG_QUADS ShaderPipeline, but with culling enabled.
//	 */
//	public static final RenderPipeline QUADS = RenderPipelines
//		.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
//			.withLocation(Identifier.of("wurst:pipeline/wurst_quads"))
//			.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
//			.build());
//	
//	/**
//	 * Similar to the DEBUG_QUADS ShaderPipeline, but with culling enabled
//	 * and no depth test.
//	 */
//	public static final RenderPipeline ESP_QUADS = RenderPipelines
//		.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
//			.withLocation(Identifier.of("wurst:pipeline/wurst_esp_quads"))
//			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
//	
//	/**
//	 * Similar to the DEBUG_QUADS ShaderPipeline, but with no depth test.
//	 */
//	public static final RenderPipeline ESP_QUADS_NO_CULLING = RenderPipelines
//		.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
//			.withLocation(Identifier.of("wurst:pipeline/wurst_esp_quads"))
//			.withCull(false)
//			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).build());
}