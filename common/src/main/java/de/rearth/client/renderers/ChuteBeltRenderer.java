package de.rearth.client.renderers;

import de.rearth.blocks.ChuteBlockEntity;
import de.rearth.util.Spline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class ChuteBeltRenderer implements BlockEntityRenderer<ChuteBlockEntity> {
    
    @Override
    public void render(ChuteBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        
        var sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
                       .apply(Identifier.of("minecraft", "block/stone"));
        
        var consumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        
        matrices.push();
        var entry = matrices.peek();
        var modelMatrix = entry.getPositionMatrix();
        
        var conveyorOffsets = List.of(new Vec3i(4, 0, 6), new Vec3i(-4, 0, 14), new Vec3i(2, 0, 20));
        
        // all positions here are in world space
        var conveyorMidPoints = conveyorOffsets.stream().map(elem -> entity.getPos().add(elem)).toList();
        var conveyorStartPoint = entity.getPos();
        var conveyorStartDir = new Vec3d(0, 0, 1);
        var conveyorEndDir = new Vec3d(0, 0, 1);
        var conveyorEndPoint = entity.getPos().add(0, 0, 32);
        var conveyorMidPointsWorld = conveyorMidPoints.stream().map(BlockPos::toCenterPos).toArray(Vec3d[]::new);
        
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
        var lineWidth = 0.4f;
        
        var beginRight = conveyorStartDir.crossProduct(new Vec3d(0, 1, 0)).normalize();
        
        var lastRight = new Vec3d(0.5f, 0.5f, 0.5f).add(beginRight.multiply(lineWidth));
        var lastLeft = new Vec3d(0.5f, 0.5f, 0.5f).add(beginRight.multiply(-lineWidth));
        
        light = 150;
        overlay = 150;
        
        for (int i = 0; i < segmentCount - 1; i++) {
            
            var progress = i / (float) segmentCount;
            var nextProgress = (i + 1) / (float) segmentCount;
            var worldPoint = Spline.getPointOnCatmullRomSpline(progress, conveyorStartPoint.toCenterPos(), conveyorStartDir, conveyorEndPoint.toCenterPos(), conveyorEndDir, conveyorMidPointsWorld);
            var localPoint = worldPoint.subtract(entity.getPos().toCenterPos());
            var worldPointNext = Spline.getPointOnCatmullRomSpline(nextProgress, conveyorStartPoint.toCenterPos(), conveyorStartDir, conveyorEndPoint.toCenterPos(), conveyorEndDir, conveyorMidPointsWorld);
            var localPointNext = worldPointNext.subtract(entity.getPos().toCenterPos());
            
            var direction = localPointNext.subtract(localPoint);
            var cross = direction.crossProduct(new Vec3d(0, 1, 0)).normalize();
            
            var nextRight = localPointNext.add(cross.multiply(lineWidth)).add(0.5f, 0.5f, 0.5f);
            var nextLeft = localPointNext.add(cross.multiply(-lineWidth)).add(0.5f, 0.5f, 0.5f);
            
            // draw a quad from lastLeft -> nextLeft -> nextRight -> lastRight
            
            var uMin = sprite.getFrameU(0);
            var uMax = sprite.getFrameU(1);
            var vMin = sprite.getFrameV(0);
            var vMax = sprite.getFrameV(1);
            
            consumer.vertex(modelMatrix,  (float) lastRight.x, (float) lastRight.y, (float) lastRight.z)
              .color(Colors.WHITE)
              .texture(uMax, vMax)
              .normal(entry, 0, 1, 0)
              .light(light)
              .overlay(overlay);
            consumer.vertex(modelMatrix, (float) nextRight.x, (float) nextRight.y, (float) nextRight.z)
              .color(Colors.RED)
              .texture(uMin, vMax)
              .normal(entry, 0, 1, 0)
              .light(light)
              .overlay(overlay);
            consumer.vertex(modelMatrix, (float) nextLeft.x, (float) nextLeft.y, (float) nextLeft.z)
              .color(Colors.GREEN)
              .texture(uMin, vMin)
              .normal(entry, 0, 1, 0)
              .light(light)
              .overlay(overlay);
            consumer.vertex(modelMatrix, (float) lastLeft.x, (float) lastLeft.y, (float) lastLeft.z)
              .color(Colors.BLACK)
              .texture(uMax, vMin)
              .normal(entry, 0, 1, 0)
              .light(light)
              .overlay(overlay);
            
            lastRight = nextRight;
            lastLeft = nextLeft;
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
}
