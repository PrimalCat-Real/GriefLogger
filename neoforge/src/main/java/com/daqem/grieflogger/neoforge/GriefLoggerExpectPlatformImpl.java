package com.daqem.grieflogger.neoforge;

import com.daqem.grieflogger.GriefLoggerExpectPlatform;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class GriefLoggerExpectPlatformImpl {
    /**
     * This is our actual method to {@link GriefLoggerExpectPlatform#getConfigDirectory()}.
     */
    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static int insertItem(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        net.neoforged.neoforge.items.IItemHandler itemHandler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
        if (itemHandler == null) return stack.getCount();
        
        net.minecraft.world.item.ItemStack remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(itemHandler, stack.copy(), false);
        return remainder.getCount();
    }

    public static int extractItem(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        net.neoforged.neoforge.items.IItemHandler itemHandler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
        if (itemHandler == null) return 0;
        
        int toRemove = stack.getCount();
        int removed = 0;
        for (int i = 0; i < itemHandler.getSlots() && removed < toRemove; i++) {
            net.minecraft.world.item.ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(slotStack, stack)) {
                int available = slotStack.getCount();
                int take = Math.min(available, toRemove - removed);
                net.minecraft.world.item.ItemStack extracted = itemHandler.extractItem(i, take, false);
                removed += extracted.getCount();
            }
        }
        return removed;
    }
}
