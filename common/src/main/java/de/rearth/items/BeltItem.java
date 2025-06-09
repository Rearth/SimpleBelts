package de.rearth.items;

import de.rearth.BlockEntitiesContent;
import de.rearth.ComponentContent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.List;

public class BeltItem extends Item {
    
    public BeltItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        
        var targetBlockPos = context.getBlockPos();
        
        var chuteCandidate = context.getWorld().getBlockEntity(targetBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteCandidate.isPresent()) {
            var chuteEntity = chuteCandidate.get();
            System.out.println(chuteEntity.getPos());
            context.getStack().set(ComponentContent.BELT.get(), targetBlockPos);
        }
        
        return super.useOnBlock(context);
    }
    
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        
        if (stack.contains(ComponentContent.BELT.get())) {
            var targetPos = stack.get(ComponentContent.BELT.get());
            tooltip.add(Text.literal(targetPos.toShortString()));
        }
        
        super.appendTooltip(stack, context, tooltip, type);
    }
}
