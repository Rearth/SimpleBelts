package de.rearth.fabric.client;

import de.rearth.client.BeltsClient;
import de.rearth.client.renderers.BeltOutlineRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.model.BakedQuadFactory;

public final class ExampleModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BeltsClient.init();
        
        WorldRenderEvents.BLOCK_OUTLINE.register(ExampleModFabricClient::renderBlockOutline);
        
        BeltsClient.registerRenderers();
        
    }
    
    private static boolean renderBlockOutline(WorldRenderContext worldRenderContext, WorldRenderContext.BlockOutlineContext blockOutlineContext) {
        BeltOutlineRenderer.renderPlannedBelt(worldRenderContext.world(), worldRenderContext.camera(), worldRenderContext.matrixStack(), worldRenderContext.consumers());
        return true;
    }
}
