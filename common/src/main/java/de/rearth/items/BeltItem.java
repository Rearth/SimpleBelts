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
            stack.remove(ComponentContent.BELT_START.get());
            stack.remove(ComponentContent.BELT_DIR.get());
            user.sendMessage(Text.literal("Reset belt!"));
        }
        
        return super.use(world, user, hand);
    }
    
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        
        var stack = context.getStack();
        
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        
        var targetBlockPos = context.getBlockPos();
        
        var hasStart = stack.contains(ComponentContent.BELT_START.get()) && stack.contains(ComponentContent.BELT_DIR.get());
        
        var chuteCandidate = context.getWorld().getBlockEntity(targetBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteCandidate.isPresent()) {
            var chuteEntity = chuteCandidate.get();
            
            if (hasStart) {
                // create end
                var startPos = stack.get(ComponentContent.BELT_START.get());
                var startDir = stack.get(ComponentContent.BELT_DIR.get());
                var midPoints = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), new ArrayList<BlockPos>());
                var endPos = targetBlockPos;
                var endDir = chuteEntity.getOwnFacing();
                
                createBelt(startPos, startDir, midPoints, endPos, endDir, context.getWorld());
                
                stack.remove(ComponentContent.MIDPOINTS.get());
                stack.remove(ComponentContent.BELT_START.get());
                stack.remove(ComponentContent.BELT_DIR.get());
                
                context.getPlayer().sendMessage(Text.literal("Created Belt!"));
                
            } else {
                // assign manual start
                var startPos = targetBlockPos;
                var startDir = chuteEntity.getOwnFacing();
                
                stack.set(ComponentContent.BELT_START.get(), startPos);
                stack.set(ComponentContent.BELT_DIR.get(), startDir);
                
                context.getPlayer().sendMessage(Text.literal("Stored Start!"));
            }
            
            return ActionResult.SUCCESS;
        }
        
        var supportCandidate = context.getWorld().getBlockState(targetBlockPos);
        if (hasStart && supportCandidate.getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            // store midpoint
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
            
            return ActionResult.SUCCESS;
        }
        
        // at this point, no existing midpoint or start is being targeted, so we try to store the potential positions of a new one.
        // this is done by taking the block on the surface of the target. For grounds/walls, the direction is determined by the player. Otherwise facing away from the target.
        var targetDir = context.getSide();
        targetBlockPos = targetBlockPos.add(context.getSide().getVector());
        if (context.getSide().getAxis().equals(Direction.Axis.Y)) {
            targetDir = context.getHorizontalPlayerFacing();
        }
        
        var candidateState = context.getWorld().getBlockState(targetBlockPos);
        if (candidateState.isReplaceable() || candidateState.isAir()) {
            // create either new start or end at this position
            if (hasStart) {
                // create end
                var startPos = stack.get(ComponentContent.BELT_START.get());
                var startDir = stack.get(ComponentContent.BELT_DIR.get());
                var midPoints = stack.getOrDefault(ComponentContent.MIDPOINTS.get(), new ArrayList<BlockPos>());
                var endPos = targetBlockPos;
                var endDir = targetDir;
                
                createBelt(startPos, startDir, midPoints, endPos, endDir, context.getWorld());
                
                stack.remove(ComponentContent.MIDPOINTS.get());
                stack.remove(ComponentContent.BELT_START.get());
                stack.remove(ComponentContent.BELT_DIR.get());
                
                context.getPlayer().sendMessage(Text.literal("Created Belt!"));
                
            } else {
                var startPos = targetBlockPos;
                var startDir = targetDir.getOpposite();
                
                stack.set(ComponentContent.BELT_START.get(), startPos);
                stack.set(ComponentContent.BELT_DIR.get(), startDir);
                
                context.getPlayer().sendMessage(Text.literal("Stored Start!"));
            }
        }
        
        return ActionResult.SUCCESS;
    }
    
    // creates optional chute entities at start and end
    @SuppressWarnings("OptionalIsPresent")
    private void createBelt(BlockPos start, Direction startDir, List<BlockPos> supports, BlockPos end, Direction endDir, World world) {
        
        // optionally create start entity
        var startState = world.getBlockState(start);
        var startCandidate = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (startCandidate.isEmpty() && (startState.isReplaceable() || startState.isAir())) {
            world.setBlockState(start, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, startDir));
            startCandidate = world.getBlockEntity(start, BlockEntitiesContent.CHUTE_BLOCK.get());
        }
        
        // optionally create end entity
        var endState = world.getBlockState(end);
        var endCandidate = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (endCandidate.isEmpty() && (endState.isReplaceable() || endState.isAir())) {
            world.setBlockState(end, BlockContent.CHUTE_BLOCK.get().getDefaultState().with(HorizontalFacingBlock.FACING, endDir));
            endCandidate = world.getBlockEntity(end, BlockEntitiesContent.CHUTE_BLOCK.get());
        }
        
        // create belt in block entity
        if (startCandidate.isPresent() &&  endCandidate.isPresent()) {
            var startEntity = startCandidate.get();
            startEntity.assignFromBeltItem(end, supports);
        }
    }
    
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        
        if (stack.contains(ComponentContent.BELT_START.get())) {
            var targetPos = stack.get(ComponentContent.BELT_START.get());
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
