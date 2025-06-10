package de.rearth.client;

import de.rearth.BlockEntitiesContent;
import de.rearth.client.renderers.ChuteBeltRenderer;
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
