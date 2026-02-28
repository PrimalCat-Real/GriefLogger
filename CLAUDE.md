# Claude Rules для GriefLogger

## Позелные источники
1. C:\Projects\mods\CoreProtect-master - плагин которые выступает в роли прородителя
2. C:\Projects\mods\GriefLogger-Rollback-Addon-main - адон на ролбеки с плавельной оптимизацией

## Формат кода
1. **Название переменных** - всегда називать переменные полным названием для сохранения понимания
2. **Цвета** - использовать ТОЛЬКО цвета из `Theme.java`, никогда не использовать raw `ChatFormatting` или hex напрямую

## Поиск библиотек и решений

### 1. Приоритет поиска
При поиске библиотек/решений для Minecraft мода **ВСЕГДА** начинай с:
1. **Modrinth** - искать готовые моды/библиотеки в комьюнити
2. **CurseForge** - альтернативный источник модов
3. **GitHub** - искать в репозиториях Minecraft модов
4. Только потом - общие Java библиотеки (ORMLite, JDBI и т.д.)

### 2. Проверка совместимости версий
При нахождении библиотеки/мода **ОБЯЗАТЕЛЬНО** проверь:
- Поддерживается ли **NeoForge** (не Forge, не Fabric отдельно)
- Поддерживается ли версия **1.21.1** (текущая версия проекта)
- Фильтруй результаты по версии на Modrinth/CurseForge

### 3. Как искать на Modrinth
1. Зайти на https://modrinth.com/mods
2. Отфильтровать по:
   - Game version: 1.21.1
   - Loader: NeoForge
3. Искать по ключевым словам

## Информация о проекте

- **Minecraft версия**: 1.21.1
- **Mod loader**: NeoForge
- **Тип проекта**: Форк GriefLogger
- **База данных**: SQLite / MySQL / PostgreSQL

### ВАЖНО: Только NeoForge
- **НЕ ТРОГАТЬ** `fabric/build.gradle` и другие Fabric файлы
- Все изменения делать только для **NeoForge**
- При добавлении зависимостей: `common/build.gradle` + `neoforge/build.gradle`

## Текущие задачи

См. `future_plan.md` для запланированных улучшений rollback системы.

---

## База данных

### Переключатель типа БД
```java
// GriefLogger.java
public static int DATABASE_TYPE = 0;  // 0=SQLite, 1=MySQL, 2=PostgreSQL
```
TODO: Вынести в конфиг

### Схема таблиц

```
users (id*, uuid UNIQUE, name)
usernames (id*, time, uuid, name, UNIQUE(uuid,name))
levels (id*, name UNIQUE)
materials (id*, name UNIQUE)
entities (id*, name UNIQUE)
block_states (id*, state_string UNIQUE)

blocks (time, user→users, level→levels, x, y, z, state_id→block_states, type→materials, action)
containers (time, user→users, level→levels, x, y, z, type→materials, data BLOB, amount, action)
items (time, user→users, level→levels, x, y, z, type→materials, data BLOB, amount, action)
sessions (time, user→users, level→levels, x, y, z, action)
chats (time, user→users, level→levels, x, y, z, message)
commands (time, user→users, level→levels, x, y, z, command)
```

### Action коды
- **blocks**: 0=place, 1=break, 2=interact, 3=entity
- **containers/items**: 0=deposit, 1=withdraw
- **sessions**: 0=login, 1=logout

---

## ORM (Query Builder)

### Расположение
```
database/orm/
├── Dialect.java          # MySQL/PostgreSQL/SQLite абстракция
├── query/
│   ├── Query.java        # Фасад: Query.select(), Query.insert(), etc.
│   ├── SelectBuilder.java
│   ├── InsertBuilder.java
│   ├── UpdateBuilder.java
│   ├── DeleteBuilder.java
│   └── WhereClause.java
└── schema/
    └── SchemaBuilder.java  # CREATE TABLE builder
```

### Примеры использования

#### SELECT
```java
Query.select("blocks")
    .columns("blocks.time", "users.name", "blocks.action")
    .join("users", "blocks.user = users.id")
    .whereEq("levels.name", levelName)
    .whereBetween("blocks.x", minX, maxX)
    .whereIn("blocks.action", List.of(0, 1))
    .orderByDesc("blocks.time")
    .limit(1000)
    .execute(database, rs -> mapResult(rs));
```

#### INSERT
```java
Query.insert("materials")
    .value("name", materialName)
    .ignore()  // INSERT IGNORE / INSERT OR IGNORE
    .queue(database);
```

#### CREATE TABLE
```java
SchemaBuilder.create("blocks")
    .bigint("time")
    .integer("user")
    .integer("x")
    .blob("data")
    .foreignKey("user", "users", "id")
    .index("coordinates", "x", "y", "z")
    .build(database);
```

### Dialect методы
| Метод | SQLite | MySQL | PostgreSQL |
|-------|--------|-------|------------|
| insertIgnore() | INSERT OR IGNORE | INSERT IGNORE | INSERT |
| onConflictDoNothing() | ON CONFLICT DO NOTHING | ON DUPLICATE KEY UPDATE id = id | ON CONFLICT DO NOTHING |
| autoIncrement() | AUTOINCREMENT | AUTO_INCREMENT | (SERIAL) |
| blobType() | blob | blob | bytea |

---

## Утилиты

### CompressionUtils
```java
// Сжатие NBT данных для БД (GZIP)
byte[] compressed = CompressionUtils.compress(rawBytes);
byte[] decompressed = CompressionUtils.decompress(compressed);
```
- Формат: 1 байт заголовок (0x00=raw, 0x01=gzip) + данные
- Автоматически пропускает сжатие для данных < 64 байт или если сжатие не уменьшает размер

### SimpleItemStack.tagFromBytes()
```java
// Десериализация с поддержкой сжатия и legacy данных
DataComponentPatch patch = SimpleItemStack.tagFromBytes(bytes, level);
```

---

## Сервисы

Все сервисы наследуют `IRepository` и используют ORM:
- `BlockService` - блоки, block_states
- `ContainerService` - инвентарь контейнеров
- `ItemService` - dropped/pickup items
- `SessionService` - login/logout
- `ChatService` - сообщения чата
- `CommandService` - команды
- `UserService` - игроки
- `UsernameService` - история имен
- `LevelService` - миры
- `MaterialService` - материалы
- `EntityService` - сущности

---

## Конфигурация

Расположение: `config/GriefLoggerConfig.java`
Файл конфига: `config/grieflogger.toml`

```toml
[database]
useMysql = false          # true для MySQL, false для SQLite
mysqlHost = "localhost"
mysqlPort = 3306
mysqlDatabase = "database"
mysqlUsername = "username"
mysqlPassword = "password"
mysqlTimeout = 5000
useIndexes = true         # улучшает скорость inspect/lookup

[general]
maxPageSize = 10          # записей на страницу (1-100)

[server]
serverSideOnlyMode = true

[queue]
queueFrequency = 20       # как часто выполнять очередь БД (в тиках)

[hello]
helloFrequency = 600      # частота hello пакета
```

---

## Команды

Префиксы: `/grieflogger` или `/gl`

| Команда | Описание |
|---------|----------|
| `/gl inspect` | Включить режим инспекции (ПКМ по блоку) |
| `/gl lookup [фильтры]` | Поиск в логах |
| `/gl rollback [фильтры]` | Откат изменений |
| `/gl page [номер]` | Навигация по страницам результата |

### Фильтры

| Фильтр | Синтаксис | Описание |
|--------|-----------|----------|
| `user` | `u:Steve` | По имени игрока |
| `time` | `t:1d` `t:2h30m` | За последние N времени |
| `radius` | `r:10` | Радиус в блоках |
| `action` | `a:break` `a:place` | По типу действия |
| `include` | `i:stone` | Включить материал |
| `exclude` | `e:dirt` | Исключить материал |

Примеры:
```
/gl lookup u:Steve t:1d r:50
/gl rollback u:Griefer t:2h a:break
```

---

## События (Events)

### Блоки
- `BreakContainerEvent` - разрушение контейнера (сундуки, бочки)
- `LeftClickBlockEvent` - ЛКМ по блоку
- `RightClickBlockEvent` - ПКМ по блоку
- `LogBlockEvent` - логирование блока
- `InspectBlockEvent` - инспекция блока
- `InspectContainerEvent` - инспекция контейнера
- `InspectDoorEvent` - инспекция двери

### Предметы
- `DropItemEvent` - выброс предмета
- `PickupItemEvent` - подбор предмета
- `CraftItemEvent` - крафт
- `SmeltItemEvent` - плавка
- `BreakItemEvent` - поломка инструмента
- `ConsumeItemEvent` - потребление (еда)
- `ThrowItemEvent` - бросок (жемчуг)
- `ShootItemEvent` - выстрел (стрелы)

### Игроки
- `PlayerJoinEvent` - вход на сервер
- `PlayerQuitEvent` - выход с сервера
- `ChatEvent` - сообщение в чат
- `CommandEvent` - выполнение команды

### Сущности
- `EntityEvents` - спавн/смерть сущностей

---

## Архитектура проекта

```
common/src/main/java/com/daqem/grieflogger/
├── block/              # Обработка блоков
│   ├── container/      # Контейнеры (сундуки, бочки)
│   └── coalesce/       # Объединение событий блоков
├── command/            # Команды мода
│   ├── filter/         # Фильтры для lookup/rollback
│   ├── argument/       # Аргументы команд
│   └── page/           # Пагинация
├── config/             # Конфигурация
├── database/           # БД
│   ├── cache/          # Кэширование (UserCache)
│   ├── orm/            # Query Builder
│   │   ├── annotation/ # @Table, @Column, @Id
│   │   ├── query/      # SelectBuilder, InsertBuilder...
│   │   └── schema/     # SchemaBuilder
│   ├── queue/          # Очередь запросов
│   └── service/        # Сервисы доступа к БД
├── event/              # Обработчики событий
│   ├── block/          # События блоков
│   └── item/           # События предметов
├── mixin/              # Mixin классы
├── model/              # Модели данных
│   ├── action/         # BlockAction, ItemAction
│   └── history/        # History классы для результатов
├── player/             # Расширения игрока
├── thread/             # Потоки и ThreadManager
└── util/               # Утилиты (CompressionUtils)
```

---

## Сборка проекта

```bash
# Сборка всех платформ
./gradlew build

# Только NeoForge
./gradlew neoforge:build

# Запуск клиента для тестов
./gradlew neoforge:runClient

# Запуск сервера для тестов
./gradlew neoforge:runServer
```

Артефакты: `neoforge/build/libs/`

---

## ORM Аннотации (WIP)

```java
@Table("users")
public class UserEntity {
    @Id
    private int id;

    @Column("uuid")
    private String uuid;

    @Column("name")
    private String name;
}
```

Аннотации пока в разработке, см. `database/orm/annotation/`

---

## Цветовая палитра (Theme + Kyori Adventure)

### Расположение
`util/Theme.java`

### Зависимость
```properties
# gradle.properties
kyori_version=4.17.0
```

### Цвета (только 7)

| Цвет | Hex | Использование |
|------|-----|---------------|
| `PRIMARY` | `#55FFFF` | Название мода, заголовки, важные метки |
| `SECONDARY` | `#FFFFFF` | Значения, описания |
| `ERROR` | `#FF5555` | Ошибки, предупреждения, удаление |
| `SUCCESS` | `#55FF55` | Подтверждения, добавление |
| `ACCENT` | `#FFAA00` | Акценты, важные значения |
| `MUTED` | `#AAAAAA` | Timestamps, координаты |
| `INFO` | `#5555FF` | Информационные сообщения |

### Использование

```java
// Простой цветной текст (возвращает Kyori Component)
Theme.primary("GriefLogger")
Theme.error("Error!")
Theme.success("Done!")

// Готовые паттерны сообщений
Theme.message("Info text")       // "GriefLogger - Info text"
Theme.errorMessage("Failed!")    // "GriefLogger - Failed!" (red)
Theme.header("Title")            // "----- Title -----"
Theme.labelValue("Status", "OK") // "Status: OK"

// Градиенты
Theme.gradient("#FF0000", "#00FF00", "Rainbow text")
Theme.gradient(List.of("#FF0000", "#FFFF00", "#00FF00"), "Multi-color")

// MiniMessage формат
Theme.parse("<gradient:red:blue>Gradient</gradient>")
Theme.parse("<bold><#FF5555>Custom hex</bold>")

// Конвертация в Minecraft Component (для sendSuccess и т.д.)
source.sendSuccess(() -> Theme.toMinecraft(Theme.header("Title")), false);
```

### Маппинг действий на цвета палитры
- **Добавление** (place, deposit, pickup) → `SUCCESS`
- **Удаление** (break, withdraw, drop) → `ERROR`
- **Взаимодействие** (click, interact) → `ACCENT`

### ВАЖНО
- **НИКОГДА** не использовать `ChatFormatting` напрямую
- **НИКОГДА** не использовать hex цвета вне Theme.java
- **ВСЕГДА** конвертировать через `Theme.toMinecraft()` для Minecraft API
- При необходимости нового цвета - добавить его в Theme.java

---

## Система фильтров

### Формат команд (CoreProtect-style)
```
/gl lookup u:Steve t:1d r:10 a:break
```

### Разделитель
- Основной: `:` (двоеточие)
- Fallback: `.` (точка) для совместимости

### Алиасы фильтров

| Фильтр | Алиасы |
|--------|--------|
| `user` | `u`, `user`, `users`, `p` |
| `time` | `t`, `time` |
| `radius` | `r`, `radius` |
| `action` | `a`, `action` |
| `include` | `i`, `include`, `b`, `block` |
| `exclude` | `e`, `exclude` |

### Формат времени
```
t:1d          # 1 день
t:2h30m       # 2 часа 30 минут
t:1w-1d       # от 1 недели до 1 дня назад (диапазон)
```

Единицы: `s` (секунды), `m` (минуты), `h` (часы), `d` (дни), `w` (недели), `mo` (месяцы), `y` (годы)

### Специальные значения
- `u:#global` - все игроки
- `r:#global` - весь мир
- `r:c4` - радиус в чанках (4 чанка = 64 блока)

### Action алиасы
```
+block / -block     # place / break
+container          # deposit
-container          # withdraw
kill                # kill entity
click               # interaction
+item / -item       # pickup / drop
```

---

## Подсказки для Claude (Self-Improvement)

### 0. Только NeoForge!
**НИКОГДА** не редактировать файлы в `fabric/` директории. Все изменения только для NeoForge.

### 1. Проверка зависимостей перед использованием
Перед использованием классов типа `GriefLogger.getDatabase()` или `GriefLogger.MOD_VERSION` - **сначала прочитай** исходный класс и проверь что методы/поля существуют.

### 2. Добавление зависимостей
При добавлении библиотек для Minecraft мода:
- Добавлять в `common/build.gradle` (implementation)
- Добавлять в `neoforge/build.gradle` (implementation + shadowBundle)
- **НЕ ТРОГАТЬ** `fabric/build.gradle`

### 3. Паттерн фильтров
При добавлении нового фильтра:
1. Создать класс, реализующий `IFilter`
2. Добавить в `Filters.java` (LIST и ALIASES)
3. Добавить suggestions в `getSuggestions()`
4. Добавить переводы в `en_us.json`

### 4. Паттерн команд
При добавлении новой команды:
1. Создать класс, реализующий `ICommand`
2. Зарегистрировать в `GriefLoggerCommand.java`
3. Использовать `Theme` для всех сообщений

### 5. Тестирование компиляции
После изменений **всегда** запускать:
```bash
./gradlew build
```

### 6. Backwards compatibility
При изменении форматов (например, разделитель фильтров):
- Сохранять поддержку старого формата как fallback
- Пример: поддержка и `:` и `.` в фильтрах

### 7. Структура кода
- Константы выносить в начало класса
- Использовать `private static final` для дефолтных значений
- Группировать связанные поля и методы

### 8. Локализация (НЕ ПРИОРИТЕТ)
- **НЕ ТРАТИТЬ ВРЕМЯ** на переводы и локализацию
- Файл `en_us.json` - только базовые ключи при необходимости
- Мультиязычность - не планируется в ближайшее время

---

## Логирование автоматических систем (Mixin Pattern)

### Проблема
Когда автоматические системы (чуты, хопперы, буры) перемещают предметы или ломают блоки, vanilla Minecraft не вызывает стандартные события. Нужны Mixin'ы для перехвата этих действий.

### Phantom Users (Фантомные пользователи)
Для атрибуции автоматических действий используются специальные "пользователи":
```java
// Формат: #<тип>[@<координаты>]
"#chute"                    // Простой чут
"#smart_chute@-14,84,-3"    // Smart Chute с координатами связанного блока
"#hopper"                   // Хоппер
"#drill"                    // Бур Create
```

### Регистрация Phantom User
```java
// В mixin перед логированием:
Services.USER.insertPhantomUser(phantomUser);
```

### AutomatedTransferTracker
**Проблема:** Когда игрок держит контейнер открытым, а автоматика перемещает предметы, оба системы пытаются залогировать изменение.

**Решение:** `AutomatedTransferTracker` - маркирует контейнеры с недавней автоматической активностью.

```java
// В mixin автоматики (chute, hopper, etc):
AutomatedTransferTracker.getInstance().markAutomatedActivity(containerPos);

// В ContainerTransactionManager:
if (AutomatedTransferTracker.getInstance().hasRecentAutomatedActivity(containerPos)) {
    // Пропустить логирование для игрока - автоматика уже залогировала
}
```

**Ключевые моменты:**
1. `markAutomatedActivity()` вызывается ДО логирования автоматикой
2. Suppression window = 200ms (4 тика)
3. `lastKnownState` ВСЕГДА обновляется, даже если логирование пропущено

### Пример Mixin для Create Chute
Расположение: `neoforge/src/main/java/com/daqem/grieflogger/neoforge/mixin/create/`

```java
@Mixin(value = ChuteBlockEntity.class, remap = false)
public abstract class MixinChuteBlockEntity extends BlockEntity {

    @Shadow public ItemStack item;
    @Shadow public abstract float getItemMotion();

    @Inject(method = "setItem(Lnet/minecraft/world/item/ItemStack;F)V", at = @At("HEAD"))
    private void grieflogger$onSetItem(ItemStack stack, float insertionPos, CallbackInfo ci) {
        Level level = this.getLevel();
        if (level == null || level.isClientSide()) return;

        BlockPos chutePos = this.getBlockPos();
        ItemStack oldItem = this.item;

        // Item LEAVING chute
        if (oldItem != null && !oldItem.isEmpty() && stack.isEmpty()) {
            float motion = getItemMotion();
            BlockPos targetPos = motion > 0 ? chutePos.above() : chutePos.below();
            grieflogger$logTransfer(level, chutePos, oldItem, ItemAction.REMOVE_ITEM, targetPos);
            if (!grieflogger$isChute(level, targetPos)) {
                grieflogger$logTransfer(level, targetPos, oldItem, ItemAction.ADD_ITEM, chutePos);
            }
        }

        // Item ENTERING chute
        if (!stack.isEmpty()) {
            BlockPos sourcePos = insertionPos > 0.5f ? chutePos.above() : chutePos.below();
            grieflogger$logTransfer(level, chutePos, stack, ItemAction.ADD_ITEM, sourcePos);
            if (!grieflogger$isChute(level, sourcePos)) {
                grieflogger$logTransfer(level, sourcePos, stack, ItemAction.REMOVE_ITEM, chutePos);
            }
        }
    }

    @Unique
    private void grieflogger$logTransfer(Level level, BlockPos pos, ItemStack stack, ItemAction action, BlockPos relatedPos) {
        // 1. Mark automated activity (prevents duplicate player logging)
        AutomatedTransferTracker.getInstance().markAutomatedActivity(pos);

        // 2. Build phantom user name
        String chuteType = grieflogger$getChuteType();  // "chute", "smart_chute", etc.
        String phantomUser = "#" + chuteType + "@" + relatedPos.getX() + "," + relatedPos.getY() + "," + relatedPos.getZ();

        // 3. Register phantom user and log
        Services.USER.insertPhantomUser(phantomUser);
        Services.CONTAINER.insertWithPhantom(phantomUser, level, pos, new SimpleItemStack(stack), action);

        // 4. Console log
        GriefLogger.LOGGER.info("[{}] Action={} Item={}x{} Pos={} Related={}",
                chuteType,
                action == ItemAction.ADD_ITEM ? "ADD" : "REMOVE",
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getCount(),
                pos.toShortString(),
                relatedPos.toShortString()
        );
    }

    @Unique
    private String grieflogger$getChuteType() {
        String className = this.getClass().getSimpleName();
        if (className.endsWith("BlockEntity")) {
            className = className.substring(0, className.length() - "BlockEntity".length());
        }
        return className.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
```

### Mixin конфигурация
Файл: `neoforge/src/main/resources/grieflogger-create.mixins.json`
```json
{
  "required": false,
  "minVersion": "0.8",
  "package": "com.daqem.grieflogger.neoforge.mixin.create",
  "compatibilityLevel": "JAVA_21",
  "mixins": [
    "MixinChuteBlockEntity"
  ],
  "plugin": "com.daqem.grieflogger.neoforge.mixin.create.CreateMixinPlugin"
}
```

### MixinPlugin для условной загрузки
```java
public class CreateMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Загружать mixin только если Create установлен
        return isClassLoaded("com.simibubi.create.Create");
    }

    private static boolean isClassLoaded(String className) {
        try {
            Class.forName(className, false, CreateMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    // ... остальные методы возвращают null/пустые коллекции
}
```

### Паттерн для блоков (буры, разрушители)
Для логирования разрушения блоков автоматикой:
```java
// В mixin бура/разрушителя:
@Inject(method = "breakBlock", at = @At("HEAD"))
private void grieflogger$onBreakBlock(BlockPos pos, CallbackInfo ci) {
    Level level = this.getLevel();
    if (level == null || level.isClientSide()) return;

    BlockState state = level.getBlockState(pos);
    String phantomUser = "#drill@" + this.getBlockPos().getX() + "," + this.getBlockPos().getY() + "," + this.getBlockPos().getZ();

    Services.USER.insertPhantomUser(phantomUser);
    Services.BLOCK.insertBlockStateWithPhantom(
            phantomUser,
            level.dimension().location().toString(),
            pos,
            state,
            BlockAction.BREAK_BLOCK
    );
}
```

### Queue Flush перед запросами
**Проблема:** Данные в очереди БД еще не записаны когда пользователь делает inspect.

**Решение:** Вызывать `flushQueues()` перед всеми запросами истории:
```java
// В InspectContainerEvent, LookupCommand, RollbackCommand, etc:
GriefLogger.getDatabase().flushQueues();
// ... затем запрос к БД
```

### Чеклист для нового Mixin
1. [ ] Найти метод для инъекции (setItem, breakBlock, etc.)
2. [ ] Определить тип действия (ItemAction или BlockAction)
3. [ ] Построить phantomUser с координатами
4. [ ] Вызвать `AutomatedTransferTracker.markAutomatedActivity()` если работа с контейнером
5. [ ] Зарегистрировать phantom user: `Services.USER.insertPhantomUser()`
6. [ ] Залогировать действие через соответствующий Service
7. [ ] Добавить консольный лог для дебага
8. [ ] Создать MixinPlugin для условной загрузки
9. [ ] Добавить в соответствующий mixins.json
10. [ ] Протестировать с `./gradlew build && ./gradlew neoforge:runServer`
