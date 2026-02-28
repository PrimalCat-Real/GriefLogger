package com.daqem.grieflogger;

import com.daqem.grieflogger.cache.CacheHandler;
import com.daqem.grieflogger.config.GriefLoggerConfig;
import com.daqem.grieflogger.database.Database;
import com.daqem.grieflogger.database.consumer.Consumer;
import com.daqem.grieflogger.database.service.*;
import com.daqem.grieflogger.event.*;
import com.daqem.grieflogger.event.block.BlockEvents;
import com.daqem.grieflogger.event.item.ItemEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

public class GriefLogger {

    public static final UUID SYSTEM_UUID = new UUID(0L, 0L);
    public static final String SYSTEM_USERNAME = "[System]";
    public static final String MOD_ID = "grieflogger";
    public static final Logger LOGGER = LogUtils.getLogger();


    public static final String PHANTOM_WATER = "#water";
    public static final String PHANTOM_LAVA = "#lava";
    public static final String PHANTOM_FIRE = "#fire";
    public static final String PHANTOM_DECAY = "#decay";          
    public static final String PHANTOM_VINE = "#vine";            
    public static final String PHANTOM_EXPLOSION = "#explosion";  
    public static final String PHANTOM_PISTON = "#piston";
    public static final String PHANTOM_ENDERMAN = "#enderman";
    public static final String PHANTOM_GRAVITY = "#gravity";      
    public static final String PHANTOM_HOPPER = "#hopper";        
    public static final String PHANTOM_CHUTE = "#chute";          
    public static final String PHANTOM_UNKNOWN = "#unknown";

    private static final ThreadLocal<Boolean> ROLLBACK_ACTIVE = ThreadLocal.withInitial(() -> false);

    public static void setRollbackActive(boolean active) {
        ROLLBACK_ACTIVE.set(active);
    }

    public static boolean isRollbackActive() {
        return ROLLBACK_ACTIVE.get();
    }

    /**
     * Database type selector:
     * 0 = SQLite (default)
     * 1 = MySQL
     * 2 = PostgreSQL
     * TODO: Move to config file
     */
    public static int DATABASE_TYPE = 0;

    private static Database DATABASE;

    public static void init() {
        initConfigs();
        boolean databaseReady = prepareDatabase();
        if (!databaseReady) {
            return;
        }
        startBackgroundThreads();
        registerEvents();
    }

    private static void startBackgroundThreads() {
        Consumer.start(DATABASE);

        CacheHandler.start();

        LOGGER.info("Background threads started");
    }

    /**
     * Shutdown GriefLogger and cleanup resources.
     * Should be called when server is stopping.
     */
    public static void shutdown() {
        LOGGER.info("Shutting down GriefLogger...");

        Consumer.stop();

        CacheHandler.stop();

        if (DATABASE != null && DATABASE_TYPE == 0) {
            DATABASE.performWalCheckpoint();
        }

        LOGGER.info("GriefLogger shutdown complete");
    }

    private static void initConfigs() {
        GriefLoggerConfig.init();
    }

    private static void registerEvents() {
        BlockEvents.registerEvents();
        TickEvents.registerEvents();
        EntityEvents.registerEvents();

        PlayerJoinEvent.registerEvent();
        PlayerQuitEvent.registerEvent();
        LevelLoadEvent.registerEvent();
        RegisterCommandEvent.registerEvent();

        ChatEvent.registerEvent();
        CommandEvent.registerEvent();
    }

    private static boolean prepareDatabase() {
        LOGGER.info("Preparing GriefLogger database...");
        long start = System.currentTimeMillis();

        DATABASE_TYPE = GriefLoggerConfig.useMysql.get() ? 1 : 0;

        try {
            DATABASE = new Database();
            boolean connected = DATABASE.createConnection();

            if (!connected) {
                LOGGER.error("Failed to connect to database, disabling GriefLogger...");
                return false;
            }


        } catch (Exception e) {
            LOGGER.error("Failed to connect to database, disabling GriefLogger...", e);
            return false;
        }

        Services.MATERIAL.createTable();
        Services.USER.createTable();

        Services.USERNAME.createTable();
        Services.LEVEL.createTable();
        Services.ENTITY.createTable();
        Services.BLOCK.createTable();
        Services.CONTAINER.createTable();
        Services.SESSION.createTable();
        Services.CHAT.createTable();
        Services.COMMAND.createTable();
        Services.ITEM.createTable();
        Services.ROLLBACK.createTable();
        Services.CHUNK_BACKUP.createTable();

        if (GriefLoggerConfig.useIndexes.get()) {
            Services.BLOCK.createIndexes();
            Services.CHAT.createIndexes();
            Services.COMMAND.createIndexes();
            Services.CONTAINER.createIndexes();
            Services.ITEM.createIndexes();
            Services.SESSION.createIndexes();
            Services.ROLLBACK.createIndexes();
        }
        try {
            Services.USER.insertOrUpdateName(SYSTEM_UUID, SYSTEM_USERNAME);
        } catch (Exception e) {
            LOGGER.error("Failed to insert system user", e);
        }

        long end = System.currentTimeMillis();
        LOGGER.info("Database prepared in {}ms.", end - start);
        return true;
    }

    public static Database getDatabase() {
        return DATABASE;
    }

    public static MutableComponent translate(String str) {
        MutableComponent component = translate(str, TranslatableContents.NO_ARGS);
        if (GriefLoggerConfig.serverSideOnlyMode.get()) {
            component = Component.literal(component.getString()).withStyle(component.getStyle());
        }
        return component;
    }

    public static MutableComponent translate(String str, Object... args) {
        MutableComponent component = Component.translatable(MOD_ID + "." + str, args);
        if (GriefLoggerConfig.serverSideOnlyMode.get()) {
            component = Component.literal(component.getString()).withStyle(component.getStyle());
        }
        return component;
    }

    public static MutableComponent literal(String str) {
        return Component.literal(str);
    }

    public static MutableComponent themedTranslate(String str) {
        MutableComponent component = themedTranslate(str, TranslatableContents.NO_ARGS);
        if (GriefLoggerConfig.serverSideOnlyMode.get()) {
            component = Component.literal(component.getString()).withStyle(component.getStyle());
        }
        return component;
    }

    public static MutableComponent themedTranslate(String str, Object... args) {
        MutableComponent component = Component.translatable(MOD_ID + "." + str, args).withStyle(getTheme());
        if (GriefLoggerConfig.serverSideOnlyMode.get()) {
            component = Component.literal(component.getString()).withStyle(component.getStyle());
        }
        return component;
    }

    public static MutableComponent themedLiteral(String str) {
        MutableComponent component = Component.literal(str).withStyle(getTheme());
        if (GriefLoggerConfig.serverSideOnlyMode.get()) {
            component = Component.literal(component.getString()).withStyle(component.getStyle());
        }
        return component;
    }

    public static Component getName() {
        Component component = translate("name").withStyle(getTheme());
        if (GriefLoggerConfig.serverSideOnlyMode.get()) {
            component = Component.literal(component.getString()).withStyle(component.getStyle());
        }
        return component;
    }

    public static UUID getEntityUUID(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer.getUUID();
        }
        return SYSTEM_UUID;
    }

    public static Style getTheme() {
        return Style.EMPTY.withColor(0xFCBA03);
    }

    public static ResourceLocation getId(String id) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }
}
