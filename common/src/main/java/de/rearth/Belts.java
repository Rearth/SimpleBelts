package de.rearth;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Belts {
    public static final String MOD_ID = "belts";
    public static final Logger LOGGER = LoggerFactory.getLogger("oritech");

    public static void init() {
        System.out.println("Hello world from common!");
        // Write common init code here.
        
        NetworkContent.init();
        
        BlockContent.BLOCKS.register();
        ItemContent.ITEMS.register();
        BlockEntitiesContent.TYPES.register();
        ComponentContent.COMPONENTS.register();
        ItemGroupContent.GROUPS.register();
    }
    
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
