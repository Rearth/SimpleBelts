package rearth.belts.fabric;

import rearth.belts.Belts;
import rearth.belts.api.item.BlockItemApi;
import rearth.belts.api.item.ItemApi;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class FabricItemApi implements BlockItemApi {
    
    @Override
    public ItemApi.InventoryStorage find(World world, BlockPos pos, @Nullable BlockState state, @Nullable BlockEntity entity, @Nullable Direction direction) {
        var candidate = ItemStorage.SIDED.find(world, pos, state, entity, direction);
        if (candidate == null) return null;
        return new FabricStorageWrapper(candidate);
    }
    
    // used to interact with storages from other mods
    public static class FabricStorageWrapper implements ItemApi.InventoryStorage {
        
        public final Storage<ItemVariant> storage;
        
        public FabricStorageWrapper(Storage<ItemVariant> storage) {
            this.storage = storage;
        }
        
        @Override
        public boolean supportsInsertion() {
            return storage.supportsInsertion();
        }
        
        @Override
        public int insert(ItemStack inserted, boolean simulate) {
            if (inserted.isEmpty()) return 0;
            try (var transaction = Transaction.openOuter()) {
                var insertCount = storage.insert(ItemVariant.of(inserted), inserted.getCount(), transaction);
                if (!simulate)
                    transaction.commit();
                return (int) insertCount;
            }
        }
        
        @Override
        public int insertToSlot(ItemStack inserted, int slot, boolean simulate) {
            if (inserted.isEmpty()) return 0;
            
            // this usually won't be used
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                try (var transaction = Transaction.openOuter()) {
                    var insertCount = slottedStorage.getSlot(slot).insert(ItemVariant.of(inserted), inserted.getCount(), transaction);
                    if (!simulate)
                        transaction.commit();
                    return (int) insertCount;
                }
                
            }
            
            return 0;
        }
        
        @Override
        public boolean supportsExtraction() {
            return storage.supportsExtraction();
        }
        
        @Override
        public int extract(ItemStack extracted, boolean simulate) {
            if (extracted.isEmpty()) return 0;
            try (var transaction = Transaction.openOuter()) {
                var extractedCount = storage.extract(ItemVariant.of(extracted), extracted.getCount(), transaction);
                if (!simulate)
                    transaction.commit();
                return (int) extractedCount;
            }
        }
        
        @Override
        public int extractFromSlot(ItemStack extracted, int slot, boolean simulate) {
            if (extracted.isEmpty()) return 0;
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                try (var transaction = Transaction.openOuter()) {
                    var extractedCount = slottedStorage.getSlot(slot).extract(ItemVariant.of(extracted), extracted.getCount(), transaction);
                    if (!simulate)
                        transaction.commit();
                    return (int) extractedCount;
                }
                
            }
            
            return 0;
        }
        
        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            Belts.LOGGER.error("Unable to set stack in slot: {}, stack is: {}", slot, stack);
            Belts.LOGGER.error("This should never happen");
        }
        
        @Override
        public ItemStack getStackInSlot(int slot) {
            // this usually won't be used
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                return slottedStorage.getSlot(slot).getResource().toStack((int) slottedStorage.getSlot(slot).getAmount());
            }
            
            return ItemStack.EMPTY;
        }
        
        @Override
        public int getSlotCount() {
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                return slottedStorage.getSlotCount();
            }
            
            return 1;
        }
        
        @Override
        public int getSlotLimit(int slot) {
            
            if (storage instanceof SlottedStorage<ItemVariant> slottedStorage) {
                return (int) slottedStorage.getSlot(slot).getCapacity();
            }
            
            return 64;
        }
    }
    
    
    
}
