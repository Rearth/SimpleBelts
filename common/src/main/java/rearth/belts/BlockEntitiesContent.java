package rearth.belts;

import rearth.belts.blocks.ChuteBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.RegistryKeys;

public class BlockEntitiesContent {
    
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.BLOCK_ENTITY_TYPE);
    
    
    public static final RegistrySupplier<BlockEntityType<ChuteBlockEntity>> CHUTE_BLOCK = TYPES.register(Belts.id("chute"), () -> BlockEntityType.Builder.create(ChuteBlockEntity::new, BlockContent.CHUTE_BLOCK.get()).build(null));
    
}
