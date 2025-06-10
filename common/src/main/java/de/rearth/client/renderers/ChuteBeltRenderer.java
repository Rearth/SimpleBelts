package de.rearth.client.renderers;

import de.rearth.BlockEntitiesContent;
import de.rearth.blocks.ChuteBlockEntity;
import de.rearth.util.Spline;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;

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
        
        if (true) {
            return createSplineModel(entity, target);
        }
        
        return cachedModel.computeIfAbsent(entity.getPos().asLong(), key -> createSplineModel(entity, target));
    }
    
    private static Vertex[] createSplineModel(ChuteBlockEntity entity, ChuteBlockEntity target) {
        
        var sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
                       .apply(Identifier.of("minecraft", "block/stone"));
        var result = new ArrayList<Vertex>();
        
        // all positions here are in world space
        var conveyorStartPoint = entity.getPos();
        var conveyorMidPoints = new ArrayList<BlockPos>();
        var conveyorEndPoint = entity.getTarget();
        var conveyorStartDir = Vec3d.of(entity.getOwnFacing().getVector());
        var conveyorEndDir = Vec3d.of(target.getOwnFacing().getOpposite().getVector());
        
        var conveyorMidPointsVisual = conveyorMidPoints.stream().map(BlockPos::toCenterPos).toArray(Vec3d[]::new);
        var conveyorStartPointVisual = conveyorStartPoint.toCenterPos().add(conveyorStartDir.multiply(-0.5f));
        var conveyorEndPointVisual = conveyorEndPoint.toCenterPos().add(conveyorEndDir.multiply(0.5f));
        
        // calculate segment count / total size
        var allPoints = new ArrayList<BlockPos>();
        allPoints.add(conveyorStartPoint);
        allPoints.addAll(conveyorMidPoints);
        allPoints.add(conveyorEndPoint);
        var totalDist = 0f;
        for (int i = 0; i < allPoints.size() - 1; i++) {
            totalDist += (float) allPoints.get(i).getManhattanDistance(allPoints.get(i + 1));
        }
        
        var segmentSize = 0.95f;
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
            progress = Math.clamp(progress, 0, 0.9999f);
            var nextProgress = (i + 1) / (float) segmentCount;
            nextProgress = Math.clamp(nextProgress, 0, 0.9999f);
            var worldPoint = Spline.getPointOnCatmullRomSpline(progress, conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            var worldPointNext = Spline.getPointOnCatmullRomSpline(nextProgress, conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual);
            var localPointNext = worldPointNext.subtract(entity.getPos().toCenterPos());
            
            var direction = localPointNext.subtract(localPoint);
            var cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
            if (last)
                cross = conveyorEndDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
            
            var nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
            var nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);
            
            var worldPos = BlockPos.ofFloored(worldPoint);
            
            // draw a quad from lastLeft -> nextLeft -> nextRight -> lastRight
            
            var uMin = sprite.getFrameU(0);
            var uMax = sprite.getFrameU(1);
            var vMin = sprite.getFrameV(0);
            var vMax = sprite.getFrameV(1);
            
            var botRight = Vertex.create(lastRight, uMax, vMax, worldPos);
            var topRight = Vertex.create(nextRight, uMin, vMax, worldPos);
            var topLeft = Vertex.create(nextLeft, uMin, vMin, worldPos);
            var botLeft = Vertex.create(lastLeft, uMax, vMin, worldPos);
            
            result.add(botRight);
            result.add(topRight);
            result.add(topLeft);
            result.add(botLeft);
            
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
