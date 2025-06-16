package de.rearth.api;


import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * ApiLookupCache is used to cache results from a lookup function (for example,
 * fetching a FluidStorage) based on a given block state and entity. The cache
 * is refreshed only if the block state changes or if the underlying block entity
 * is removed.
 *
 * @param <T> The type of the target object (e.g., FluidStorage)
 */
public class ApiLookupCache<T> {
    
    @FunctionalInterface
    public interface LookupFunction<T> {
        T invoke(World world, BlockPos targetPos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction direction);
    }
    
    private BlockEntity cachedEntity;
    private T cachedTarget;
    
    // Cache the last known block state to avoid unnecessary refreshes.
    private BlockState lastBlockState;
    
    private final BlockPos targetPos;
    private final Direction direction;
    private final World world;
    
    private final LookupFunction<T> lookupFunction;
    
    private ApiLookupCache(BlockEntity cachedEntity, T cachedTarget, BlockPos targetPos, Direction direction, World world, LookupFunction<T> lookupFunction) {
        this.cachedEntity = cachedEntity;
        this.cachedTarget = cachedTarget;
        this.targetPos = targetPos;
        this.direction = direction;
        this.world = world;
        this.lookupFunction = lookupFunction;
        // Initialize the lastBlockState using the current state at creation.
        this.lastBlockState = world.getBlockState(targetPos);
    }
    
    public static <T> ApiLookupCache<T> create(BlockPos targetPos, Direction direction, World world, LookupFunction<T> lookupFunction) {
        var state = world.getBlockState(targetPos);
        var entity = world.getBlockEntity(targetPos);
        T target = lookupFunction.invoke(world, targetPos, state, entity, direction);
        
        return new ApiLookupCache<>(entity, target, targetPos, direction, world, lookupFunction);
    }
    
    /**
     * Returns the target value. The lookup is recomputed only if needed
     * -- either because the block state has changed or the cache is otherwise invalid.
     * This is intended to be called every tick.
     *
     * @return The cached or freshly looked up target.
     */
    public T lookup() {
        
        // If cache is valid, return the cached target.
        if (isCacheValid()) {
            return cachedTarget;
        }
        
        cachedTarget = null;
        cachedEntity = null;
        
        refresh();
        return cachedTarget;
    }
    
    /**
     * Refreshes the cache by issuing a new lookup only if the entity exists.
     * The lastBlockState is updated to reflect the current state.
     * If the new state is identical to the old state, it is canceled early.
     *
     */
    private void refresh() {
        var currentState = world.getBlockState(targetPos);
        
        // if no blockstate change has occurred, the lookup would fail anyway
        if (currentState.equals(lastBlockState)) {
            return;
        }
        
        var entity = world.getBlockEntity(targetPos);
        T target = null;
        
        if (entity != null) {
            target = lookupFunction.invoke(world, targetPos, currentState, entity, direction);
        }
        
        cachedTarget = target;
        cachedEntity = entity;
        lastBlockState = currentState;
    }
    
    /**
     * Checks if the cache is still valid:
     * - The cached BlockEntity still exists and is not removed.
     * - There is a valid target.
     *
     * @return true if the cache can be used; false otherwise.
     */
    private boolean isCacheValid() {
        var entity = cachedEntity;
        var target = cachedTarget;
        
        // Check if block entity is valid.
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        
        // Check that our target object is still valid.
        return target != null;
    }
}