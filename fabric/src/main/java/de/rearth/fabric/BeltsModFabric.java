package de.rearth.fabric;

import de.rearth.api.item.ItemApi;
import net.fabricmc.api.ModInitializer;

import de.rearth.Belts;

public final class BeltsModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        
        ItemApi.BLOCK = new FabricItemApi();
        
        Belts.init();
    }
}
