# Mixins Documentation

This document explains the Mixins used in GriefLogger to intercept vanilla events and mod mechanics (specifically the Create mod).

## Vanilla Server Mixins
*   **`ServerLevelMixin`**: Tracks the final progress of block breaking events, noting the block position and breaker ID.
*   **`MixinServerPlayer`**: Operates strictly on the server side. Responsibilities include processing item queues, logging item changes to the console, and ticking the container transaction manager for real-time tracking of player inventories and actions.
*   **`LevelMixin`**: Injects into `markAndNotifyBlock` to capture block state changes directly at the level logic. (Note: Signature may need fixing in future refactors).

## Create Mod Integrations (`neoforge.mixin.create.*`)

The integration with the Create mod heavily relies on intercepting item transfers between contraptions.

### Initialization (`CreateMixinPlugin.java`)
*   Safely checks if the Create mod is loaded by checking if its core class resource exists before applying mixins.
*   **Important**: Uses resource checking instead of `Class.forName()` to avoid loading classes too early in the classloader.

### Conveyor Belts (`BeltInventoryMixin.java`)
*   **Logic**: Intercepts the original item insertion method. If the operation is not a simulation and the resulting item stack shrinks (meaning items actually moved), it calculates the exact world coordinates based on the item's position on the long conveyor belt and logs the difference.

### Smart Chutes (`ChuteBlockEntityMixin.java`)
*   **Logic**: Deals with items entering and leaving chutes.
*   **Leaving**: When the old slot has items and the new one is empty, it determines the direction based on item motion, logs the item leaving the chute, and logs it entering the target inventory (unless it's another chute).
*   **Entering**: Logs items entering the chute and leaving the source inventory.
*   **Attribution/Anti-spam**: Marks the target container as having *automated activity*. This prevents the main `ContainerTransactionManager` from falsely attributing these machine actions to players.
*   **Dynamism**: Dynamically determines the specific chute type (Smart, normal, etc.) by extracting the simple class name, stripping the "BlockEntity" suffix, and converting from CamelCase to snake_case (e.g., `SmartChute` -> `smart_chute`).

### Mechanical Arms (`ArmBlockEntityMixin.java`)
*   **Logic**: Requires fine-grained control around the method execution (using temporary variables to store states between `HEAD` and `RETURN`).
*   **Taking Items**: Remembers the container position before indices reset, remembers what was in the arm's hand, and calculates precisely how many items were taken. The source container logs a `REMOVE` action.
*   **Depositing Items**: Remembers the target position, calculates how many items were placed. The target container logs an `ADD` action.
*   **Data Generation**: For every mechanical arm action, two transaction logs are produced: 
    1. A log for the source/target container (inverting the action: if chest gave item, the arm took it).
    2. A mirror log for the mechanical arm itself as an entity.
