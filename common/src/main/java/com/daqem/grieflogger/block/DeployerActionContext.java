package com.daqem.grieflogger.block;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;

/** Carries the stationary deployer position through synchronous fake-player events. */
public final class DeployerActionContext {

    private static final ThreadLocal<Deque<BlockPos>> DEPLOYER_POSITIONS = new ThreadLocal<>();

    private DeployerActionContext() {
    }

    public static void begin(BlockPos deployerPosition) {
        Deque<BlockPos> positions = DEPLOYER_POSITIONS.get();
        if (positions == null) {
            positions = new ArrayDeque<>();
            DEPLOYER_POSITIONS.set(positions);
        }
        positions.push(deployerPosition.immutable());
    }

    public static void end() {
        Deque<BlockPos> positions = DEPLOYER_POSITIONS.get();
        if (positions == null) {
            return;
        }
        if (!positions.isEmpty()) {
            positions.pop();
        }
        if (positions.isEmpty()) {
            DEPLOYER_POSITIONS.remove();
        }
    }

    public static BlockPos getPosition() {
        Deque<BlockPos> positions = DEPLOYER_POSITIONS.get();
        return positions == null || positions.isEmpty() ? null : positions.peek();
    }
}
