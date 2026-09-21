package com.daqem.grieflogger.neoforge.mixin.create;

import com.daqem.grieflogger.block.DeployerActionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes the stationary deployer as the source of nested fake-player events. */
@Mixin(value = DeployerBlockEntity.class, remap = false)
public abstract class DeployerBlockEntityMixin {

    @WrapMethod(method = "activate")
    private void grieflogger$trackDeployerAction(Operation<Void> original) {
        DeployerBlockEntity deployerBlockEntity = (DeployerBlockEntity) (Object) this;
        DeployerActionContext.begin(deployerBlockEntity.getBlockPos());
        try {
            original.call();
        } finally {
            DeployerActionContext.end();
        }
    }
}
