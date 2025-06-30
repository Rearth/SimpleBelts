package rearth.belts.blocks;

import dev.architectury.platform.Platform;
import dev.ftb.mods.ftbfiltersystem.api.FTBFilterSystemAPI;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockContent;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.ItemContent;
import rearth.belts.api.item.ItemApi;
import rearth.belts.client.renderers.ChuteBeltRenderer;
import rearth.belts.util.SplineUtil;

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
    
    // used to check if a belt is used as target. Periodically updated on belt ends from the belt starts.
    private long lastTargetedTime;
    
    // used for filtering. Optionally works with create and ftb filters.
    public ItemStack filteredItem = ItemStack.EMPTY;
    
    // client only data, used for rendering
    public ChuteBeltRenderer.Quad[] renderedModel;
    public Map<Short, Vec3d> lastRenderedPositions = new HashMap<>();
    
    private boolean networkDirty = false;
    
    public ChuteBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesContent.CHUTE_BLOCK.get(), pos, state);
    }
    
    @Override
    public void tick(World world, BlockPos pos, BlockState state, ChuteBlockEntity blockEntity) {
        if (world == null) return;
        
        if (target == null || target.equals(BlockPos.ORIGIN)) {
            if (!world.isClient && !movingItems.isEmpty()) {
                dropContent(world, pos);
            }
            return;
        }
        
        if (beltData == null) {
            beltData = BeltData.create(this);
            if (world instanceof ServerWorld serverWorld)
                serverWorld.getChunkManager().markForUpdate(pos);
        }
        
        if (beltData == null) {
            target = null;
            midPoints = new ArrayList<>();
            return;
        }
        
        if (world.isClient) return;
        
        moveItemsOnBelt();
        loadItemsOnBelt();
        
        // refresh target
        if (world.getTime() % 19 == 0)
            assignTargetState(world);
        
        
        if (networkDirty && world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
            networkDirty = false;
        }
        
    }
    
    public void dropContent(World world, BlockPos pos) {
        for (var beltItem : movingItems) {
            var stack = beltItem.stack;
            var spawnAt = pos.toCenterPos();
            world.spawnEntity(new ItemEntity(world, spawnAt.x, spawnAt.y, spawnAt.z, stack));
        }
        
        if (!movingItems.isEmpty() || (target != null && !target.equals(BlockPos.ORIGIN))) {
            // pretend to drop an actual belt
            var stack = new ItemStack(ItemContent.BELT.get(), 1);
            var spawnAt = pos.toCenterPos();
            world.spawnEntity(new ItemEntity(world, spawnAt.x, spawnAt.y, spawnAt.z, stack));
        }
        
        movingItems.clear();
    }
    
    private void assignTargetState(World world) {
        var beltTargetCandidate = world.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (beltTargetCandidate.isPresent()) {
            beltTargetCandidate.get().lastTargetedTime = world.getTime();
        } else {
            target = null;
            midPoints = new ArrayList<>();
        }
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
                networkDirty = true;
            }
            
            // try to insert last item (if its in queue). Gets put into queue when the end is reached.
            if (inQueue && outputQueue == 1) {
                var conveyorEndEntityCandidate = world.getBlockEntity(target, BlockEntitiesContent.CHUTE_BLOCK.get());
                if (conveyorEndEntityCandidate.isEmpty()) continue;
                var conveyorEndEntity = conveyorEndEntityCandidate.get();
                var targetInv = ItemApi.BLOCK.find(world, target.add(conveyorEndEntity.getOwnFacing().getOpposite().getVector()), null, null, conveyorEndEntity.getOwnFacing());
                if (targetInv == null) continue;
                
                var insertionStack = pair.stack;
                var insertedAmount = targetInv.insert(insertionStack, true);
                if (insertedAmount == insertionStack.getCount()) {
                    targetInv.insert(insertionStack, false);
                    outputQueue = 0;    // reset queue
                    unloaded = true;
                    networkDirty = true;
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
        
        if ((world.getTime() + extractionOffset) % extractionInterval != 0) return;
        
        if (getPotentialQueueStart() < 0) return;
        
        var source = ItemApi.BLOCK.find(world, pos.add(getOwnFacing().getOpposite().getVector()), null, null, getOwnFacing());
        if (source != null) {
            // try extracting first stack
            ItemStack extracted = null;
            for (int i = 0; i < source.getSlotCount(); i++) {
                var stackInSlot = source.getStackInSlot(i).copy();
                if (stackInSlot.isEmpty()) continue;
                if (!stackMatchesFilter(stackInSlot)) continue;
                stackInSlot.setCount(Math.min(stackInSlot.getCount(), 64));
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
                networkDirty = true;
            }
        }
    }
    
    private boolean stackMatchesFilter(ItemStack stack) {
        if (filteredItem.isEmpty()) return true;
        
        if (Platform.isModLoaded("ftbfiltersystem")) {
            var filterAPI = FTBFilterSystemAPI.api();
            if (filterAPI.isFilterItem(filteredItem))
                return filterAPI.doesFilterMatch(filteredItem, stack);
        }
        
        return stack.getItem().equals(filteredItem.getItem());
    }
    
    private float getPotentialQueueStart() {
        var squashFactor = 0.8f;
        var beltLength = beltData.totalLength();
        var queueCount = outputQueue;
        var queueSize = queueCount * squashFactor / beltLength;
        return (float) (1f - queueSize);
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
        
        nbt.put("filter", filteredItem.encodeAllowEmpty(registryLookup));
        
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
        
        filteredItem = ItemStack.fromNbtOrEmpty(registryLookup, nbt.getCompound("filter"));
        
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
    
    public boolean isUsed() {
        var usedAsTarget = world.getTime() - lastTargetedTime < 40;
        var usedAsSource = target != null && !target.equals(BlockPos.ORIGIN);
        return usedAsTarget || usedAsSource;
    }
    
    public void assignFromBeltItem(BlockPos target, List<BlockPos> midpoints) {
        this.target = target;
        this.midPoints = midpoints;
        beltData = BeltData.create(this);
        networkDirty = true;
        this.markDirty();
        
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
    }
    
    public List<Pair<BlockPos, Direction>> getMidPointsWithTangents() {
        return midPoints.stream()
                 .filter(point -> world.getBlockState(point).getBlock().equals(BlockContent.CONVEYOR_SUPPORT_BLOCK.get()))
                 .map(point -> new Pair<>(point, world.getBlockState(point).get(HorizontalFacingBlock.FACING)))
                 .toList();
    }
    
    public void assignFilterItem(ItemStack stack, PlayerEntity player) {
        
        if (stack.isEmpty()) {
            resetFilterItem(player);
            return;
        }
        
        player.sendMessage(Text.translatable("message.belts.filter_set"));
        filteredItem = stack.copy();
        this.markDirty();
        
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
    }
    
    public void resetFilterItem(PlayerEntity player) {
        player.sendMessage(Text.translatable("message.belts.filter_reset"));
        filteredItem = ItemStack.EMPTY;
        this.markDirty();
        
        if (world instanceof ServerWorld serverWorld)
            serverWorld.getChunkManager().markForUpdate(pos);
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
        
        public static @Nullable BeltData create(ChuteBlockEntity entity) {
            
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
