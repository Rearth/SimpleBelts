package rearth.belts.fabric;

import rearth.belts.api.item.ItemApi;
import net.fabricmc.api.ModInitializer;

import rearth.belts.Belts;

public final class BeltsModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        
        ItemApi.BLOCK = new FabricItemApi();
        
        Belts.init();
    }
}
