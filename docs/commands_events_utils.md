# Events, Commands, Models & Utils Documentation

This document covers user interaction points (Commands), Minecraft Event interceptors, data structures, and miscellaneous utilities.

## Commands

Commands parse human-readable filters into database queries and trigger rollbacks or lookups.

### Filters (`FilterList.java`, `IFilter.java`, `ActionFilter.java`, etc.)
*   **Syntax**: Supports modern format (`u:Steve`) and legacy CoreProtect format (`u.Steve`). Fallbacks to single-character prefixes or partial name matches if aliasing fails.
*   **User Filter**: Displays top 10 usernames + `#global` for auto-completion. Filters out `#global` if mixed with specific usernames. Supports comma-separated lists.
*   **Radius Filter**: Suggests common radii. Handles chunk radius syntax (e.g., `c4` = 4 chunks = 64 blocks) and regular block radius.
*   **Time Filter**: Suggests common time units (`s`, `m`, `h`, `d`, `w`, `mo` for months). Can parse time ranges (e.g., `1w-1d`) where the larger offset means further in the past.
*   **Item Filter**: Uses a built-once cache for item names, returning names without the `minecraft:` prefix for vanilla items. Supports namespace-specific filtering.
*   **Action Filter**: Differentiates block (interact, kill entity), container (drop/pickup), and session actions. Replaces underscores to match user inputs.

### Core Commands
*   **Apply/Cancel**: Executes (`ApplyCommand`) or abandons (`CancelCommand`) an active preview session. Apply clears the preview, undoes the history logic (restoring inverted states) on the server thread, and records the new operation.
*   **Rollback/Restore/Undo**: Always flushes pending database queues before reading history to ensure accuracy. Generates batch processing jobs. `Restore` equals positive confirming of breaks (setting to air) and places (restoring blocks). `Undo` strictly reverses previous rollbacks (and prevents undoing an undo).
*   **Lookup**: The `#count` flag skips visual logs and only returns the total count of matches.
*   **Near**: Automatically applies a default filter of radius 5 blocks and time 3 days (72 hours).
*   **Purge**: Requires admin permission and a minimal safety time threshold. Must include a time filter indicating how far back to delete.
*   **Status**: Outputs Mod ID, Server Info, Memory Info, Config, and DB Connection status.
*   **Teleport**: Safely teleports players to the exact center of a targeted block (`pos.getX() + 0.5`).

## Server Events (`TickEvents`, `BreakBlockEvent`, etc.)

*   **Tick Events**: Periodically sends "hello" packets to keep the database/service connections alive.
*   **Block Breaks**: Intercepts `BreakBlockEvent` to immediately log container contents before the container vanishes into the world.
*   **Block Placement**: Safely guarded by `BlockEventLock` to prevent race conditions when asynchronously logging player placements.
*   **Container Activity**: `ContainerTransactionManager` constantly checks for real-time tracking on open containers. It detects "automated activity" (like items piped in via hoppers). If automated activity is detected, it **skips player logging** for that tick to prevent false attribution, but always updates the last known state to prevent double-logging next tick.

## Models and Entities

*   `SimpleItemStack`: Used for lightweight caching. Only compares item types and NBT; it actively **does not check count** for `equals()` comparisons, ensuring different stack sizes of the same item are treated identically. Evaluates legacy uncompressed data for backwards compatibility.
*   `TimeUnit`: A robust time parser that evaluates character by character, capturing special cases like `mo` for months before matching `m` for minutes.

## Utils and Helpers

*   `CompressionUtils`: Uses heuristic logic, only compressing data (like NBT) if it exceeds a certain byte size threshold. If the compressed result turns out to be larger than the raw data (which happens with tiny strings), or decompression fails, it transparently falls back to the uncompressed raw data.
*   `Theme`: Maintains a strict 7-color palette (Aqua, White, Red, Green, Gold, Gray, Blue). Provides Kyori component helpers, Gradient support, and Minecraft Component conversions for chat messages.
