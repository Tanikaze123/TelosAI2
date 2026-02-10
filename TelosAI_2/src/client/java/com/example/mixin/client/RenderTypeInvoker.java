package com.example.mixin.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    
    @Invoker("create")
    static RenderType callCreate(String name, RenderSetup setup) {
        // This body is never executed; Mixin replaces it at runtime.
        throw new UnsupportedOperationException();
    }
}