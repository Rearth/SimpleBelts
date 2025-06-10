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
        
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        
        var targetBlockPos = context.getBlockPos();
        
        var chuteCandidate = context.getWorld().getBlockEntity(targetBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteCandidate.isPresent()) {
            var chuteEntity = chuteCandidate.get();
            
            if (context.getStack().contains(ComponentContent.BELT.get())) {
                context.getPlayer().sendMessage(Text.literal("Created belt!"));
                var storedPos = context.getStack().get(ComponentContent.BELT.get());
                chuteEntity.setTargetFromBelt(storedPos);
                context.getStack().remove(ComponentContent.BELT.get());
            } else {
                context.getPlayer().sendMessage(Text.literal("Stored target position!"));
                context.getStack().set(ComponentContent.BELT.get(), targetBlockPos);
            }
            
        }
        
        return ActionResult.SUCCESS;
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
