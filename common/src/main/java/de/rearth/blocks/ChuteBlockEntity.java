package de.rearth.blocks;

import de.rearth.Belts;
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
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

import java.util.*;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {
    
    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    
    // itemstack waiting to be inserted in the target inventor of the belt.
    // the bottom (first) of the queue is the output. Items added to this queue are queued from the start side (last queue).
    // Visually the end of the queue is forward so the end is barely visible from the outside.
    private final Deque<ItemStack> outputQueue = new ArrayDeque<>();
    
    // items that are in transit, and not in the queue yet. Key is the progress [0-1] along the current path;
    private final List<Pair<Float, ItemStack>> movingItems = new ArrayList<>();
    
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
        if (world == null || world.isClient) return;
        
        if (target == null || target.equals(BlockPos.ORIGIN)) return;
        
        refreshCaches();
        
        // todo rework queue logic to only block moving items, not move them to it
        loadItemsOnBelt();
        moveItemsOnBelt();
        processQueue();
        
        if (world.getTime() % 6 == 0) {
            var niceItems = movingItems.stream().map(pair -> pair.getLeft() + ": " + pair.getRight() + "; ").toList();
//            System.out.println("Items: " + niceItems);
//            System.out.println("Queue: " + outputQueue);
        }
        
        // todo change networking here to send small updates for the moving items
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
        
    }
    
    private void processQueue() {
        if (outputQueue.isEmpty()) return;
        
        var targetInv = targetCache.lookup();
        if (targetInv == null) return;
        
        var insertionStack = outputQueue.getFirst();
        var insertedAmount = targetInv.insert(insertionStack, true);
        if (insertedAmount == insertionStack.getCount()) {
            System.out.println("Unload from belt: " + insertionStack);
            outputQueue.removeFirst();
            targetInv.insert(insertionStack, false);
        }
        
    }
    
    private void moveItemsOnBelt() {
        
        var beltLength = 5f;
        var beltSpeed = 1f;
        var progressDelta = beltSpeed / beltLength / 20f;
        
        var nextQueueStart = getPotentialQueueStart();
        
        var queueMover = -1;
        
        for (int i = 0; i < movingItems.size(); i++) {
            var pair = movingItems.get(i);
            var itemProgress = pair.getLeft();
            var newProgress = itemProgress + progressDelta;
            var newPair = new Pair<>(newProgress, pair.getRight());
            movingItems.set(i, newPair);
            
            if (newProgress > nextQueueStart) {
                if (queueMover != -1) {
                    Belts.LOGGER.warn("Tried moving multiple items to output queue, this should never happen");
                }
                queueMover = i;
                System.out.println("Moved to queue: " + pair.getRight());
            }
        }
        
        if (queueMover != -1) {
            var queueCandidate = movingItems.get(queueMover);
            movingItems.remove(queueMover);
            outputQueue.addLast(queueCandidate.getRight());
        }
        
    }
    
    @SuppressWarnings("DataFlowIssue")
    private void loadItemsOnBelt() {
        var extractionInterval = 30;
        var extractionOffset = pos.asLong();
        
        // todo check queue overlap
        
        if (getPotentialQueueStart() <= 0) return;
        
        if ((world.getTime() + extractionOffset) % extractionInterval == 0 && sourceCache.lookup() != null) {
            // try extracting first stack
            var source = sourceCache.lookup();
            ItemStack extracted = null;
            for (int i = 0; i < source.getSlotCount(); i++) {
                var stackInSlot = source.getStackInSlot(i).copy();
                if (stackInSlot.isEmpty()) continue;
                var extractedAmount = source.extract(stackInSlot, false);
                if (extractedAmount > 0) {
                    extracted = stackInSlot.copyWithCount(extractedAmount);
                }
            }
            
            if (extracted != null) {
                System.out.println("extracted: " + extracted);
                movingItems.add(new Pair<>(0f, extracted));
            }
        }
    }
    
    private float getPotentialQueueStart() {
        var beltLength = 5f;
        var queueCount = outputQueue.size() + 1;
        var queueSize = queueCount / beltLength;
        return 1f - queueSize;
    }
    
    @SuppressWarnings({"OptionalIsPresent", "DataFlowIssue"})
    private void refreshCaches() {
        if (sourceCache == null) {
            sourceCache = ApiLookupCache.create(pos.add(getOwnFacing().getOpposite().getVector()), getOwnFacing(), world, ((world1, targetPos, state1, entity, direction) -> ItemApi.BLOCK.find(world1, targetPos, state1, entity, direction)));
        }
        
        if (targetCache == null) {
            var conveyorEndEntityCandidate = world.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
            if (conveyorEndEntityCandidate.isPresent()) {
                var conveyorEndEntity = conveyorEndEntityCandidate.get();
                targetCache = ApiLookupCache.create(target.add(conveyorEndEntity.getOwnFacing().getOpposite().getVector()), conveyorEndEntity.getOwnFacing(), world, ((world1, targetPos, state1, entity, direction) -> ItemApi.BLOCK.find(world1, targetPos, state1, entity, direction)));
            }
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
        
        var positionsList = new NbtList();
        positionsList.addAll(movingItems.stream().map(pair -> {
            var compound = new NbtCompound();
            compound.putFloat("a", pair.getLeft());
            compound.put("b", pair.getRight().encode(registryLookup));
            return compound;
        }).toList());
        nbt.put("moving", positionsList);
    }
    
    @SuppressWarnings("OptionalIsPresent")
    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        
        target = BlockPos.fromLong(nbt.getLong("target"));
        
        var midPointsList = nbt.getLongArray("midpoints");
        midPoints = Arrays.stream(midPointsList).mapToObj(BlockPos::fromLong).toList();
        
        var positions = nbt.getList("moving", NbtElement.COMPOUND_TYPE);
        movingItems.clear();
        movingItems.addAll(positions.stream().map(element -> {
            var compound = (NbtCompound) element;
            var progress = compound.getFloat("a");
            var stackCandidate = ItemStack.fromNbt(registryLookup, compound.get("b"));
            var stack = stackCandidate.isEmpty() ? ItemStack.EMPTY : stackCandidate.get();
            return new Pair<>(progress, stack);
        }).toList());
        
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
    
    public List<Pair<Float, ItemStack>> getMovingItems() {
        return movingItems;
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
