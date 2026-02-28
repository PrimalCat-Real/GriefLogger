package com.daqem.grieflogger;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.architectury.platform.Platform;

import java.nio.file.Path;

public class GriefLoggerExpectPlatform {

    @ExpectPlatform
    public static Path getConfigDirectory() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static int insertItem(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static int extractItem(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        throw new AssertionError();
    }
}
