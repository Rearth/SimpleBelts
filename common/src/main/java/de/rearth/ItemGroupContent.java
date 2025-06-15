package de.rearth;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public class ItemGroupContent {
    
    public static final DeferredRegister<ItemGroup> GROUPS = DeferredRegister.create(Belts.MOD_ID, RegistryKeys.ITEM_GROUP);
    
    public static final RegistrySupplier<ItemGroup> BELTS_GROUP = GROUPS.register(Belts.id("group"), () -> CreativeTabRegistry.create(
      Text.translatable("itemgroup.belts.items"),
      () -> new ItemStack(ItemContent.BELT.get())
    ));
}
