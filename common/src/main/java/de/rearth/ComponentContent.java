package de.rearth;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;

public class ComponentContent {
    
    public static final DeferredRegister<ComponentType<?>> COMPONENTS = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.DATA_COMPONENT_TYPE);
    
    public static final RegistrySupplier<ComponentType<BlockPos>> BELT = COMPONENTS.register(Belts.id("belt_target"),
      () -> ComponentType.<BlockPos>builder().codec(BlockPos.CODEC).packetCodec(BlockPos.PACKET_CODEC).build());
    
}
