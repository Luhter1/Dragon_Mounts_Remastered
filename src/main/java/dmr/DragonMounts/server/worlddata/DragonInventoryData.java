package dmr.DragonMounts.server.worlddata;

import dmr.DragonMounts.DMR;
import dmr.DragonMounts.server.inventory.DragonInventoryHandler.DragonInventory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * Global, dimension-independent storage of dragon inventories keyed by the dragon's stable UUID.
 *
 * <p>Unlike {@link DragonWorldData}, this data lives in the overworld's dimension storage so a
 * dragon's equipment and items are shared across all dimensions and never need to be manually
 * transferred between them.
 */
public class DragonInventoryData extends SavedData {

    private static final String NAME = DMR.MOD_ID + "_dragon_inventory_data";

    public Map<UUID, DragonInventory> dragonInventories = new HashMap<>();

    public static DragonInventoryData getInstance(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new RuntimeException("Attempted to get the data from a client world. This is wrong.");
        }

        ServerLevel overworld = serverLevel.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(factory(), NAME);
    }

    public static SavedData.Factory<DragonInventoryData> factory() {
        return new SavedData.Factory<>(DragonInventoryData::new, DragonInventoryData::load, DataFixTypes.LEVEL);
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);

        if (!dirty) {
            dragonInventories.forEach((uuid, inventory) -> {
                if (inventory != null) {
                    inventory.setDirty(false);
                }
            });
        }
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || dragonInventories.values().stream().anyMatch(DragonInventory::isDirty);
    }

    public static DragonInventoryData load(CompoundTag nbt, Provider provider) {
        DragonInventoryData data = new DragonInventoryData();

        var inventories = nbt.getCompound("dragonInventories");
        for (String key : inventories.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                DragonInventory inventory = new DragonInventory(provider);
                inventory.readNBT(inventories.getCompound(key));
                data.dragonInventories.put(uuid, inventory);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed or legacy entries
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag pCompoundTag, Provider provider) {
        CompoundTag tag = new CompoundTag();

        CompoundTag inventoriesTag = new CompoundTag();
        for (Map.Entry<UUID, DragonInventory> entry : dragonInventories.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            DragonInventory inventory = entry.getValue();
            if (inventory.inventory.getItems().stream().allMatch(ItemStack::isEmpty)) continue;
            inventoriesTag.put(entry.getKey().toString(), inventory.writeNBT());
        }

        tag.put("dragonInventories", inventoriesTag);
        return tag;
    }
}