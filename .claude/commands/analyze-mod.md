# Analyze Mod for GriefLogger Integration

Анализирует исходный код мода для поиска логики работы с предметами и блоками, которую нужно залогировать через GriefLogger.

## Использование
```
/analyze-mod <путь_к_исходникам_мода>
```

## Пример
```
/analyze-mod C:\Projects\mods\Create
```

## Инструкция для Claude

### 1. Поиск классов с логикой предметов
Найди все классы которые:
- Наследуют `BlockEntity` и имеют `ItemStackHandler` или `IItemHandler`
- Содержат методы `setItem`, `insertItem`, `extractItem`
- Работают с `Container` или `Inventory`
- Имеют логику перемещения предметов между позициями

### 2. Поиск классов с логикой блоков
Найди все классы которые:
- Вызывают `level.setBlock()` или `level.destroyBlock()`
- Содержат методы типа `breakBlock`, `placeBlock`, `mineBlock`
- Реализуют добычу/разрушение блоков (буры, разрушители)

### 3. Формат вывода
Создай таблицу в формате:

```markdown
## Результаты анализа: [Название мода]

### Логика предметов (Item Handlers)

| Класс | Метод | Тип действия | Описание | Приоритет |
|-------|-------|--------------|----------|-----------|
| ChuteBlockEntity | setItem | ADD/REMOVE | Чут перемещает предметы | HIGH |
| FunnelBlockEntity | insertItem | ADD | Воронка вставляет предметы | MEDIUM |

### Логика блоков (Block Breaking/Placing)

| Класс | Метод | Тип действия | Описание | Приоритет |
|-------|-------|--------------|----------|-----------|
| MechanicalDrillBlockEntity | breakWithProgress | BREAK | Бур ломает блоки | HIGH |
| DeployerBlockEntity | placeBlock | PLACE | Деплоер ставит блоки | HIGH |

### Рекомендуемый порядок реализации
1. [Класс] - [причина приоритета]
2. ...
```

### 4. Критерии приоритета
- **HIGH**: Часто используется игроками, критично для логирования
- **MEDIUM**: Полезно логировать, но не критично
- **LOW**: Редко используется или сложно реализовать

### 5. Что искать в коде
```java
// Паттерны для предметов:
inventory.setItem(slot, stack)
handler.insertItem(slot, stack, simulate)
handler.extractItem(slot, amount, simulate)
container.setChanged()

// Паттерны для блоков:
level.setBlock(pos, state, flags)
level.destroyBlock(pos, dropResources)
level.removeBlock(pos, moving)
BlockEvent.BreakEvent
```

### 6. Дополнительная информация
Для каждого найденного класса укажи:
- Полный путь к файлу
- Сигнатуру метода для инъекции
- Какие параметры доступны (BlockPos, Level, ItemStack, etc.)
- Есть ли проверка `level.isClientSide()`
