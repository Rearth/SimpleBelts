package de.rearth.client.renderers;

import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.ComponentContent;
import de.rearth.items.BeltItem;
import de.rearth.util.Spline;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        
        if (startCandidate.isEmpty()) return;
        var startDir = startCandidate.get().getOwnFacing().getVector();
        
        Vec3i endDir;
        if (endCandidate.isPresent()) {
            endDir = endCandidate.get().getOwnFacing().getOpposite().getVector();
        } else {
            endDir = new Vec3i(1, 0, 0);
        }
        
        matrixStack.push();
        var cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        
        var midPoints = getStoredMidpoints(itemStack, world);
        
        List<Vec3d> linePoints;
        if (midPoints.isEmpty()) {
            linePoints = getPositionsAlongLine(startPos, endPos, startDir, endDir, null);
        } else {
            var orderedPoints = orderMidPoints(startPos, endPos, midPoints);
            linePoints = getPositionsAlongLine(startPos, endPos, startDir, endDir, orderedPoints.toArray(Vec3d[]::new));
            
            for (var center : orderedPoints) {
                var lineRadius = 0.07f;
                WorldRenderer.drawBox(matrixStack, consumer.getBuffer(RenderLayer.getLines()), center.x - lineRadius, center.y - lineRadius, center.z - lineRadius, center.x + lineRadius, center.y + lineRadius, center.z + lineRadius, 0f, 0f, 1f, 0.8f);
            }
        }
        
        for (var center : linePoints) {
            var lineRadius = 0.05f;
            WorldRenderer.drawBox(matrixStack, consumer.getBuffer(RenderLayer.getLines()), center.x - lineRadius, center.y - lineRadius, center.z - lineRadius, center.x + lineRadius, center.y + lineRadius, center.z + lineRadius, 1f, 1f, 1f, 0.8f);
        }
        
        matrixStack.pop();
        
    }
    
    @NotNull
    private static List<Vec3d> orderMidPoints(Vec3d startPos, @Nullable Vec3d endPos, List<Pair<BlockPos, Direction>> midPoints) {
        var res = new ArrayList<Vec3d>();
        res.add(startPos);
        
        // calculate potential total length of line, pick the shorter one
        for (var data : midPoints) {
            var pair = getMidpointPositions(data.getLeft(), data.getRight());
            
            var totalListA = new ArrayList<>(res);
            var totalListB = new ArrayList<>(res);
            
            totalListA.add(pair.getLeft());
            totalListA.add(pair.getRight());
            
            // inverted order
            totalListB.add(pair.getRight());
            totalListB.add(pair.getLeft());
            
            if (endPos != null) {
                totalListA.add(endPos);
                totalListB.add(endPos);
            }
            
            var lengthA = getListLength(totalListA);
            var lengthB = getListLength(totalListB);
            
            if (lengthA < lengthB) {
                res.add(pair.getLeft());
                res.add(pair.getRight());
            } else {
                res.add(pair.getRight());
                res.add(pair.getLeft());
            }
            // res.add(data.getLeft().toCenterPos());
            
        }
        
        res.removeFirst();
        
        
        return res;
    }
    
    private static float getListLength(List<Vec3d> positions) {
        if (positions.size() <= 1) return 0f;
        var sum = 0f;
        for (int i = 1; i < positions.size(); i++) {
            var last = positions.get(i - 1);
            var current = positions.get(i);
            sum += (float) last.squaredDistanceTo(current);
        }
        
        return sum;
    }
    
    private static List<Vec3d> getPositionsAlongLine(Vec3d from, Vec3d to, Vec3i startDir, Vec3i endDir, @Nullable Vec3d[] midpoints) {
        var stepSize = 0.05f;
        
        var result = new ArrayList<Vec3d>();
        
        var dist = from.distanceTo(to);
        for (var i = 0f; i < dist; i += stepSize) {
            var progress = i / dist;
            
            Vec3d center;
            if (midpoints == null) {
                center = Spline.getPointOnCatmullRomSpline((float) progress, from, Vec3d.of(startDir), to, Vec3d.of(endDir), 1f, 5f);
                
            } else {
                center = Spline.getPointOnCatmullRomSpline((float) progress, from, Vec3d.of(startDir), to, Vec3d.of(endDir), 0f, 5f, midpoints);
            }
            result.add(center);
        }
        
        return result;
        
    }
    
    public static Pair<Vec3d, Vec3d> getMidpointPositions(BlockPos pos, Direction facing) {
        var forward = facing.getVector();
        var posA = pos.toCenterPos().add(Vec3d.of(forward).multiply(0.25));
        var posB = pos.toCenterPos().add(Vec3d.of(forward).multiply(-0.25));
        return new Pair<>(posA, posB);
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
