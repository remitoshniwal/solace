package com.solacemod.solace.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.ModEntities;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Registers how Solace looks, the push-to-talk key, and the screen-glitch overlay. */
@EventBusSubscriber(modid = Solace.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping TALK_KEY = new KeyMapping("key.solace.talk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.solace");

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SOLACE.get(), SolaceRenderer::new);
        event.registerEntityRenderer(ModEntities.WATCHER.get(), WatcherRenderer::new);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SolaceModels.BODY, SolaceModels::createBody);
        event.registerLayerDefinition(SolaceModels.BODY_AURA, SolaceModels::createAura);
        event.registerLayerDefinition(SolaceModels.LANTERN, SolaceModels::createLantern);
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(TALK_KEY);
    }

    @SubscribeEvent
    public static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Solace.id("glitch"), GlitchOverlay::render);
    }
}
