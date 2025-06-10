package de.rearth.blocks;

import de.rearth.Belts;
import de.rearth.BlockEntitiesContent;
import dev.architectury.networking.NetworkManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class ChuteBlockEntity extends BlockEntity {
    
    private BlockPos target;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    public BlockPos getTarget() {
        return target;
    }
    
    public Direction getOwnFacing() {
        return getCachedState().get(HorizontalFacingBlock.FACING);
    }
    
    public void setTargetFromBelt(BlockPos target) {
        this.target = target;
        NetworkManager.sendToPlayer((ServerPlayerEntity) this.getWorld().getPlayers().getFirst(), new ChuteDataPacket(pos, target));
    }
    
    public static void receivePacket(ChuteDataPacket packet, World world) {
        var candidate = world.getBlockEntity(packet.ownPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            entity.target = packet.targetPos;
        }
    }
    
    public record ChuteDataPacket(BlockPos ownPos, BlockPos targetPos) implements CustomPayload {
        
        public static PacketCodec<RegistryByteBuf, ChuteDataPacket> PACKET_CODEC = PacketCodec.tuple(
          BlockPos.PACKET_CODEC, ChuteDataPacket::ownPos,
          BlockPos.PACKET_CODEC, ChuteDataPacket::targetPos,
          ChuteDataPacket::new
        );
        
        public static final CustomPayload.Id<ChuteDataPacket> FILTER_PACKET_ID = new CustomPayload.Id<>(Belts.id("chute"));
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return FILTER_PACKET_ID;
        }
    }
}
