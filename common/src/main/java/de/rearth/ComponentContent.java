package de.rearth;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public class ComponentContent {
    
    public static final DeferredRegister<ComponentType<?>> COMPONENTS = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.DATA_COMPONENT_TYPE);
    
    public static final RegistrySupplier<ComponentType<BlockPos>> BELT_START = COMPONENTS.register(Belts.id("belt_start"),
      () -> ComponentType.<BlockPos>builder().codec(BlockPos.CODEC).packetCodec(BlockPos.PACKET_CODEC).build());
    public static final RegistrySupplier<ComponentType<Direction>> BELT_DIR = COMPONENTS.register(Belts.id("belt_start_dir"),
      () -> ComponentType.<Direction>builder().codec(Direction.CODEC).packetCodec(Direction.PACKET_CODEC).build());
    
    public static final RegistrySupplier<ComponentType<List<BlockPos>>> MIDPOINTS = COMPONENTS.register(Belts.id("belt_midpoints"),
      () -> ComponentType.<List<BlockPos>>builder().codec(BlockPos.CODEC.listOf()).packetCodec(BlockPos.PACKET_CODEC.collect(PacketCodecs.toList())).build());
    
}
