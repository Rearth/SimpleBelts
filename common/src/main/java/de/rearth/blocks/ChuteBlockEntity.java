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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {
    
    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    
    // client only data, used for rendering
    @Environment(EnvType.CLIENT)
    public ChuteBeltRenderer.Quad[] renderedModel;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        
        if (world.isClient) return;
        
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        if (target != null)
            nbt.putLong("target", target.asLong());
        
        if (!midPoints.isEmpty()) {
            var midpointsArray = midPoints.stream().map(BlockPos::asLong).toList();
            nbt.putLongArray("midpoints", midpointsArray);
        }
    }
    
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        
        target = BlockPos.fromLong(nbt.getLong("target"));
        
        var midPointsList = nbt.getLongArray("midpoints");
        midPoints = Arrays.stream(midPointsList).mapToObj(BlockPos::fromLong).toList();
        
        renderedModel = null;
    }
    
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        var base = super.toInitialChunkDataNbt(registryLookup);
        writeNbt(base, registryLookup);
        return base;
    }
    
    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
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
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
    }
    
    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING)))
                 .toList();
    }
}
