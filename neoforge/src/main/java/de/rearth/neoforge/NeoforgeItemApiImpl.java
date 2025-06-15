package de.rearth.neoforge;

import de.rearth.Belts;
import de.rearth.api.item.BlockItemApi;
import de.rearth.api.item.ItemApi;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

public class NeoforgeItemApiImpl implements BlockItemApi {
    
    @Override
    public ItemApi.InventoryStorage find(World world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity, @Nullable Direction direction) {
        
        var candidate = world.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, entity, direction);
        if (candidate == null) return null;
        return new NeoforgeStoragerWrapper(candidate);
    }
    
    // used to interact with storages from other mods. Belts really only uses the insert/extract methods, not the insertToSlot/extractFromSlot variants.
    public static class NeoforgeStoragerWrapper implements ItemApi.InventoryStorage {
        
        private final IItemHandler container;
        
        public NeoforgeStoragerWrapper(IItemHandler candidate) {
            this.container = candidate;
        }
        
        @Override
        public int insert(ItemStack inserted, boolean simulate) {
            return inserted.getCount() - ItemHandlerHelper.insertItem(container, inserted, simulate).getCount();
        }
        
        @Override
        public int insertToSlot(ItemStack inserted, int slot, boolean simulate) {
            return inserted.getCount() - container.insertItem(slot, inserted, simulate).getCount();
        }
        
        @Override
        public int extract(ItemStack extracted, boolean simulate) {
            var total = 0;
            for (int i = 0; i < container.getSlots(); i++) {
                var available = container.getStackInSlot(i);
                if (ItemStack.areItemsAndComponentsEqual(available, extracted)) {
                    total += container.extractItem(i, extracted.getCount() - total, simulate).getCount();
                }
            }
            
            return total;
        }
        
        @Override
        public int extractFromSlot(ItemStack extracted, int slot, boolean simulate) {
            return container.extractItem(slot, extracted.getCount(), simulate).getCount();
        }
        
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (container instanceof IItemHandlerModifiable handler) {
                handler.setStackInSlot(slot, stack);
            } else {
                Belts.LOGGER.error("Unable to set stack in slot: {}, stack is: {}", slot, stack);
                Belts.LOGGER.error("This should never happen");
            }
        }
        
        @Override
        public ItemStack getStackInSlot(int slot) {
            return container.getStackInSlot(slot);
        }
        
        @Override
        public int getSlotCount() {
            return container.getSlots();
        }
        
        @Override
        public int getSlotLimit(int slot) {
            return container.getSlotLimit(slot);
        }
    }
    
}
