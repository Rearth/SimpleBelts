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
import net.minecraft.client.texture.Sprite;
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
    private static final HashMap<Long, Integer> lightmapCache = new HashMap<>();
    
    public record Vertex(float x, float y, float z, float u, float v) {
        public static Vertex create(Vec3d pos, float u, float v) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
        }
    }
    
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, BlockPos worldPos) {}
    
    private Quad[] getOrComputeModel(ChuteBlockEntity entity, ChuteBlockEntity target) {
    
//        if (true) {
//            return createSplineModel(entity, target);
//        }
        
        if (entity.renderedModel == null)
            entity.renderedModel = createSplineModel(entity, target);
        
        return entity.renderedModel;
    }
    
    private static Quad[] createSplineModel(ChuteBlockEntity entity, ChuteBlockEntity target) {
        
        var sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
                       .apply(Identifier.of("belts", "block/conveyorbelt"));
        var result = new ArrayList<Quad>();
        
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
        var segmentSize = 0.75f;
        var segmentCount = (int) Math.ceil(totalDist / segmentSize);
        var lineWidth = 0.4f;
        
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
            
            var worldPos = BlockPos.ofFloored(worldPoint);
            
            var direction = localPointNext.subtract(localPoint);
            var cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
            if (last)
                cross = conveyorEndDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
            
            var nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
            var nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);
            
            var dirA = lastLeft.subtract(lastRight).normalize();
            var dirB = nextLeft.subtract(nextRight).normalize();
            var curveStrength = 1 - Math.abs(dirA.dotProduct(dirB));
            
            // split into 2 segments for strong curved segments
            if (curveStrength > 0.025) {
                var midProgress = (i + 0.5f) / (float) segmentCount;
                var worldPointMid = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, midProgress);
                var localPointMid = worldPointMid.subtract(entity.getPos().toCenterPos());
                
                var directionMid = localPointMid.subtract(localPoint);
                var crossMid = directionMid.crossProduct(new Vec3d(0, 1, 0)).normalize();
                
                var midRight = localPointMid.add(crossMid.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
                var midLeft = localPointMid.add(crossMid.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);
                
                direction = localPointNext.subtract(localPointMid);
                cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
                if (last)
                    cross = conveyorEndDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
                
                nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
                nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);
                
                addSegmentVertices(midRight, lastRight, midLeft, lastLeft, sprite, worldPos, result,0, 0.5f);
                addSegmentVertices(nextRight, midRight, nextLeft, midLeft, sprite, worldPos, result, 0.5f, 1f);
            } else {
                addSegmentVertices(nextRight, lastRight, nextLeft, lastLeft, sprite, worldPos, result, 0, 1);
            }
            lastRight = nextRight;
            lastLeft = nextLeft;
            
            // draw a quad from lastLeft -> nextLeft -> nextRight -> lastRight
        }
        
        return result.toArray(Quad[]::new);
    }
    
    private static void addSegmentVertices(Vec3d nextRight, Vec3d lastRight, Vec3d nextLeft, Vec3d lastLeft, Sprite sprite, BlockPos worldPos, ArrayList<Quad> result, float vStart, float vEnd) {

        var skirtHeight = 0.15f;
        
        // top quad
        var uMin = sprite.getFrameU(0);
        var uMax = sprite.getFrameU(1);
        var vMin = sprite.getFrameV(vStart);
        var vMax = sprite.getFrameV(vEnd);
        
        var botRight = Vertex.create(lastRight, uMin, vMin);
        var topRight = Vertex.create(nextRight, uMin, vMax);
        var topLeft = Vertex.create(nextLeft, uMax, vMax);
        var botLeft = Vertex.create(lastLeft, uMax, vMin);
        
        var quad = new Quad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);
        
        // right skirt
        uMin = sprite.getFrameU(0);
        uMax = sprite.getFrameU(2/16f);
        vMin = sprite.getFrameV(0);
        vMax = sprite.getFrameV(1);
        
        topRight = Vertex.create(nextRight.add(0, -skirtHeight, 0), uMax, vMax);
        topLeft = Vertex.create(nextRight, uMin, vMax);
        botLeft = Vertex.create(lastRight, uMin, vMin);
        botRight = Vertex.create(lastRight.add(0, -skirtHeight, 0), uMax, vMin);
        
        quad = new Quad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);
        
        // left skirt
        uMin = sprite.getFrameU(0);
        uMax = sprite.getFrameU(2/16f);
        vMin = sprite.getFrameV(0);
        vMax = sprite.getFrameV(1);
        
        topRight = Vertex.create(nextLeft.add(0, -skirtHeight, 0), uMax, vMax);
        topLeft = Vertex.create(nextLeft, uMin, vMax);
        botLeft = Vertex.create(lastLeft, uMin, vMin);
        botRight = Vertex.create(lastLeft.add(0, -skirtHeight, 0), uMax, vMin);
        
        quad = new Quad(botRight, botLeft, topLeft, topRight, worldPos);
        result.add(quad);
        
        // bot quad
        uMin = sprite.getFrameU(0);
        uMax = sprite.getFrameU(1);
        vMin = sprite.getFrameV(vStart);
        vMax = sprite.getFrameV(vEnd);
        
        botRight = Vertex.create(lastRight.add(0, -skirtHeight, 0), uMin, vMin);
        topRight = Vertex.create(nextRight.add(0, -skirtHeight, 0), uMin, vMax);
        topLeft = Vertex.create(nextLeft.add(0, -skirtHeight, 0), uMax, vMax);
        botLeft = Vertex.create(lastLeft.add(0, -skirtHeight, 0), uMax, vMin);
        
        quad = new Quad(botRight, botLeft, topLeft, topRight, worldPos);
        result.add(quad);
    }
    
    @Override
    public void render(ChuteBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        
        if (entity == null || entity.getWorld() == null) return;
        
        if (entity.getTarget() == null || entity.getTarget().getManhattanDistance(entity.getPos()) < 1) return;
        
        var targetCandidate = entity.getWorld().getBlockEntity(entity.getTarget(), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (targetCandidate.isEmpty()) return;
        
        matrices.push();
        matrices.translate(0, 0.08, 0);
        
        var entry = matrices.peek();
        var modelMatrix = entry.getPositionMatrix();
        var consumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        
        var lightRefreshInterval = 60;
        
        var quads = getOrComputeModel(entity, targetCandidate.get());
        if (quads == null) {
            matrices.pop();
            return;
        }
        
        var lastLight = WorldRenderer.getLightmapCoordinates(entity.getWorld(), entity.getPos());
        
        for (var quad : quads) {
            
            final var worldPos = quad.worldPos;
            var worldLight = lightmapCache.computeIfAbsent(quad.worldPos.asLong(), pos -> WorldRenderer.getLightmapCoordinates(entity.getWorld(), worldPos));
            if (entity.getWorld().getTime() % lightRefreshInterval == 0) {
                lightmapCache.put(quad.worldPos.asLong(), WorldRenderer.getLightmapCoordinates(entity.getWorld(), quad.worldPos));
            }
            
            var renderedVertex = quad.a;
            consumer.vertex(modelMatrix, renderedVertex.x, renderedVertex.y, renderedVertex.z)
              .color(Colors.WHITE)
              .texture(renderedVertex.u, renderedVertex.v)
              .normal(entry, 0, 1, 0)
              .light(worldLight)
              .overlay(overlay);
            
            renderedVertex = quad.b;
            consumer.vertex(modelMatrix, renderedVertex.x, renderedVertex.y, renderedVertex.z)
              .color(Colors.WHITE)
              .texture(renderedVertex.u, renderedVertex.v)
              .normal(entry, 0, 1, 0)
              .light(worldLight)
              .overlay(overlay);
            
            renderedVertex = quad.c;
            consumer.vertex(modelMatrix, renderedVertex.x, renderedVertex.y, renderedVertex.z)
              .color(Colors.WHITE)
              .texture(renderedVertex.u, renderedVertex.v)
              .normal(entry, 0, 1, 0)
              .light(lastLight)
              .overlay(overlay);
            
            renderedVertex = quad.d;
            consumer.vertex(modelMatrix, renderedVertex.x, renderedVertex.y, renderedVertex.z)
              .color(Colors.WHITE)
              .texture(renderedVertex.u, renderedVertex.v)
              .normal(entry, 0, 1, 0)
              .light(lastLight)
              .overlay(overlay);
            
            lastLight = worldLight;
        }
        
        matrices.pop();
        
        // render items
        
        // all positions here are in world space
        var conveyorStartPoint = entity.getPos();
        var conveyorEndPoint = entity.getTarget();
        var conveyorStartDir = Vec3d.of(entity.getOwnFacing().getVector());
        var conveyorFacing = targetCandidate.get().getOwnFacing();
        var conveyorEndDir = Vec3d.of(conveyorFacing.getOpposite().getVector());
        
        var conveyorMidPointsVisual = entity.getMidPointsWithTangents();
        var conveyorStartPointVisual = conveyorStartPoint.toCenterPos().add(conveyorStartDir.multiply(-0.5f));
        var conveyorEndPointVisual = conveyorEndPoint.toCenterPos().add(conveyorEndDir.multiply(0.5f));
        
        var transformedMidPoints = conveyorMidPointsVisual.stream().map(elem -> new Pair<>(elem.getLeft().toCenterPos(), Vec3d.of(elem.getRight().getVector()))).toList();
        var segmentPoints = SplineUtil.getPointPairs(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, transformedMidPoints);
        var totalDist = SplineUtil.getTotalLength(segmentPoints);
        
        var renderedItems = getRenderedStacks(entity.getWorld().getTime() + tickDelta, (float) (totalDist * 22f));
        if (renderedItems.isEmpty()) return;
        
        for (var itemData : renderedItems.entrySet()) {
            var renderedStack = itemData.getValue();
            var renderedProgress = itemData.getKey();
            var nextProgress = itemData.getKey() + 0.03;
            
            var worldPoint = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, renderedProgress);
            var nextWorldPoint = SplineUtil.getPositionOnSpline(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, conveyorMidPointsVisual, nextProgress);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            
            var forward = nextWorldPoint.subtract(worldPoint);
            var flatForward = new Vec3d(forward.x, 0, forward.z).normalize();
            var dot = new Vec3d(1, 0, 0).dotProduct(flatForward);
            var angleRad = Math.acos(dot);
            
            if (flatForward.z > 0)
                angleRad = -angleRad;
            
            matrices.push();
            matrices.translate(localPoint.x, localPoint.y, localPoint.z);
            matrices.translate(0.5f, 0.8f, 0.5f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(angleRad)));
            matrices.scale(0.6f, 0.6f, 0.6f);
            
            var worldPos = BlockPos.ofFloored(worldPoint);
            
            var worldLight = lightmapCache.computeIfAbsent(worldPos.asLong(), pos -> WorldRenderer.getLightmapCoordinates(entity.getWorld(), worldPos));
            
            MinecraftClient.getInstance().getItemRenderer().renderItem(
              renderedStack,
              ModelTransformationMode.FIXED,
              worldLight,
              overlay,
              matrices,
              vertexConsumers,
              entity.getWorld(),
              0
            );
            
            matrices.pop();
        }
        
    }
    
    private Map<Float, ItemStack> getRenderedStacks(float time, float travelDuration) {
        
        time  = time % travelDuration;
        
        var progress = time / travelDuration;
        var progress2 = time / travelDuration + 0.6f;
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
