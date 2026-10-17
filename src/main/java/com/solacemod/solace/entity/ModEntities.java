package com.solacemod.solace.entity;

import com.solacemod.solace.Solace;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's creatures with the game. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Solace.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SolaceEntity>> SOLACE = ENTITIES.register("solace",
            () -> EntityType.Builder.of(SolaceEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("solace"));

    public static final DeferredHolder<EntityType<?>, EntityType<WatcherEntity>> WATCHER = ENTITIES.register("watcher",
            () -> EntityType.Builder.of(WatcherEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F)
                    .eyeHeight(1.7F)
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("watcher"));

    private ModEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SOLACE.get(), SolaceEntity.createAttributes().build());
        event.put(WATCHER.get(), WatcherEntity.createAttributes().build());
    }
}
