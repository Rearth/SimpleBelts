package rearth.belts.client.renderers;

import rearth.belts.BlockContent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.ComponentContent;
import rearth.belts.items.BeltItem;
import rearth.belts.util.MathHelpers;
import rearth.belts.util.SplineUtil;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.List;

public class BeltOutlineRenderer {
    
    public static void renderPlannedBelt(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer) {
        if (world == null) return;
        
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK)
            return;
        
        var stack = player.getMainHandStack();
        var blockHit = ((BlockHitResult) client.crosshairTarget);
        
        if (!(stack.getItem() instanceof BeltItem)) return;
        
        var hasStart = stack.contains(ComponentContent.BELT_START.get()) && stack.contains(ComponentContent.BELT_DIR.get());
        if (!hasStart) {
            // render just start
            
            var couldBePlaced = false;
            
            // in world space
            var potentialStart = blockHit.getBlockPos().add(blockHit.getSide().getVector());
            var startDir = blockHit.getSide();
            if (blockHit.getSide().getAxis().equals(Direction.Axis.Y))
                startDir = player.getHorizontalFacing().getOpposite();
            
            var startState = world.getBlockState(potentialStart);
            if (startState.isReplaceable() || startState.isAir())
                couldBePlaced = true;
            
            var targetedChuteCandidate = world.getBlockEntity(blockHit.getBlockPos(), BlockEntitiesContent.CHUTE_BLOCK.get());
            if (targetedChuteCandidate.isPresent()) {
                var chuteEntity = targetedChuteCandidate.get();
                startDir = chuteEntity.getOwnFacing();
                potentialStart = blockHit.getBlockPos();
                couldBePlaced = true;
            }
            
            var boxDirectionOffset = Vec3d.of(startDir.getVector()).multiply(0.1 + (world.getTime() % 10) / 20f);
            var bowLower = potentialStart.toCenterPos().subtract(Vec3d.of(startDir.getVector()).multiply(0.4f)).subtract(0.1f, 0.1f, 0.1f);
            var boxUpper = potentialStart.toCenterPos().subtract(Vec3d.of(startDir.getVector()).multiply(0.4f)).add(0.1f, 0.1f, 0.1f).add(boxDirectionOffset);
            var box = new Box(bowLower, boxUpper);
            
            matrixStack.push();
            var cameraPos = camera.getPos();
            matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
            
            WorldRenderer.drawBox(matrixStack, consumer.getBuffer(RenderLayer.getLines()), box, couldBePlaced ? 0.1f : 1f, couldBePlaced ? 0.8f : 0.1f, couldBePlaced ? 0.7f : 0f, 0.9f);
            
            matrixStack.pop();
            
            return;
        }
        
        var startBlockPos = stack.get(ComponentContent.BELT_START.get());
        var startFacing = stack.get(ComponentContent.BELT_DIR.get());
        if (startBlockPos == null || startBlockPos.equals(BlockPos.ORIGIN) || startFacing == null) return;
        
        var startPos = startBlockPos.toCenterPos();
        var startDir = startFacing.getVector();
        var midPoints = BeltItem.getStoredMidpoints(stack, world);
        
        BlockPos endBlockPos;
        Direction endDir;
        
        var endChuteCandidate = world.getBlockEntity(blockHit.getBlockPos(), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (endChuteCandidate.isPresent()) {
            var endChute = endChuteCandidate.get();
            endBlockPos = blockHit.getBlockPos();
            endDir = endChute.getOwnFacing().getOpposite();
        } else if (world.getBlockState(blockHit.getBlockPos()).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            var conveyorPos = blockHit.getBlockPos();
            var conveyorFacing = world.getBlockState(blockHit.getBlockPos()).get(HorizontalFacingBlock.FACING);
            var reversedConveyorFacing = conveyorFacing.getVector().multiply(-1);
            var lastEnd = midPoints.isEmpty() ? startBlockPos : midPoints.getLast().getLeft();
            var distA = conveyorPos.add(conveyorFacing.getVector()).getSquaredDistance(lastEnd);
            var distB = conveyorPos.add(reversedConveyorFacing).getSquaredDistance(lastEnd);
            endDir = distB < distA ? conveyorFacing : conveyorFacing.getOpposite();
            endBlockPos = conveyorPos;
        } else {
            endBlockPos = blockHit.getBlockPos().add(blockHit.getSide().getVector());
            endDir = blockHit.getSide().getOpposite();
            if (endDir.getAxis().isVertical())
                endDir = player.getHorizontalFacing().getOpposite();
        }
        
        var visualEndPos = endBlockPos.toCenterPos();
        var visualEndDir = endDir.getVector();
        
        matrixStack.push();
        var cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        var linePoints = getPositionsAlongLine(startPos, visualEndPos, startDir, visualEndDir, midPoints);
        
        var lastForward = Vec3d.of(startDir).normalize();
        var lastCenter = Vec3d.ZERO;
        if (!linePoints.isEmpty())
            lastCenter = linePoints.getFirst();
        
        for (var center : linePoints) {
            var lineRadius = 0.05f;
            
            var newForward = center.subtract(lastCenter).normalize();
            
            // this only happens for the first one
            if (center.equals(lastCenter))
                newForward = lastForward;
            
            var curveFactor = newForward.distanceTo(lastForward);
            var color = new Vec3d(1, 1, 1);
            
            if (curveFactor > 0.25f) {
                color = new Vec3d(1, 0.6f, 0.2f);
            }
            
            if (curveFactor > 0.43f) {
                color = new Vec3d(1, 0, 0);
            }
            
            lastCenter = center;
            lastForward = MathHelpers.lerp(lastForward, newForward, 0.3f);
            
            WorldRenderer.drawBox(matrixStack, consumer.getBuffer(RenderLayer.getLines()), center.x - lineRadius, center.y - lineRadius, center.z - lineRadius, center.x + lineRadius, center.y + lineRadius, center.z + lineRadius, (float) color.x, (float) color.y, (float) color.z, 0.8f);
        }
        
        matrixStack.pop();
        
    }
    
    private static List<Vec3d> getPositionsAlongLine(Vec3d from, Vec3d to, Vec3i startDir, Vec3i endDir, List<Pair<BlockPos, Direction>> midpoints) {
        var stepSize = 0.1f;
        
        var result = new ArrayList<Vec3d>();
        
        var transformedMidPoints = midpoints.stream().map(elem -> new Pair<>(elem.getLeft().toCenterPos(), Vec3d.of(elem.getRight().getVector()))).toList();
        var segmentPoints = SplineUtil.getPointPairs(from, Vec3d.of(startDir), to, Vec3d.of(endDir), transformedMidPoints);
        
        var dist = SplineUtil.getTotalLength(segmentPoints);
        
        for (var i = 0f; i < dist; i += stepSize) {
            var progress = i / dist;
            var center = SplineUtil.getPositionOnSpline(from, Vec3d.of(startDir), to, Vec3d.of(endDir), midpoints, progress);
            result.add(center);
        }
        
        return result;
        
    }
    
}
