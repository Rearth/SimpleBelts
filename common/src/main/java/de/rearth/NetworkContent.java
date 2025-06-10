package de.rearth;

import de.rearth.blocks.ChuteBlockEntity;
import dev.architectury.networking.NetworkManager;

public class NetworkContent {
    
    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ChuteBlockEntity.ChuteDataPacket.FILTER_PACKET_ID, ChuteBlockEntity.ChuteDataPacket.PACKET_CODEC, (message, context) -> {
            ChuteBlockEntity.receivePacket(message, context.getPlayer().getWorld());
        });
    }
    
}
