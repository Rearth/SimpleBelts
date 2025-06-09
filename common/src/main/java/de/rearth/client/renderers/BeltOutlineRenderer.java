package de.rearth.client.renderers;

import de.rearth.ComponentContent;
import de.rearth.items.BeltItem;
import de.rearth.util.Spline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BeltOutlineRenderer {
    
    public static void renderPlannedBelt(ClientWorld world, Camera camera, MatrixStack matrixStack, VertexConsumerProvider consumer) {
        if (world == null) return;
        
        var client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null) return;
        
        var itemStack = player.getMainHandStack();
        
        if (!(itemStack.getItem() instanceof BeltItem) || !itemStack.contains(ComponentContent.BELT.get())) return;
        var startBlockPos = itemStack.get(ComponentContent.BELT.get());
        if (startBlockPos == null || startBlockPos.equals(BlockPos.ORIGIN)) return;
        
        var startPos = startBlockPos.toCenterPos();
        var endPos = getPlayerTargetPos(player, 3);
        
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            var innerBlockPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
            //  var surfacePos = innerBlockPos.add(((BlockHitResult) client.crosshairTarget).getSide().getVector());
            endPos = innerBlockPos.toCenterPos();
        }
        
        var linePoints = getPositionsAlongLine(startPos, endPos);
        
        matrixStack.push();
        var cameraPos = camera.getPos();
        matrixStack.translate(-cameraPos.getX(), -cameraPos.getY(), -cameraPos.getZ());
        
        for (var linePos : linePoints) {
            var center = linePos;
            var lineRadius = 0.1f;
            WorldRenderer.drawBox(matrixStack, consumer.getBuffer(RenderLayer.getLines()), center.x - lineRadius, center.y - lineRadius, center.z - lineRadius, center.x + lineRadius, center.y + lineRadius, center.z + lineRadius, 1f, 1f, 1f, 0.8f);
        }
        
        matrixStack.pop();
        
    }
    
    private static List<Vec3d> getPositionsAlongLine(Vec3d from, Vec3d to) {
        var stepSize = 0.2f;
        
        var result = new ArrayList<Vec3d>();
        var direction = to.subtract(from).normalize();
        
        var dist = from.distanceTo(to);
        for (var i = 0f; i < dist; i += stepSize) {
            var progress = i / dist;
            var center = Spline.getPointOnCatmullRomSpline((float) progress, from, new Vec3d(1, 0, 0), to, new Vec3d(0, 0, -1));
            result.add(center);
        }
        
        return result;
        
    }
    
    public static Vec3d getPlayerTargetPos(ClientPlayerEntity player, float distance) {
        var startPos = player.getEyePos();
        var lookVec = player.getRotationVec(0F);
        return startPos.add(lookVec.multiply(distance));
    }
    
}
