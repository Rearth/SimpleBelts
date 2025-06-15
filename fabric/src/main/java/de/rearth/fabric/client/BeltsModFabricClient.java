package de.rearth.fabric.client;

import de.rearth.BlockContent;
import de.rearth.client.BeltsClient;
import de.rearth.client.renderers.BeltOutlineRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;

public final class BeltsModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BeltsClient.init();
        
        WorldRenderEvents.BLOCK_OUTLINE.register(BeltsModFabricClient::renderBlockOutline);
        
        BeltsClient.registerRenderers();
        
        BlockRenderLayerMap.INSTANCE.putBlock(BlockContent.CHUTE_BLOCK.get(), RenderLayer.getTranslucent());
        
    }
    
    private static boolean renderBlockOutline(WorldRenderContext worldRenderContext, WorldRenderContext.BlockOutlineContext blockOutlineContext) {
        BeltOutlineRenderer.renderPlannedBelt(worldRenderContext.world(), worldRenderContext.camera(), worldRenderContext.matrixStack(), worldRenderContext.consumers());
        return true;
    }
}
