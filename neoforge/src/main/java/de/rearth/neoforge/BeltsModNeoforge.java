package de.rearth.neoforge;

import de.rearth.api.item.ItemApi;
import net.neoforged.fml.common.Mod;

import de.rearth.Belts;

@Mod(Belts.MOD_ID)
public final class BeltsModNeoforge {
    public BeltsModNeoforge() {
        
        ItemApi.BLOCK = new NeoforgeItemApiImpl();
        
        // Run our common setup.
        Belts.init();
    }
}
