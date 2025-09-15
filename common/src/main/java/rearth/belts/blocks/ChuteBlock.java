package rearth.belts.blocks;

import com.mojang.serialization.MapCodec;
import dev.architectury.platform.Platform;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import rearth.belts.BlockEntitiesContent;
import rearth.belts.util.MathHelpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChuteBlock extends HorizontalFacingBlock implements BlockEntityProvider {
    
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();
    
    private VoxelShape createShapeForDirection(Direction direction) {
        return VoxelShapes.union(
          MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(2 / 16f, 4 / 16f, 14 / 16f, 14 / 16f, 1f, 1f), direction, BlockFace.FLOOR),
          MathHelpers.rotateVoxelShape(VoxelShapes.cuboid(3 / 16f, 5 / 16f, 16 / 16f, 13 / 16f, 15 / 16f, 18 / 16f), direction, BlockFace.FLOOR)
        ).simplify();
    }
    
    public ChuteBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }
    
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            if (!entity.isUsed()) return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
            if (!world.isClient)
                entity.assignFilterItem(stack, player);
            return ItemActionResult.SUCCESS;
        }
        
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }
    
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        
        var candidate = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (candidate.isPresent()) {
            var entity = candidate.get();
            if (!entity.isUsed()) return super.onUse(state, world, pos, player, hit);
            if (!world.isClient)
                entity.resetFilterItem(player);
            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
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
        return Objects.requireNonNull(super.getPlacementState(ctx)).with(Properties.HORIZONTAL_FACING, ctx.getSide());
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
    
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        
        if (world.isClient) return super.onBreak(world, pos, state, player);
        
        var chuteEntity = world.getBlockEntity(pos, BlockEntitiesContent.CHUTE_BLOCK.get());
        if (chuteEntity.isEmpty()) return super.onBreak(world, pos, state, player);
        
        chuteEntity.get().dropContent(world, pos);
        
        return super.onBreak(world, pos, state, player);
    }
    
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        
        var showExtra = Screen.hasControlDown();
        if (showExtra) {
            tooltip.add(Text.translatable("block.belts.chute.tooltip.1").formatted(Formatting.GRAY));
            if (Platform.isModLoaded("ftbfiltersystem"))
                tooltip.add(Text.translatable("block.belts.chute.tooltip.ftbfilters").formatted(Formatting.GRAY));
        }
        
        super.appendTooltip(stack, context, tooltip, options);
    }
}
