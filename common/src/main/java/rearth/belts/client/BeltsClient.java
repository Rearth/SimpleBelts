package rearth.belts.client;

import rearth.belts.BlockEntitiesContent;
import rearth.belts.client.renderers.ChuteBeltRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class BeltsClient {
    
    public static void init() {
        System.out.println("Hello from belt client!");
        
    }
    
    public static void registerRenderers() {
        System.out.println("Registering renderers");
        
        BlockEntityRendererFactories.register(BlockEntitiesContent.CHUTE_BLOCK.get(), ctx -> new ChuteBeltRenderer());
    }
    
}
