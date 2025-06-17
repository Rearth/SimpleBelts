package de.rearth.blocks;

import de.rearth.BlockContent;
import de.rearth.BlockEntitiesContent;
import de.rearth.api.ApiLookupCache;
import de.rearth.api.item.ItemApi;
import de.rearth.client.renderers.ChuteBeltRenderer;
import de.rearth.util.SplineUtil;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChuteBlockEntity extends BlockEntity implements BlockEntityTicker<ChuteBlockEntity> {
    
    // everything in this section is synced to the client
    private BlockPos target;
    private List<BlockPos> midPoints = new ArrayList<>();
    // items that are in transit, and not in the queue yet. Key is the progress [0-1] along the current path;
    // first = at begin of transit path, near extraction point. Last = near target.
    private final Deque<BeltItem> movingItems = new ArrayDeque<>();
    
    // number of items actively waiting / blocked by the output side of the belt.
    private int outputQueue = 0;
    
    // this is calculated on both the client and server
    private BeltData beltData;
    
    private ApiLookupCache<ItemApi.InventoryStorage> sourceCache;
    private ApiLookupCache<ItemApi.InventoryStorage> targetCache;
    
    // client only data, used for rendering
    @Environment(EnvType.CLIENT)
    public ChuteBeltRenderer.Quad[] renderedModel;
    @Environment(EnvType.CLIENT)
    public Map<Short, Vec3d> lastRenderedPositions = new HashMap<>();
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (world == null || world.isClient) return;
        
        if (target == null || target.equals(BlockPos.ORIGIN)) return;
        if (beltData == null) {
            beltData = BeltData.create(this);
            if (world instanceof ServerWorld serverWorld)
                serverWorld.getChunkManager().markForUpdate(pos);
        }
        
        refreshCaches();
        
        moveItemsOnBelt();
        loadItemsOnBelt();
        
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
        
    }
    
    private void moveItemsOnBelt() {
        
        var beltLength = beltData.totalLength();
        var beltSpeed = 1f;
        var progressDelta = beltSpeed / beltLength / 20f;
        
        boolean unloaded = false;
        outputQueue = 0;
        
        for (var pair : movingItems.reversed()) {
            var itemProgress = pair.progress;
            var newProgress = itemProgress + progressDelta;
            
            var inQueue = newProgress >= getPotentialQueueStart();
            
            // only accept new position if not in queue
            if (inQueue) {
                outputQueue++;
            } else {
                pair.progress = (float) newProgress;
            }
            
            // try to insert last item (if its in queue). Gets put into queue when the end is reached.
            if (inQueue && outputQueue == 1) {
                var targetInv = targetCache.lookup();
                if (targetInv == null) continue;
                
                var insertionStack = pair.stack;
                var insertedAmount = targetInv.insert(insertionStack, true);
                if (insertedAmount == insertionStack.getCount()) {
                    targetInv.insert(insertionStack, false);
                    outputQueue = 0;    // reset queue
                    unloaded = true;
                }
            }
        }
        
        if (unloaded)
            movingItems.removeLast();
        
    }
    
    @SuppressWarnings("DataFlowIssue")
    private void loadItemsOnBelt() {
        var extractionInterval = (int) (20 / 0.8f) + 1;
        var extractionOffset = pos.asLong();
        
        if (getPotentialQueueStart() < 0) return;
        
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
                    break;
                }
            }
            
            if (extracted != null) {
                var id = (short) world.random.nextBetween(Short.MIN_VALUE, Short.MAX_VALUE);
                movingItems.addFirst(new BeltItem(id, extracted));
                this.markDirty();
            }
        }
    }
    
    private float getPotentialQueueStart() {
        var squashFactor = 0.8f;
        var beltLength = beltData.totalLength();
        var queueCount = outputQueue;
        var queueSize = queueCount * squashFactor / beltLength;
        return (float) (1f - queueSize);
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
            compound.putFloat("a", pair.progress);
            compound.put("b", pair.stack.encode(registryLookup));
            compound.putShort("id", pair.id);
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
            var id = compound.getShort("id");
            var stackCandidate = ItemStack.fromNbt(registryLookup, compound.get("b"));
            var stack = stackCandidate.isEmpty() ? ItemStack.EMPTY : stackCandidate.get();
            return new BeltItem(progress, id, stack);
        }).toList());
        
        if (world == null) return;
        
        beltData = BeltData.create(this);
        
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
    
    public Iterable<BeltItem> getMovingItems() {
        return movingItems;
    }
    
    public BlockPos getTarget() {
        return target;
    }
    
    public BeltData getBeltData() {
        return beltData;
    }
    
    public Direction getOwnFacing() {
        return getCachedState().get(HorizontalFacingBlock.FACING);
    }
    
    public void assignFromBeltItem(BlockPos target, List<BlockPos> midpoints) {
        this.target = target;
        this.midPoints = midpoints;
        beltData = BeltData.create(this);
        
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
    }
    
    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING)))
                 .toList();
    }
    
    public static class BeltItem {
        public float progress;
        public final short id;
        public final ItemStack stack;
        
        public BeltItem(short id, ItemStack stack) {
            this.id = id;
            this.stack = stack;
        }
        
        public BeltItem(float progress, short id, ItemStack stack) {
            this.id = id;
            this.stack = stack;
            this.progress = progress;
        }
    }
    
    public record BeltData(List<Pair<Vec3d, Vec3d>> allPoints, double totalLength, Double[] segmentLengths) {
        
        public static BeltData create(ChuteBlockEntity entity) {
            
            if (entity.getWorld() == null || entity.target == null || entity.target.equals(BlockPos.ORIGIN))
                return null;
            
            var targetCandidate = entity.getWorld().getBlockEntity(entity.getTarget(), BlockEntitiesContent.CHUTE_BLOCK.get());
            if (targetCandidate.isEmpty()) return null;
            
            var conveyorStartPoint = entity.getPos();
            var conveyorEndPoint = entity.getTarget();
            var conveyorStartDir = Vec3d.of(entity.getOwnFacing().getVector());
            var conveyorFacing = targetCandidate.get().getOwnFacing();
            var conveyorEndDir = Vec3d.of(conveyorFacing.getOpposite().getVector());
            
            var conveyorMidPointsVisual = entity.getMidPointsWithTangents();
            var conveyorStartPointVisual = conveyorStartPoint.toCenterPos().add(conveyorStartDir.multiply(-0.5f));
            var conveyorEndPointVisual = conveyorEndPoint.toCenterPos().add(conveyorEndDir.multiply(0.5f));
            
            var transformedMidPoints = conveyorMidPointsVisual.stream().map(elem -> new Pair<>(elem.getLeft().toCenterPos(), Vec3d.of(elem.getRight().getVector()))).toList();
            var segmentPoints = SplineUtil.getPointPairs(conveyorStartPointVisual, conveyorStartDir, conveyorEndPointVisual, conveyorEndDir, transformedMidPoints);
            
            var segmentLengths = new Double[segmentPoints.size() - 1];
            var totalLength = 0d;
            for (int i = 0; i < segmentPoints.size() - 1; i++) {
                var from = segmentPoints.get(i);
                var to = segmentPoints.get(i + 1);
                var length = SplineUtil.getLineLength(from.getLeft(), from.getRight(), to.getLeft(), to.getRight().multiply(1));
                segmentLengths[i] = (length);
                totalLength += length;
            }
            
            return new BeltData(segmentPoints, totalLength, segmentLengths);
        }
        
    }
}
