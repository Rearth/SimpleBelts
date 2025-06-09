package de.rearth;

import de.rearth.blocks.ChuteBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;

public class BlockContent {
    
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.BLOCK);
    
    public static final RegistrySupplier<Block> CHUTE_BLOCK = BLOCKS.register(Belts.id("chute"), () -> new ChuteBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));
    
}
