/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gl.PostEffectProcessor
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.WorldRenderer
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.entity.Entity
 *  org.lwjgl.opengl.GL11
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.ModifyArg
 *  org.spongepowered.asm.mixin.injection.Redirect
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package dev.stella.asm.mixins;

import dev.stella.stella;
import dev.stella.api.utils.Wrapper;
import dev.stella.core.impl.ShaderManager;
import dev.stella.mod.modules.impl.player.Freecam;
import dev.stella.mod.modules.impl.render.Chams;
import dev.stella.mod.modules.impl.render.ShaderModule;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={WorldRenderer.class})
public abstract class MixinWorldRenderer {
    @Unique
    boolean renderingChams = false;
    @Unique
    boolean renderingEntity = false;

    @Inject(method={"renderEntity"}, at={@At(value="HEAD")})
    private void injectChamsForEntity(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (Chams.INSTANCE.isOn() && Chams.INSTANCE.throughWall.getValue()) {
            if (Chams.INSTANCE.chams(entity)) {
                if (this.renderingEntity) {
                    Wrapper.mc.getBufferBuilders().getEntityVertexConsumers().draw();
                    this.renderingEntity = false;
                }
                // Disable depth test to render entity through walls
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                // Also disable depth mask
                GL11.glDepthMask(false);
                this.renderingChams = true;
            } else {
                this.renderingEntity = true;
            }
        }
    }

    @Inject(method={"renderEntity"}, at={@At(value="RETURN")})
    private void injectChamsForEntityPost(Entity entity, double cameraX, double cameraY, double cameraZ, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (Chams.INSTANCE.isOn() && Chams.INSTANCE.throughWall.getValue() && this.renderingChams) {
            Wrapper.mc.getBufferBuilders().getEntityVertexConsumers().draw();
            // Re-enable depth test and depth mask
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            this.renderingChams = false;
        }
    }

    @Redirect(method={"render"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/gl/PostEffectProcessor;render(F)V", ordinal=0), require=0)
    void replaceShaderHook(PostEffectProcessor instance, float tickDelta) {
        ShaderManager.Shader shaders = ShaderModule.INSTANCE.mode.getValue();
        if (ShaderModule.INSTANCE.isOn() && Wrapper.mc.world != null) {
            stella.SHADER.setupShader(shaders, stella.SHADER.getShaderOutline(shaders));
        } else {
            instance.render(tickDelta);
        }
    }

    @ModifyArg(method={"render"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V"), index=3)
    private boolean renderSetupTerrainModifyArg(boolean spectator) {
        return Freecam.INSTANCE.isOn() || spectator;
    }
}

