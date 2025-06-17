package de.rearth.items;

import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class TooltipBlockItem extends BlockItem {
    
    public TooltipBlockItem(Block block, Settings settings) {
        super(block, settings);
    }
    
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        
        var showExtra = Screen.hasControlDown();
        if (showExtra) {
            var langKey = stack.getItem().getTranslationKey();
            tooltip.add(Text.translatable(langKey + ".tooltip").formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable("message.belts.show_extra").formatted(Formatting.GRAY, Formatting.ITALIC));
        }
        
        super.appendTooltip(stack, context, tooltip, type);
    }
}
