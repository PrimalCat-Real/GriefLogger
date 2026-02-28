---
description: Создает Mixin для логирования действий автоматических систем
---
# Create Mixin for GriefLogger

Создает Mixin для логирования действий автоматических систем (чуты, буры, хопперы и т.д.).

## Использование
Данный workflow будет запущен по запросу пользователя. Пользователь должен указать мод, класс, метод и тип (item или block).

## Примеры вызова от пользователя
`Создай mixin для мода create, класс ChuteBlockEntity, метод setItem, тип item`
`Сделай миксин для MechanicalDrillBlockEntity (create), метод breakWithProgress, тип block`

## Инструкция для Antigravity

### 1. Прочитать ANTIGRAVITY.md
Сначала прочитай секцию "Логирование автоматических систем (Mixin Pattern)" в ANTIGRAVITY.md (ранее CLAUDE.md) для понимания паттерна.

### 2. Структура файлов

Если папка `mixins/<mod_name>/` ещё не существует — создай её. Никогда не добавляй название мода к имени файла, только используй его как имя подпапки.

```
neoforge/src/main/java/com/daqem/grieflogger/neoforge/mixin/
└── <mod_name>/                          # Создать если не существует
    ├── Mixin<ClassName>.java            # Сам mixin
    └── <ModName>MixinPlugin.java        # Plugin для условной загрузки

neoforge/src/main/resources/
└── grieflogger-<mod_name>.mixins.json
```

**Примеры правильных путей:**
- `/mixin/create/MixinChuteBlockEntity.java` ✅
- `/mixin/MixinCreateChuteBlockEntity.java` ❌ (название мода в имени файла — неверно)

### 3. Шаблон Mixin для ПРЕДМЕТОВ (item)
```java
package com.daqem.grieflogger.neoforge.mixin.<mod_name>;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.block.container.AutomatedTransferTracker;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.SimpleItemStack;
import com.daqem.grieflogger.model.action.ItemAction;
// ... импорты целевого класса
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = <TargetClass>.class, remap = false)
public abstract class Mixin<ClassName> extends BlockEntity {

    // Shadow необходимые поля из целевого класса
    @Shadow public ItemStack item;  // пример

    public Mixin<ClassName>() {
        super(null, null, null);
    }

    @Inject(method = "<targetMethod>", at = @At("HEAD"))
    private void grieflogger$on<Method>(/* параметры метода */, CallbackInfo ci) {
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockPos pos = this.getBlockPos();

        // Определить старое и новое состояние
        ItemStack oldItem = this.item;
        ItemStack newItem = /* из параметров */;

        // Логика определения ADD/REMOVE
        if (/* item removed */) {
            grieflogger$logTransfer(level, pos, oldItem, ItemAction.REMOVE_ITEM, /* relatedPos */);
        }
        if (/* item added */) {
            grieflogger$logTransfer(level, pos, newItem, ItemAction.ADD_ITEM, /* relatedPos */);
        }
    }

    @Unique
    private void grieflogger$logTransfer(Level level, BlockPos pos, ItemStack stack, ItemAction action, BlockPos relatedPos) {
        try {
            // 1. Маркировать автоматическую активность
            AutomatedTransferTracker.getInstance().markAutomatedActivity(pos);

            // 2. Построить phantom user
            String deviceType = grieflogger$getDeviceType();
            String phantomUser = "#" + deviceType;
            if (relatedPos != null) {
                phantomUser += "@" + relatedPos.getX() + "," + relatedPos.getY() + "," + relatedPos.getZ();
            }

            // 3. Зарегистрировать и залогировать
            Services.USER.insertPhantomUser(phantomUser);
            Services.CONTAINER.insertWithPhantom(
                    phantomUser,
                    level,
                    pos,
                    new SimpleItemStack(stack),
                    action
            );

            // 4. Консольный лог
            GriefLogger.LOGGER.info("[{}] Action={} Item={}x{} Pos={} Related={}",
                    deviceType,
                    action == ItemAction.ADD_ITEM ? "ADD" : "REMOVE",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    stack.getCount(),
                    pos.toShortString(),
                    relatedPos != null ? relatedPos.toShortString() : "none"
            );
        } catch (Exception e) {
            GriefLogger.LOGGER.error("Failed to log transfer", e);
        }
    }

    @Unique
    private String grieflogger$getDeviceType() {
        String className = this.getClass().getSimpleName();
        if (className.endsWith("BlockEntity")) {
            className = className.substring(0, className.length() - "BlockEntity".length());
        }
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
```

### 4. Шаблон Mixin для БЛОКОВ (block)
```java
package com.daqem.grieflogger.neoforge.mixin.<mod_name>;

import com.daqem.grieflogger.GriefLogger;
import com.daqem.grieflogger.database.service.Services;
import com.daqem.grieflogger.model.action.BlockAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = <TargetClass>.class, remap = false)
public abstract class Mixin<ClassName> extends BlockEntity {

    public Mixin<ClassName>() {
        super(null, null, null);
    }

    @Inject(method = "<breakMethod>", at = @At("HEAD"))
    private void grieflogger$onBreakBlock(BlockPos targetPos, /* другие параметры */, CallbackInfo ci) {
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.isAir()) return;

        grieflogger$logBlockBreak(level, targetPos, targetState);
    }

    @Unique
    private void grieflogger$logBlockBreak(Level level, BlockPos pos, BlockState state) {
        try {
            BlockPos devicePos = this.getBlockPos();
            String deviceType = grieflogger$getDeviceType();
            String phantomUser = "#" + deviceType + "@" + devicePos.getX() + "," + devicePos.getY() + "," + devicePos.getZ();

            Services.USER.insertPhantomUser(phantomUser);
            Services.BLOCK.insertBlockStateWithPhantom(
                    phantomUser,
                    level.dimension().location().toString(),
                    pos,
                    state,
                    BlockAction.BREAK_BLOCK
            );

            GriefLogger.LOGGER.info("[{}] Action=BREAK Block={} Pos={} Device={}",
                    deviceType,
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    pos.toShortString(),
                    devicePos.toShortString()
            );
        } catch (Exception e) {
            GriefLogger.LOGGER.error("Failed to log block break", e);
        }
    }

    @Unique
    private String grieflogger$getDeviceType() {
        String className = this.getClass().getSimpleName();
        if (className.endsWith("BlockEntity")) {
            className = className.substring(0, className.length() - "BlockEntity".length());
        }
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
```

### 5. Шаблон MixinPlugin
```java
package com.daqem.grieflogger.neoforge.mixin.<mod_name>;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class <ModName>MixinPlugin implements IMixinConfigPlugin {

    private static final String MOD_CLASS = "<полный.путь.к.главному.классу.мода>";

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return isModLoaded();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static boolean isModLoaded() {
        try {
            Class.forName(MOD_CLASS, false, <ModName>MixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

### 6. Шаблон mixins.json
Файл: `neoforge/src/main/resources/grieflogger-<mod_name>.mixins.json`
```json
{
  "required": false,
  "minVersion": "0.8",
  "package": "com.daqem.grieflogger.neoforge.mixin.<mod_name>",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "Mixin<ClassName>"
  ],
  "plugin": "com.daqem.grieflogger.neoforge.mixin.<mod_name>.<ModName>MixinPlugin"
}
```

### 7. Регистрация в neoforge.mods.toml
Добавить в `neoforge/src/main/resources/META-INF/neoforge.mods.toml`:
```toml
[[mixins]]
config = "grieflogger-<mod_name>.mixins.json"
```

### 8. Чеклист после создания
Выведи результат в артефакт (если требуется) или просто отчитайся после создания файлов:
- [ ] Файл Mixin создан в правильной директории
- [ ] MixinPlugin создан с правильным MOD_CLASS
- [ ] mixins.json создан и заполнен
- [ ] Добавлен в neoforgemods.toml
- [ ] `./gradlew build` проходит
- [ ] Готово для теста в игре

### 9. Частые ошибки
1. **remap = false** - обязательно для модов, использующих не-обфусцированные имена
2. **CallbackInfo vs CallbackInfoReturnable** - зависит от возвращаемого типа метода
3. **Dummy constructor** - нужен если extends BlockEntity
4. **level.isClientSide()** - всегда проверять в начале
