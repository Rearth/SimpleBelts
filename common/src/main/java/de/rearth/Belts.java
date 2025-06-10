package de.rearth;

import net.minecraft.util.Identifier;

public final class Belts {
    public static final String MOD_ID = "belts";

    public static void init() {
        System.out.println("Hello world from common!");
        // Write common init code here.
        
        NetworkContent.init();
        
        BlockContent.BLOCKS.register();
        ItemContent.ITEMS.register();
        BlockEntitiesContent.TYPES.register();
        ComponentContent.COMPONENTS.register();
    }
    
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
