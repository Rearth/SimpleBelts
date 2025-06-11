package de.rearth.items;

import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.ComponentContent;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BeltItem extends Item {
    
    public BeltItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        
        if (!world.isClient() && user.isSneaking()) {
            var stack = user.getStackInHand(hand);
            stack.remove(ComponentContent.MIDPOINTS.get());
            stack.remove(ComponentContent.BELT.get());
            user.sendMessage(Text.literal("Reset belt!"));
        }
        
        return super.use(world, user, hand);
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
                var midPoints = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), new ArrayList<BlockPos>());
                var usedMidPoints = new ArrayList<>(midPoints); // ensure we have a mutable copy
                Collections.reverse(usedMidPoints);
                chuteEntity.assignFromBeltItem(storedPos, usedMidPoints);
                stack.remove(ComponentContent.BELT.get());
                stack.remove(ComponentContent.MIDPOINTS.get());
            } else {
                context.getPlayer().sendMessage(Text.literal("Stored start position!"));
                stack.set(ComponentContent.BELT.get(), targetBlockPos);
            }
        }
        
        var holderCandidate = context.getWorld().getBlockState(targetBlockPos);
        if (holderCandidate.getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            var list = new ArrayList<BlockPos>();
            if (stack.contains(ComponentContent.MIDPOINTS.get())) {
                list.addAll(stack.get(ComponentContent.MIDPOINTS.get()));
            }
            
            if (list.contains(targetBlockPos)) {
                context.getPlayer().sendMessage(Text.literal("Midpoint already stored!"));
                return ActionResult.FAIL;
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
    
    public static List<Pair<BlockPos, Direction>> getStoredMidpoints(ItemStack stack, World world) {
        var res = new ArrayList<Pair<BlockPos, Direction>>();
        if (stack.contains(ComponentContent.MIDPOINTS.get())) {
            stack.get(ComponentContent.MIDPOINTS.get())
              .stream()
              .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
              .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING))).forEachOrdered(res::add);
        }
        
        return res;
    }
}
