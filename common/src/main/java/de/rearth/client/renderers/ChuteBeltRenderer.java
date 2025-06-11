package de.rearth.client.renderers;

import de.rearth.BlockEntitiesContent;
import de.rearth.blocks.ChuteBlockEntity;
import de.rearth.util.SplineUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity> {
    
    // yeah this isnt the way to store per-instance data, this needs to be moved to the entity maybe? With env annotations?
    private static final HashMap<Long, Vertex[]> cachedModel = new HashMap<>();
    private static final HashMap<Long, Integer> lightmapCache = new HashMap<>();
    
    public record Vertex(float x, float y, float z, float u, float v, BlockPos worldPos) {
        public static Vertex create(Vec3d pos, float u, float v, BlockPos worldPos) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v, worldPos);
        }
    }
    
    private Vertex[] getOrComputeModel(ChuteBlockEntity entity, ChuteBlockEntity target) {
        
//        if (true) {
//            return createSplineModel(entity, target);
//        }
        
        return cachedModel.computeIfAbsent(entity.getPos().asLong(), key -> createSplineModel(entity, target));
    }
    
    private static Vertex[] createSplineModel(ChuteBlockEntity entity, ChuteBlockEntity target) {
        
        var sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
                       .apply(Identifier.of("belts", "block/conveyorbelt"));
        var result = new ArrayList<Vertex>();
        
        // all positions here are in world space
        var conveyorStartPoint = entity.getPos();
        var conveyorEndPoint = entity.getTarget();
        var conveyorStartDir = Vec3d.of(entity.getOwnFacing().getVector());
        var conveyorEndDir = Vec3d.of(target.getOwnFacing().getOpposite().getVector());
        
        var conveyorMidPointsVisual = entity.getMidPointsWithTangents();
        var conveyorStartPointVisual = conveyorStartPoint.toCenterPos().add(conveyorStartDir.multiply(-0.5f));
        var conveyorEndPointVisual = conveyorEndPoint.toCenterPos().add(conveyorEndDir.multiply(0.5f));
        
        var transformedMidPoints = conveyorMidPointsVisual.stream().map(elem -> new Pair<>(elem.getLeft().toCenterPos(), Vec3d.of(elem.getRight().getVector()))).toList();
        var segmentPoints = SplineUtil.getPointPairs(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, transformedMidPoints);
        var totalDist = SplineUtil.getTotalLength(segmentPoints);
        
        // todo move forward in different increments, and adjust segmentSize dynamically based on distance between points?
        var segmentSize = 0.5f;
        var segmentCount = (int) Math.ceil(totalDist / segmentSize);
        var lineWidth = 0.35f;
        
        var beginRight = conveyorStartDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        
        // local space
        var localStart = new Vec3d(0.5f, 0.5, 0.5f).add(conveyorStartDir.multiply(-0.5f));
        var lastRight = localStart.add(beginRight.multiply(lineWidth));
        var lastLeft = localStart.add(beginRight.multiply(-lineWidth));
        
        for (int i = 0; i < segmentCount; i++) {
            
            var last = i == segmentCount - 1;
            var progress = i / (float) segmentCount;
            var nextProgress = (i + 1) / (float) segmentCount;
            var worldPoint = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, progress);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            var worldPointNext = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, nextProgress);
            var localPointNext = worldPointNext.subtract(entity.getPos().toCenterPos());
            
            var direction = localPointNext.subtract(localPoint);
            var cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
            if (last)
                cross = conveyorEndDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
            
            var nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
            var nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);
            
            var worldPos = BlockPos.ofFloored(worldPoint);
            
            // draw a quad from lastLeft -> nextLeft -> nextRight -> lastRight
            
            var lengthRight = nextRight.distanceTo(lastRight);
            lengthRight = Math.clamp(lengthRight, 0, 1);
            var lengthLeft = nextLeft.distanceTo(lastLeft);
            lengthLeft = Math.clamp(lengthLeft, 0, 1);
            
            var uMin = sprite.getFrameU(0);
            var uMax = sprite.getFrameU(1);
            var vMin = sprite.getFrameV(0);
            var vMaxLeft = sprite.getFrameV((float) lengthLeft);
            var vMaxRight = sprite.getFrameV((float) lengthRight);
            
            var botRight = Vertex.create(lastRight, uMin, vMin, worldPos);
            var topRight = Vertex.create(nextRight, uMin, vMaxRight, worldPos);
            var topLeft = Vertex.create(nextLeft, uMax, vMaxLeft, worldPos);
            var botLeft = Vertex.create(lastLeft, uMax, vMin, worldPos);
            
            result.add(topRight);
            result.add(topLeft);
            result.add(botLeft);
            result.add(botRight);
            
            lastRight = nextRight;
            lastLeft = nextLeft;
        }
        
        return result.toArray(Vertex[]::new);
    }
    
    @Override
    public void render(ChuteBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        
        if (entity == null || entity.getWorld() == null) return;
        
        if (entity.getTarget() == null || entity.getTarget().getManhattanDistance(entity.getPos()) < 2) return;
        
        var targetCandidate = entity.getWorld().getBlockEntity(entity.getTarget(), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (targetCandidate.isEmpty()) return;
        
        matrices.push();
        matrices.translate(0, 0.05, 0);
        
        var entry = matrices.peek();
        var modelMatrix = entry.getPositionMatrix();
        var consumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        
        var lightRefreshInterval = 60;
        
        var vertices = getOrComputeModel(entity, targetCandidate.get());
        if (vertices == null) {
            matrices.pop();
            return;
        }
        
        for (var vertex : vertices) {
            
            final var worldPos = vertex.worldPos;
            var worldLight = lightmapCache.computeIfAbsent(vertex.worldPos.asLong(), pos -> WorldRenderer.getLightmapCoordinates(entity.getWorld(), worldPos));
            if (entity.getWorld().getTime() % lightRefreshInterval == 0) {
                lightmapCache.put(vertex.worldPos.asLong(), WorldRenderer.getLightmapCoordinates(entity.getWorld(), vertex.worldPos));
            }
            
            consumer.vertex(modelMatrix, vertex.x, vertex.y, vertex.z)
              .color(Colors.WHITE)
              .texture(vertex.u, vertex.v)
              .normal(entry, 0, 1, 0)
              .light(worldLight)
              .overlay(overlay);
        }
        
        matrices.pop();
        
        // render items
        var renderedItems = getRenderedStacks(entity.getWorld().getTime() + tickDelta);
        
        // all positions here are in world space
        var conveyorStartPoint = entity.getPos();
        var conveyorEndPoint = entity.getTarget();
        var conveyorStartDir = Vec3d.of(entity.getOwnFacing().getVector());
        var conveyorEndDir = Vec3d.of(targetCandidate.get().getOwnFacing().getOpposite().getVector());
        
        var conveyorMidPointsVisual = entity.getMidPointsWithTangents();
        var conveyorStartPointVisual = conveyorStartPoint.toCenterPos().add(conveyorStartDir.multiply(-0.5f));
        var conveyorEndPointVisual = conveyorEndPoint.toCenterPos().add(conveyorEndDir.multiply(0.5f));
        
        for (var itemData : renderedItems.entrySet()) {
            var renderedStack = itemData.getValue();
            var renderedProgress = itemData.getKey();
            var nextProgress = itemData.getKey() + 0.03;
            
            var worldPoint = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, renderedProgress);
            var nextWorldPoint = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, nextProgress);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            
            var forward = nextWorldPoint.subtract(worldPoint).normalize();
            var dot = new Vec3d(1, 0, 0).dotProduct(forward);
            
            matrices.push();
            matrices.translate(localPoint.x, localPoint.y, localPoint.z);
            matrices.translate(0.5f, 0.8f, 0.5f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(-dot)));
            matrices.scale(0.6f, 0.6f, 0.6f);
            
            MinecraftClient.getInstance().getItemRenderer().renderItem(
              renderedStack,
              ModelTransformationMode.FIXED,
              light,
              overlay,
              matrices,
              vertexConsumers,
              entity.getWorld(),
              0
            );
            
            matrices.pop();
        }
        
    }
    
    private Map<Float, ItemStack> getRenderedStacks(float time) {
        
        time  = time % 100;
        
        var progress = time / 100f;
        var progress2 = time / 100f + 0.6f;
        progress2 = progress2 % 1;
        
        var res = new HashMap<Float,ItemStack>();
        res.put(progress, new ItemStack(Items.STICK));
        res.put(progress2, new ItemStack(Items.DIRT));
        return res;
    }
    
    @Override
    public boolean rendersOutsideBoundingBox(ChuteBlockEntity blockEntity) {
        return true;
    }
    
    @Override
    public int getRenderDistance() {
        return 128;
    }
    
    // overrides NF mixin
    public Box getRenderBoundingBox(BlockEntity blockEntity) {
        return new Box(
          Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
        );
    }
}
