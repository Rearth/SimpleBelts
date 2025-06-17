package de.rearth.blocks;

import com.mojang.serialization.MapCodec;
import de.rearth.util.MathHelpers;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ChuteBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    
    private VoxelShape createShapeForDirection(Direction direction) {
        return VoxelShapes.union(
          MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(2/16f, 4/16f, 14/16f, 14/16f, 1f, 1f), direction, BlockFace.FLOOR),
          MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(3/16f, 5/16f, 16/16f, 13/16f, 15/16f, 18/16f), direction, BlockFace.FLOOR)
        ).simplify();
    }
    
    public ChuteBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }
    
    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        var dir = state.get(Properties.HORIZONTAL_FACING);
        return SHAPES.computeIfAbsent(dir, this::createShapeForDirection);
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }
    
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return Objects.requireNonNull(super.getPlacementState(ctx)).with(Properties.HORIZONTAL_FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }
    
    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
    }
    
    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ChuteBlockEntity(pos, state);
    }
    
    @Override
    protected boolean onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data) {
        super.onSyncedBlockEvent(state, world, pos, type, data);
        var blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.onSyncedBlockEvent(type, data);
    }
    
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return ((world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof ChuteBlockEntity chuteBlockEntity)
                chuteBlockEntity.tick(world1, pos, state1, chuteBlockEntity);
        });
    }
}
