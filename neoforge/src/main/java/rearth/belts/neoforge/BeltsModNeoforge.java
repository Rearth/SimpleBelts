package rearth.belts.neoforge;

import rearth.belts.api.item.ItemApi;
import net.neoforged.fml.common.Mod;

import rearth.belts.Belts;

@Mod(Belts.MOD_ID)
public final class BeltsModNeoforge {
    public BeltsModNeoforge() {
        
        ItemApi.BLOCK = new NeoforgeItemApiImpl();
        
        // Run our common setup.
        Belts.init();
    }
}
