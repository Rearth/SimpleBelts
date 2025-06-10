package de.rearth.items;

import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.ComponentContent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BeltItem extends Item {
    
    public BeltItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        
        var stack = context.getStack();
        
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        
        var targetBlockPos = context.getBlockPos();
        
        var chuteCandidate = context.getWorld().getBlockEntity(targetBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteCandidate.isPresent()) {
            var chuteEntity = chuteCandidate.get();
            
            if (stack.contains(ComponentContent.BELT.get())) {
                context.getPlayer().sendMessage(Text.literal("Created belt!"));
                var storedPos = stack.get(ComponentContent.BELT.get());
                chuteEntity.setTargetFromBelt(storedPos);
                stack.remove(ComponentContent.BELT.get());
                stack.remove(ComponentContent.MIDPOINTS.get());
            } else {
                context.getPlayer().sendMessage(Text.literal("Stored target position!"));
                stack.set(ComponentContent.BELT.get(), targetBlockPos);
            }
        }
        
        var holderCandidate = context.getWorld().getBlockState(targetBlockPos);
        if (holderCandidate.getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            var list = new ArrayList<BlockPos>();
            if (stack.contains(ComponentContent.MIDPOINTS.get())) {
                list.addAll(stack.get(ComponentContent.MIDPOINTS.get()));
            }
            list.add(targetBlockPos);
            stack.set(ComponentContent.MIDPOINTS.get(), list);
            context.getPlayer().sendMessage(Text.literal("Stored midpoint!"));
        }
        
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        
        if (stack.contains(ComponentContent.BELT.get())) {
            var targetPos = stack.get(ComponentContent.BELT.get());
            tooltip.add(Text.literal(targetPos.toShortString()));
        }
        
        if (stack.contains(ComponentContent.MIDPOINTS.get())) {
            tooltip.add(Text.literal("Midpoints: "));
            for (var midPoint : stack.get(ComponentContent.MIDPOINTS.get())) {
                tooltip.add(Text.literal(midPoint.toShortString()));
            }
        }
        
        super.appendTooltip(stack, context, tooltip, type);
    }
}
