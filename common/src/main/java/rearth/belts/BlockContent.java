package rearth.belts;

import rearth.belts.blocks.ChuteBlock;
import rearth.belts.blocks.ConveyorSupportBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;

public class BlockContent {
    
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.BLOCK);
    
    public static final RegistrySupplier<Block> CHUTE_BLOCK = BLOCKS.register(Belts.id("chute"), () -> new ChuteBlock(AbstractBlock.Settings.copy(Blocks.GLASS).sounds(BlockSoundGroup.DRIPSTONE_BLOCK).nonOpaque()));
    public static final RegistrySupplier<Block> CONVEYOR_MODEL = BLOCKS.register(Belts.id("conveyor_model"), () -> new Block(AbstractBlock.Settings.copy(Blocks.GLASS).nonOpaque()));
    public static final RegistrySupplier<Block> CONVEYOR_SUPPORT_BLOCK = BLOCKS.register(Belts.id("conveyor_support"), () -> new ConveyorSupportBlock(AbstractBlock.Settings.copy(Blocks.GLASS).sounds(BlockSoundGroup.DRIPSTONE_BLOCK).nonOpaque()));
    
}
