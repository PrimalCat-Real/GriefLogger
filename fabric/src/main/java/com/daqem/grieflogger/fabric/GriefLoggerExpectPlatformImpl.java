package com.daqem.grieflogger.fabric;

import com.daqem.grieflogger.GriefLoggerExpectPlatform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class GriefLoggerExpectPlatformImpl {
    /**
     * This is our actual method to {@link GriefLoggerExpectPlatform#getConfigDirectory()}.
     */
    public static Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static int insertItem(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        // Not implemented on Fabric for now, return full count to fallback to default logic
        return stack.getCount();
    }

    public static int extractItem(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        // Not implemented on Fabric for now, return 0 to fallback to default logic
        return 0;
    }
}
