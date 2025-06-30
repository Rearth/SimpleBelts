package rearth.belts.client.renderers;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.blocks.ChuteBlockEntity;
import rearth.belts.util.MathHelpers;
import rearth.belts.util.SplineUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity> {
    
    private static final HashMap<Long, Integer> lightmapCache = new HashMap<>();
    
    public record Vertex(float x, float y, float z, float u, float v) {
        public static Vertex create(Vec3d pos, float u, float v) {
            return new Vertex((float) pos.x, (float) pos.y, (float) pos.z, u, v);
        }
    }
    
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, BlockPos worldPos) {
    }
    
    @Override
    public void render(ChuteBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        
        if (entity == null || entity.getWorld() == null) return;
        
        if (entity.getTarget() == null || entity.getTarget().getManhattanDistance(entity.getPos()) < 1) return;
        
        var targetCandidate = entity.getWorld().getBlockEntity(entity.getTarget(), BlockEntitiesContent.CHUTE_BLOCK.get());
        if (targetCandidate.isEmpty()) return;
        
        var beltData = entity.getBeltData();
        if (beltData == null) return;
        
        var itemRenderDistSq = 64 * 64;
        var beltRenderDistSq = 96 * 96;
        
        renderBeltMesh(entity, matrices, vertexConsumers, overlay, targetCandidate, beltRenderDistSq);
        
        // render items
        renderBeltItems(entity, matrices, vertexConsumers, overlay, beltData, itemRenderDistSq);
        
        renderBeltFilter(entity, matrices, vertexConsumers, light, overlay, beltRenderDistSq);
        
    }
    
    private void renderBeltMesh(ChuteBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int overlay, Optional<ChuteBlockEntity> targetCandidate, int beltRenderDistSq) {
        matrices.push();
        matrices.translate(0, -2 / 16f + 0.08f, 0);
        
        var entry = matrices.peek();
        var modelMatrix = entry.getPositionMatrix();
        var consumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        
        var lightRefreshInterval = 82;
        
        var quads = getOrComputeModel(entity, targetCandidate.get());
        if (quads == null) {
            matrices.pop();
            return;
        }
        
        var lastLight = WorldRenderer.getLightmapCoordinates(entity.getWorld(), entity.getPos());
        
        for (var quad : quads) {
            
            final var worldPos = quad.worldPos;
            
            // abort early is camera is too far away for this segment
            var camDist = MinecraftClient.getInstance().getCameraEntity().getPos().squaredDistanceTo(Vec3d.of(worldPos));
            if (camDist > beltRenderDistSq) continue;
            
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
    }
    
    private Quad[] getOrComputeModel(ChuteBlockEntity entity, ChuteBlockEntity target) {

        if (true) {
            return createSplineModel(entity, target);
        }
        
        if (entity.renderedModel == null)
            entity.renderedModel = createSplineModel(entity, target);
        
        return entity.renderedModel;
    }
    
    private static Quad[] createSplineModel(ChuteBlockEntity entity, ChuteBlockEntity target) {
        
        var sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
                       .apply(Identifier.of("belts", "block/conveyorbelt"));
        var result = new ArrayList<Quad>();
        
        var beltData = entity.getBeltData();
        if (beltData == null) return null;
        
        var segmentSize = 0.75f;
        var segmentCount = (int) Math.ceil(beltData.totalLength() / segmentSize);
        var lineWidth = 0.33f;
        
        var conveyorStartDir = Vec3d.of(entity.getOwnFacing().getVector());
        var conveyorEndDir = Vec3d.of(target.getOwnFacing().getOpposite().getVector());
        
        var beginRight = conveyorStartDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        
        // local space
        var localStart = new Vec3d(0.5f, 0.5, 0.5f).add(conveyorStartDir.multiply(-0.5f));
        var lastRight = localStart.add(beginRight.multiply(lineWidth));
        var lastLeft = localStart.add(beginRight.multiply(-lineWidth));
        
        for (int i = 0; i < segmentCount; i++) {
            
            var last = i == segmentCount - 1;
            var progress = i / (float) segmentCount;
            var nextProgress = (i + 1) / (float) segmentCount;
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, progress);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            var worldPointNext = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPointNext = worldPointNext.subtract(entity.getPos().toCenterPos());
            
            var worldPos = BlockPos.ofFloored(worldPointNext.add(0.5f, 0, 0.5f));
            
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
                var worldPointMid = SplineUtil.getPositionOnSpline(beltData, midProgress);
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
                
                addSegmentVertices(midRight, lastRight, midLeft, lastLeft, sprite, worldPos, result, 0, 0.5f);
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
        uMax = sprite.getFrameU(2 / 16f);
        vMin = sprite.getFrameV(vStart);
        vMax = sprite.getFrameV(vEnd);
        
        topRight = Vertex.create(nextRight.add(0, -skirtHeight, 0), uMax, vMax);
        topLeft = Vertex.create(nextRight, uMin, vMax);
        botLeft = Vertex.create(lastRight, uMin, vMin);
        botRight = Vertex.create(lastRight.add(0, -skirtHeight, 0), uMax, vMin);
        
        quad = new Quad(topRight, topLeft, botLeft, botRight, worldPos);
        result.add(quad);
        
        // left skirt
        uMin = sprite.getFrameU(0);
        uMax = sprite.getFrameU(2 / 16f);
        vMin = sprite.getFrameV(vStart);
        vMax = sprite.getFrameV(vEnd);
        
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
    
    private void renderBeltItems(ChuteBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int overlay, ChuteBlockEntity.BeltData beltData, int itemRenderDistSq) {
        var renderedItems = getRenderedStacks(entity);
        
        for (var itemData : renderedItems) {
            var renderedStack = itemData.stack;
            var renderedProgress = itemData.progress;
            var delta = 0.05f / beltData.totalLength();
            var nextProgress = itemData.progress + delta;
            
            var worldPoint = SplineUtil.getPositionOnSpline(beltData, renderedProgress);
            var cam = MinecraftClient.getInstance().getCameraEntity();
            
            // abort early is camera is too far away
            var camDist = cam.getPos().squaredDistanceTo(worldPoint);
            if (camDist > itemRenderDistSq) continue;
            
            // abort if item is behind player (very basic frustum culling)
            var camLookDir = cam.getRotationVector();
            var itemOffset = worldPoint.subtract(cam.getPos());
            // negative dot product means the item is behind
            if (camDist > 1f && camLookDir.dotProduct(itemOffset.normalize()) < 0)
                continue;
            
            
            var nextWorldPoint = SplineUtil.getPositionOnSpline(beltData, nextProgress);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            
            var lastRenderPosition = entity.lastRenderedPositions.getOrDefault(itemData.id, localPoint);
            var renderPosition = MathHelpers.lerp(lastRenderPosition, localPoint, 0.03f);
            
            entity.lastRenderedPositions.put(itemData.id, renderPosition);
            
            var forward = nextWorldPoint.subtract(worldPoint);
            var flatForward = new Vec3d(forward.x, 0, forward.z).normalize();
            var dot = new Vec3d(1, 0, 0).dotProduct(flatForward);
            var angleRad = Math.acos(dot);
            var angleUp = Math.acos(forward.normalize().dotProduct(flatForward));
            
            if (forward.y < 0)
                angleUp = -angleUp;
            
            if (flatForward.z > 0) {
                angleRad = -angleRad;
            }
            
            matrices.push();
            matrices.translate(renderPosition.x, renderPosition.y, renderPosition.z);
            matrices.translate(0.5f, 0.8f - 3 / 16f, 0.5f);
            
            var bakedmodel = MinecraftClient.getInstance().getItemRenderer().getModel(renderedStack, entity.getWorld(), null, 0);
            var useItemTransform = !bakedmodel.getQuads(null, null, entity.getWorld().random).isEmpty();
            
            if (useItemTransform) {
                matrices.translate(0, -2 / 16f, 0);
                matrices.scale(0.8f, 0.8f, 0.8f);
            }
            
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(angleRad)));
            if (Math.abs(angleUp) > 0.01f)
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.toDegrees(angleUp)));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            
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
        
        if (entity.getWorld().getTime() % 104 == 0)
            cleanPositionsCache(entity);
    }
    
    
    private void renderBeltFilter(ChuteBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, int itemRenderDistSq) {
        var renderedStack = entity.filteredItem;
        if (renderedStack.isEmpty()) return;
        
        var worldPoint = entity.getPos().toCenterPos();
        var cam = MinecraftClient.getInstance().getCameraEntity();
        
        // abort early is camera is too far away
        var camDist = cam.getPos().squaredDistanceTo(worldPoint);
        if (camDist > itemRenderDistSq) return;
        
        // abort if item is behind player (very basic frustum culling)
        var camLookDir = cam.getRotationVector();
        var itemOffset = worldPoint.subtract(cam.getPos());
        // negative dot product means the item is behind
        if (camDist > 5f && camLookDir.dotProduct(itemOffset.normalize()) < 0)
            return;
        
        var ownFacing = entity.getOwnFacing();
        
        var forwardDir = Vec3d.of(ownFacing.getVector());
        var renderOffset = forwardDir.multiply(-0.43f);
        
        matrices.push();
        matrices.translate(0.5f, 0.7f, 0.5f);
        matrices.translate(renderOffset.x, renderOffset.y, renderOffset.z);
        
        if (ownFacing.getAxis().equals(Direction.Axis.X))
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        
        matrices.scale(0.4f, 0.4f, 0.4f);
        
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
    
    private Iterable<ChuteBlockEntity.BeltItem> getRenderedStacks(ChuteBlockEntity entity) {
        return entity.getMovingItems();
    }
    
    // remove unused cache indices to avoid memory leaks
    private void cleanPositionsCache(ChuteBlockEntity entity) {
        var active = getRenderedStacks(entity);
        var cache = entity.lastRenderedPositions;
        var usedData = new HashMap<Short, Vec3d>();
        for (var movedItem : active) {
            var lastEntry = cache.getOrDefault(movedItem.id, Vec3d.ZERO);
            usedData.put(movedItem.id, lastEntry);
        }
        
        cache.clear();
        cache.putAll(usedData);
    }
    
    @Override
    public boolean rendersOutsideBoundingBox(ChuteBlockEntity blockEntity) {
        return true;
    }
    
    @Override
    public int getRenderDistance() {
        return 96;
    }
    
    // overrides NF mixin
    public Box getRenderBoundingBox(BlockEntity blockEntity) {
        return new Box(
          Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
        );
    }
}
