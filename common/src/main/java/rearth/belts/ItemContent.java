package rearth.belts;

import rearth.belts.items.BeltItem;
import rearth.belts.items.TooltipBlockItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;

public class ItemContent {
    
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.ITEM);
    
    public static final RegistrySupplier<Item> CHUTE = ITEMS.register(Belts.id("chute"), () -> new TooltipBlockItem(BlockContent.CHUTE_BLOCK.get(), new Item.Settings().arch$tab(ItemGroupContent.BELTS_GROUP)));
    public static final RegistrySupplier<Item> CONVEYOR_SUPPORT = ITEMS.register(Belts.id("conveyor_support"), () -> new TooltipBlockItem(BlockContent.CONVEYOR_SUPPORT_BLOCK.get(), new Item.Settings().arch$tab(ItemGroupContent.BELTS_GROUP)));
    public static final RegistrySupplier<Item> BELT = ITEMS.register(Belts.id("belt"), () -> new BeltItem(new Item.Settings().arch$tab(ItemGroupContent.BELTS_GROUP)));
    
}
