package com.daqem.grieflogger.neoforge.mixin.create;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin plugin that conditionally enables Create mod mixins.
 * Only applies mixins if Create mod is present.
 */
public class CreateMixinPlugin implements IMixinConfigPlugin {

    private static boolean createLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            createLoaded = getClass().getClassLoader()
                    .getResource("com/simibubi/create/content/logistics/chute/ChuteBlockEntity.class") != null;
        } catch (Exception e) {
            createLoaded = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return createLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    public static boolean isCreateLoaded() {
        return createLoaded;
    }
}
