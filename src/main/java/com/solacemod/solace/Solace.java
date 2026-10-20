package com.solacemod.solace;

import com.mojang.logging.LogUtils;
import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.ModEntities;
import com.solacemod.solace.network.ModNetwork;
import com.solacemod.solace.util.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** The mod's starting point. NeoForge creates this once when the game loads. */
@Mod(Solace.MODID)
public final class Solace {
    public static final String MODID = "solace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Solace(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        modBus.addListener(ModNetwork::register);

        container.registerConfig(ModConfig.Type.COMMON, SolaceConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // Game events (chat, ticks, logins, commands) are registered with @EventBusSubscriber in the event package.
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
