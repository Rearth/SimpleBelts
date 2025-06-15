package de.rearth.blocks;

import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.api.ApiLookupCache;
import de.rearth.api.item.ItemApi;
import de.rearth.client.renderers.ChuteBeltRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
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
    
    private ApiLookupCache<ItemApi.InventoryStorage> sourceCache;
    private ApiLookupCache<ItemApi.InventoryStorage> targetCache;
    
    // client only data, used for rendering
    @Environment(EnvType.CLIENT)
    public ChuteBeltRenderer.Quad[] renderedModel;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (world.isClient) return;
        
        if (target == null || target.equals(BlockPos.ORIGIN)) return;
        
        if (sourceCache == null) {
            sourceCache = ApiLookupCache.create(pos.add(getOwnFacing().getOpposite().getVector()), getOwnFacing(), world, ((world1, targetPos, state1, entity, direction) -> ItemApi.BLOCK.find(world1, targetPos, state1, entity, direction)));
        }
        
        if (targetCache == null) {
            var conveyorEndEntityCandidate = world.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
            if (conveyorEndEntityCandidate.isPresent()) {
                var conveyorEndEntity = conveyorEndEntityCandidate.get();
                targetCache = ApiLookupCache.create(target.add(conveyorEndEntity.getOwnFacing().getOpposite().getVector()), conveyorEndEntity.getOwnFacing(), world, ((world1, targetPos, state1, entity, direction) -> ItemApi.BLOCK.find(world1, targetPos, state1, entity, direction)));
            } else {
                return;
            }
        }
        
        // todo fix source candidate randomly return null on NF after some time?
        var sourceCandidate = sourceCache.lookup();
        if (sourceCandidate != null) {
            for (int i = 0; i < sourceCandidate.getSlotCount(); i++) {
                var slotStack = sourceCandidate.getStackInSlot(i);
                if (slotStack.isEmpty()) continue;
                
                var extracted = sourceCandidate.extract(slotStack, true);
                if (extracted > 0) {
                    System.out.println("extracted: " + slotStack + " " + extracted);
                    break;
                }
            }
        }
        
        var targetCandidate = targetCache.lookup();
        if (targetCandidate != null) {
            var toInsert = new ItemStack(Items.STICK);
            var inserted = targetCandidate.insert(toInsert, true);
            
            if (inserted > 0)
                System.out.println("inserted: " + inserted);
        }
        
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
    
    @SuppressWarnings("OptionalIsPresent")
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        
        target = BlockPos.fromLong(nbt.getLong("target"));
        
        var midPointsList = nbt.getLongArray("midpoints");
        midPoints = Arrays.stream(midPointsList).mapToObj(BlockPos::fromLong).toList();
        
        if (world == null) return;
        
        if (world.isClient) {
            renderedModel = null;
        }
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
