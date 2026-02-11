package com.example.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    
    @Invoker("create")
    static RenderType callCreate(String name,
            VertexFormat format, 
            VertexFormat.Mode mode, 
            int bufferSize, 
            boolean affectsOutline, 
            RenderType.CompositeState state) {
        // This body is never executed; Mixin replaces it at runtime.
        throw new UnsupportedOperationException();
    }
}