package de.rearth.blocks;

import de.rearth.Belts;
import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.client.renderers.ChuteBeltRenderer;
import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {
    
    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    
    private boolean pendingSetupPacket = false;
    
    // client only data, used for rendering
    @Environment(EnvType.CLIENT)
    public ChuteBeltRenderer.Quad[] renderedModel;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        
        if (world.isClient) return;
        
        if (pendingSetupPacket) {
            pendingSetupPacket = false;
            NetworkManager.sendToPlayer((ServerPlayerEntity) this.getWorld().getPlayers().getFirst(), new ChuteDataPacket(pos, target, midPoints));
        }
        
    }
    
    public BlockPos getTarget() {
        return target;
    }
    
    public Direction getOwnFacing() {
        return getCachedState().get(HorizontalFacingBlock.FACING);
    }
    
    public void assignFromBeltItem(BlockPos target, List<BlockPos> midpoints) {
        this.target = target;
        this.midPoints = midpoints;
        pendingSetupPacket = true;
    }
    
    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING)))
                 .toList();
    }
    
    public static void receivePacket(ChuteDataPacket packet, World world) {
        var candidate = world.getBlockEntity(packet.ownPos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            
            // reset internal client cache
            entity.renderedModel = null;
            
            entity.target = packet.targetPos;
            entity.midPoints = packet.midpoints;
        }
    }
    
    public record ChuteDataPacket(BlockPos ownPos, BlockPos targetPos,
                                  List<BlockPos> midpoints) implements CustomPayload {
        
        public static PacketCodec<RegistryByteBuf, ChuteDataPacket> PACKET_CODEC = PacketCodec.tuple(
          BlockPos.PACKET_CODEC, ChuteDataPacket::ownPos,
          BlockPos.PACKET_CODEC, ChuteDataPacket::targetPos,
          BlockPos.PACKET_CODEC.collect(PacketCodecs.toList()), ChuteDataPacket::midpoints,
          ChuteDataPacket::new
        );
        
        public static final CustomPayload.Id<ChuteDataPacket> FILTER_PACKET_ID = new CustomPayload.Id<>(Belts.id("chute"));
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return FILTER_PACKET_ID;
        }
    }
}
