# Результаты анализа: Create (mc1.21.1)

Путь к моду: `C:\Projects\mods\Create-mc1.21.1-dev`

---

## Логика предметов (Item Handlers)

| Класс | Метод | Тип действия | Описание | Приоритет |
|-------|-------|--------------|----------|-----------|
| [ChuteBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/logistics/chute/ChuteBlockEntity.java) | `handleDownwardOutput`, `handleUpwardOutput`, `handleInput` | ADD/REMOVE | Чут перемещает предметы вверх/вниз между инвентарями | **HIGH** |
| [FunnelBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/logistics/funnel/FunnelBlockEntity.java) | `activateExtractor`, `handleDirectBeltInput` | ADD/REMOVE | Воронка вставляет/извлекает предметы из соседних инвентарей | **HIGH** |
| [DeployerItemHandler](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/deployer/DeployerItemHandler.java) | `insertItem`, `extractItem` (через `setItemInHand`) | ADD/REMOVE | Деплоер держит и использует предметы | **HIGH** |
| [DeployerBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/deployer/DeployerBlockEntity.java) | `setItem` (slot management) | ADD/REMOVE | Внутренний слот деплоера | **HIGH** |
| [ToolboxBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/equipment/toolbox/ToolboxBlockEntity.java) | `setItem` (через `playerInv.setItem`) | ADD/REMOVE | Тулбокс синхронизирует предметы с хотбаром игрока | **MEDIUM** |
| [BasinBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/processing/basin/BasinBlockEntity.java) | `acceptOutputs`, `extractItem` (через `BasinInventory`) | ADD/REMOVE | Бассейн принимает/выдает предметы результата рецептов | **MEDIUM** |
| [SchematicannonBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/schematics/cannon/SchematicannonBlockEntity.java) | `grabItemsFromAttachedInventories`, `extractItem` | REMOVE | Пушка забирает блоки из соседних инвентарей для строительства | **HIGH** |
| [GlobalStation](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/trains/station/GlobalStation.java) | `insertItemStacked` (carriageInventory) | ADD | Станция загружает предметы в вагоны поезда | **MEDIUM** |
| [Train](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/trains/entity/Train.java) | `extractItem` / `insertItemStacked` (fuelItems) | ADD/REMOVE | Поезд потребляет топливо из инвентаря | **LOW** |
| [SawBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/saw/SawBlockEntity.java) | `insertItem` (через обработку рецептов) | ADD | Пила выдает результат обработки | **MEDIUM** |
| [DrillBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/drill/DrillBlockEntity.java) | `insertItemStacked` / `setItem` (оптим. cobblegen) | ADD | Бур кладет дроп в хоппер/чут/ленту ниже | **MEDIUM** |
| [EjectorBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/logistics/depot/EjectorBlockEntity.java) | `extractItem` → `addToLaunchedItems` | REMOVE | Катапульта выбрасывает предметы | **LOW** |
| [ItemHandlerContainer](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/foundation/blockEntity/ItemHandlerContainer.java) | `setItem`, `removeItem` | ADD/REMOVE | Базовый контейнер для многих BlockEntity Create | **LOW** |

---

## Логика блоков (Block Breaking/Placing)

| Класс | Метод | Тип действия | Описание | Приоритет |
|-------|-------|--------------|----------|-----------|
| [BlockBreakingKineticBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity.java) | `onBlockBroken` → `BlockHelper.destroyBlock` | BREAK | **Базовый класс** для всех ломающих механизмов (бур, пила) | **HIGH** |
| [DrillBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/drill/DrillBlockEntity.java) | `onBlockBroken` (наследует) | BREAK | Механический бур ломает блоки | **HIGH** |
| [SawBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/saw/SawBlockEntity.java) | `onBlockBroken` (наследует) | BREAK | Механическая пила ломает деревья/блоки | **HIGH** |
| [DeployerHandler](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/deployer/DeployerHandler.java) | `tryHarvestBlock` | BREAK | Деплоер ломает блоки (режим PUNCH через FakePlayer) | **HIGH** |
| [DeployerHandler](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/deployer/DeployerHandler.java) | `activateInner` (right-click) | PLACE | Деплоер ставит блоки (режим USE через FakePlayer) | **HIGH** |
| [SchematicannonBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/schematics/cannon/SchematicannonBlockEntity.java) | `tickPrinter` → `level.setBlock` | PLACE | Пушка ставит блоки из схематики | **HIGH** |
| [RollerMovementBehaviour](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/contraptions/actors/roller/RollerMovementBehaviour.java) | `level.setBlockAndUpdate` | PLACE | Ролик на конструкции ставит блоки (дорогу) | **HIGH** |
| [ManualApplicationRecipe](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/kinetics/deployer/ManualApplicationRecipe.java) | `level.destroyBlock` + `level.setBlock` | BREAK/PLACE | Деплоер применяет рецепт (разрушает → ставит трансформированный блок) | **MEDIUM** |
| [PulleyBlockEntity](file:///C:/Projects/mods/Create-mc1.21.1-dev/src/main/java/com/simibubi/create/content/contraptions/pulley/PulleyBlockEntity.java) | `level.destroyBlock` / `level.setBlock` | BREAK/PLACE | Шкив ставит/убирает верёвки и магниты | **LOW** |

---

## Дополнительная информация по ключевым классам

### BlockBreakingKineticBlockEntity (BREAK — HIGH)
- **Путь**: `content/kinetics/base/BlockBreakingKineticBlockEntity.java`
- **Метод для инъекции**: `onBlockBroken(BlockState stateToBreak)` (line 139)
- **Доступные параметры**: `level` (Level), `breakingPos` (BlockPos), `stateToBreak` (BlockState)
- **Проверка `isClientSide`**: ✅ Да, в `tick()` (line 88): `if (level.isClientSide) return;`
- **Примечание**: Это базовый класс — перехват здесь покроет и `DrillBlockEntity`, и `SawBlockEntity`

### DeployerHandler.tryHarvestBlock (BREAK — HIGH)
- **Путь**: `content/kinetics/deployer/DeployerHandler.java`
- **Метод для инъекции**: `tryHarvestBlock(ServerPlayer player, ServerPlayerGameMode interactionManager, BlockPos pos)` (line 375)
- **Доступные параметры**: `player` (ServerPlayer / DeployerFakePlayer), `pos` (BlockPos), `level` из player
- **Проверка `isClientSide`**: ✅ Неявно — метод принимает `ServerPlayer` и `ServerPlayerGameMode`

### DeployerHandler.activateInner (PLACE — HIGH)
- **Путь**: `content/kinetics/deployer/DeployerHandler.java`
- **Метод для инъекции**: `activateInner(...)` (line 156) — после right-click block placement
- **Доступные параметры**: `player` (DeployerFakePlayer), `clickedPos` (BlockPos), `level` из player, `stack` (ItemStack)
- **Проверка `isClientSide`**: ✅ Неявно — `level` это `ServerLevel`

### SchematicannonBlockEntity.tickPrinter (PLACE — HIGH)
- **Путь**: `content/schematics/cannon/SchematicannonBlockEntity.java`
- **Метод для инъекции**: `tickPrinter()` (line 318) — при установке блоков
- **Доступные параметры**: `level`, `currentPos` (BlockPos), `blockState` (BlockState)
- **Проверка `isClientSide`**: ✅ Да, в `tick()` проверяется перед вызовом `tickPrinter()`

### ChuteBlockEntity (ITEM — HIGH)
- **Путь**: `content/logistics/chute/ChuteBlockEntity.java`
- **Методы для инъекции**: `setItem(ItemStack, float)`, `handleDownwardOutput(boolean)`, `handleUpwardOutput(boolean)`
- **Доступные параметры**: `level`, `worldPosition` (BlockPos), `item` (ItemStack)
- **Проверка `isClientSide`**: ✅ Да, в `tick()`: `if (level.isClientSide) return;` (для серверной логики)

### FunnelBlockEntity (ITEM — HIGH)
- **Путь**: `content/logistics/funnel/FunnelBlockEntity.java`
- **Методы для инъекции**: `activateExtractor()` (line 120), `handleDirectBeltInput(...)` (line 304)
- **Доступные параметры**: `level`, `worldPosition` (BlockPos), `stack` (ItemStack через `invManipulation`)
- **Проверка `isClientSide`**: ✅ Да, в `tick()`

### RollerMovementBehaviour (PLACE — HIGH)
- **Путь**: `content/contraptions/actors/roller/RollerMovementBehaviour.java`
- **Метод для инъекции**: Место вызова `level.setBlockAndUpdate(targetPos, toPlace)` (line 483)
- **Доступные параметры**: `level`, `targetPos` (BlockPos), `toPlace` (BlockState)
- **Проверка `isClientSide`**: Нужно проверять перед инъекцией

---

## Рекомендуемый порядок реализации

1. **BlockBreakingKineticBlockEntity** — покрывает бур и пилу одним Mixin'ом (максимальный охват)
2. **DeployerHandler** — деплоер один из самых мощных инструментов Create (ставит/ломает блоки)
3. **ChuteBlockEntity** — чуты очень часто используются для автоматизации
4. **FunnelBlockEntity** — воронки критичны для логистики Create
5. **SchematicannonBlockEntity** — схематик-пушка может ставить большое количество блоков
6. **RollerMovementBehaviour** — ролик на конструкции может массово ставить блоки
7. **ToolboxBlockEntity** — полезно для отслеживания синхронизации инвентаря
8. **BasinBlockEntity** — бассейн (рецепты переработки)
9. **GlobalStation / Train** — логистика поездов
