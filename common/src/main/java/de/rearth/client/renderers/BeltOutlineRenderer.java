package de.rearth.client.renderers;

import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.ComponentContent;
import de.rearth.items.BeltItem;
import de.rearth.util.SplineUtil;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class BeltOutlineRenderer {
    
    public static void renderPlannedBelt(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer) {
        if (world == null) return;
        
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK)
            return;
        
        var itemStack = player.getMainHandStack();
        
        if (!(itemStack.getItem() instanceof BeltItem) || !itemStack.contains(ComponentContent.BELT.get())) return;
        var startBlockPos = itemStack.get(ComponentContent.BELT.get());
        if (startBlockPos == null || startBlockPos.equals(BlockPos.ORIGIN)) return;
        
        var startPos = startBlockPos.toCenterPos();
        var endBlockPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        var endPos = endBlockPos.toCenterPos();
        
        var startCandidate = world.getBlockEntity(startBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        var endCandidate = world.getBlockEntity(endBlockPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        var endState = world.getBlockState(endBlockPos);
        
        if (startCandidate.isEmpty()) return;
        var startDir = startCandidate.get().getOwnFacing().getVector();
        
        var midPoints = BeltItem.getStoredMidpoints(itemStack, world);
        
        Vec3i endDir;
        if (endCandidate.isPresent()) {
            endDir = endCandidate.get().getOwnFacing().getOpposite().getVector();
        } else if (endState.getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get())) {
            endDir = endState.get(HorizontalFacingBlock.FACING).getVector();
            var reversedEndDir = endDir.multiply(-1);
            var lastEnd = midPoints.isEmpty() ? startBlockPos : midPoints.getLast().getLeft();
            var distA = endBlockPos.add(endDir).getSquaredDistance(lastEnd);
            var distB = endBlockPos.add(reversedEndDir).getSquaredDistance(lastEnd);
            if (distB > distA) endDir = reversedEndDir;
        } else {
            endDir = new Vec3i(0, 0, 0);
        }
        
        matrixStack.push();
        var cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        var linePoints = getPositionsAlongLine(startPos, endPos, startDir, endDir, midPoints);
        
        for (var center : linePoints) {
            var lineRadius = 0.05f;
            WorldRenderer.drawBox(matrixStack, consumer.getBuffer(RenderLayer.getLines()), center.x - lineRadius, center.y - lineRadius, center.z - lineRadius, center.x + lineRadius, center.y + lineRadius, center.z + lineRadius, 1f, 1f, 1f, 0.8f);
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
