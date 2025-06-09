package de.rearth.neoforge;

import net.neoforged.bus.EventBus;
import net.neoforged.fml.common.Mod;

import de.rearth.Belts;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Belts.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        
        // Run our common setup.
        Belts.init();
    }
}
