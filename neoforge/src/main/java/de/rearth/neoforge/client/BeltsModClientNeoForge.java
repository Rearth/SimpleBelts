package de.rearth.neoforge.client;

import de.rearth.Belts;
import de.rearth.client.BeltsClient;
import de.rearth.client.renderers.BeltOutlineRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@Mod(value = Belts.MOD_ID, dist = Dist.CLIENT)
public class BeltsModClientNeoForge {
    
    public BeltsModClientNeoForge(IEventBus eventBus) {
        BeltsClient.init();
        eventBus.register(new EventHandler());
    }
    
    @EventBusSubscriber(modid = Belts.MOD_ID, value = Dist.CLIENT)
    public static class CustomEvents {
        
        @SubscribeEvent
        public static void onOutlineRender(RenderHighlightEvent.Block event) {
            BeltOutlineRenderer.renderPlannedBelt(MinecraftClient.getInstance().world, event.getCamera(), event.getPoseStack(), event.getMultiBufferSource());
        }
        
    }
    
    static class EventHandler {
        
        @SubscribeEvent
        public void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            BeltsClient.registerRenderers();
        }
    }
    
}
