# Rollback and Block Assessment Documentation

This document covers how GriefLogger groups block events, attributes natural events, and processes rollback requests.

## Phantom Users & Natural Attribution
To avoid ignoring environmental events while still attributing them, GriefLogger uses **Phantom Users**.
*   **Concept**: Instead of a player UUID, natural events are logged under pseudo-names like `#water`, `#fire`, `#explosion`, `#leaf_decay`, `#vine_growth`, `#gravity` (sand/gravel), and `#chute`/`#hopper` (for items).
*   **Format**: Can be simple (`#hopper`) or include coordinates for long contraptions (`#chute@x,y,z` mapped to `chute (x, y, z)`).
*   **Beneficial**: This enables the server to rollback structural damage caused by water, fire, or TNT by rolling back the respective phantom user.

## Event Coalescing and Filtering (`BlockEventCoalescer`, `BlockChangeFilter`)
*   **Goal**: De-duplicate spammy block updates (like water flowing continuously) and filter system events.
*   **Logic**:
    *   If a block changes to its exact same state, the event is skipped.
    *   Crop growth state changes are skipped (they are block state changes, not distinct block changes).
    *   Events check player proximity. Events near players are prioritized; if a block transitions from a system event to a player event, the phantom user is overridden by the player.
    *   The `SpreadCache` deduplicates natural events like fire spread or water flow to not flood the database. If a player acts, the spread cache for that block is invalidated.
*   **Block Categories**: The filter has distinct parsing for vines, kelp, grass, mushrooms, amethyst, sculk, moss, and dripleaf to attribute their changes correctly. Ice/snow melting is attributed to `#water`.

## Rollback Execution

### Pre-processing and Preview (`PreviewSession.java`, `RollbackProgress.java`)
*   When a rollback is requested, the system can enter a Preview Mode holding the session for up to **5 minutes**.
*   `RollbackProgress` cancels any currently active operation for that player and outputs a visual progress bar (e.g., `[████████░░░░] 45%`).

### Batch Processing (`BatchProcessor.java`)
*   To avoid server lag, extensive rollbacks are split into periodic batches.
*   The processor counts blocks and container items, processes them in chunks, updates the action bar progress, and schedules the next batch for the subsequent server tick until complete.

### Core Logic (`RollbackAction`, `ContainerRollbackProcessor`)
*   **Database Tracking**: Every block changes during a rollback is logged as a reversed action (e.g., Block removed (`block -> air`) must be restored (`air -> block`)).
*   **Containers**:
    *   If a container block no longer exists at a position, the items intended for it are dropped on the ground.
    *   When restoring a container, the processor first attempts to stack items with identical existing items, and only then looks for empty slots. If the container gets full, remainder items are dropped.
    *   Transactions track if items were explicitly added, removed, or dropped because the container was full.
