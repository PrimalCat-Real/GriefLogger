package com.daqem.grieflogger.config;

import com.daqem.grieflogger.GriefLogger;
import com.mojang.text2speech.OperatingSystem;
import com.supermartijn642.configlib.api.ConfigBuilders;
import com.supermartijn642.configlib.api.IConfigBuilder;

import java.util.function.Supplier;

public class GriefLoggerConfig {

    public static void init() {
    }

    public static final Supplier<Boolean> useMysql;
    public static final Supplier<String> mysqlHost;
    public static final Supplier<Integer> mysqlPort;
    public static final Supplier<String> mysqlDatabase;
    public static final Supplier<String> mysqlUsername;
    public static final Supplier<String> mysqlPassword;
    public static final Supplier<Integer> mysqlTimeout;
    public static final Supplier<Boolean> useIndexes;

    public static final Supplier<Integer> maxPageSize;

    public static final Supplier<Integer> rollbackBatchSize;
    public static final Supplier<Integer> purgeMinDays;

    public static final Supplier<Boolean> serverSideOnlyMode;

    public static final Supplier<Integer> queueFrequency;
    public static final Supplier<Integer> helloFrequency;

    public static final Supplier<Boolean> logWaterFlow;
    public static final Supplier<Boolean> logLavaFlow;
    public static final Supplier<Boolean> logLeafDecay;
    public static final Supplier<Boolean> logFireSpread;
    public static final Supplier<Boolean> logVineGrowth;
    public static final Supplier<Boolean> logExplosions;
    public static final Supplier<Boolean> logPistons;
    public static final Supplier<Boolean> logGravity;
    public static final Supplier<Boolean> logEntityChanges;

    // Home detection
    public static final Supplier<Boolean> homesEnabled;
    public static final Supplier<Integer> homeTrackInterval;
    public static final Supplier<Integer> homeClusterInterval;
    public static final Supplier<Integer> homeMinClusterPoints;
    public static final Supplier<Integer> homeStayChecks;
    public static final Supplier<Integer> homeBackupCooldown;
    public static final Supplier<Integer> homeMaxBackupsPerHome;
    public static final Supplier<Integer> homeQuitDelay;

    static {
        IConfigBuilder config = ConfigBuilders.newTomlConfig(GriefLogger.MOD_ID, GriefLogger.MOD_ID, true);
        config.push("database");
        useMysql = config.comment("Whether to use MySQL or SQLite").onlyOnServer().define("useMysql", false);
        mysqlHost = config.comment("MySQL host").onlyOnServer().define("mysqlHost", "localhost", 1, 255);
        mysqlPort = config.comment("MySQL port").onlyOnServer().define("mysqlPort", 3306, 1, 65535);
        mysqlDatabase = config.comment("MySQL database").onlyOnServer().define("mysqlDatabase", "database", 1, 255);
        mysqlUsername = config.comment("MySQL username").onlyOnServer().define("mysqlUsername", "username", 1, 255);
        mysqlPassword = config.comment("MySQL password").onlyOnServer().define("mysqlPassword", "password", 1, 255);
        mysqlTimeout = config.comment("MySQL timeout").onlyOnServer().define("mysqlTimeout", 5000, 1, 60000);
        useIndexes = config.comment("Whether to use indexes (improves inspect/lookup speed)").onlyOnServer().define("useIndexes", true);
        config.pop();

        config.push("general");
        maxPageSize = config.comment("Maximum page size").onlyOnServer().define("maxPageSize", 10, 1, 100);
        config.pop();

        config.push("rollback");
        rollbackBatchSize = config.comment("Number of blocks to process per tick during rollback (lower = less lag, slower rollback)").onlyOnServer().define("rollbackBatchSize", 500, 50, 5000);
        purgeMinDays = config.comment("Minimum days for purge command (safety limit)").onlyOnServer().define("purgeMinDays", 7, 1, 365);
        config.pop();

        config.push("server");
        serverSideOnlyMode = config.comment("Whether to run the mod in server side only mode").onlyOnServer().define("serverSideOnlyMode", true);
        config.pop();

        config.push("queue");
        queueFrequency = config.comment("The frequency at which the database queue is executed (every 'x' ticks)").onlyOnServer().define("queueFrequency", 20, 1, 100);
        config.pop();

        config.push("hello");
        helloFrequency = config.comment("The frequency at which the hello packet is sent to the server (every 'x' ticks)").onlyOnServer().define("helloFrequency", 600, 1, 1000);
        config.pop();

        config.push("logging");
        logWaterFlow = config.comment("Log water flow events (attributed to #water)").onlyOnServer().define("waterFlow", true);
        logLavaFlow = config.comment("Log lava flow events (attributed to #lava)").onlyOnServer().define("lavaFlow", true);
        logLeafDecay = config.comment("Log leaf decay events (attributed to #decay)").onlyOnServer().define("leafDecay", true);
        logFireSpread = config.comment("Log fire spread events (attributed to #fire)").onlyOnServer().define("fireSpread", true);
        logVineGrowth = config.comment("Log vine/plant growth events (attributed to #vine). Can generate a lot of data!").onlyOnServer().define("vineGrowth", false);
        logExplosions = config.comment("Log explosion events (TNT, creeper) (attributed to #explosion)").onlyOnServer().define("explosions", true);
        logPistons = config.comment("Log piston push/pull events (attributed to #piston)").onlyOnServer().define("pistons", true);
        logGravity = config.comment("Log falling block events like sand/gravel (attributed to #gravity)").onlyOnServer().define("gravity", true);
        logEntityChanges = config.comment("Log entity-caused block changes (enderman, etc.)").onlyOnServer().define("entityChanges", true);
        config.pop();

        config.push("homes");
        homesEnabled = config.comment("Enable automatic home detection and chunk backup").onlyOnServer().define("enabled", true);
        homeTrackInterval = config.comment("How often to sample player positions (in ticks, 1200 = 1 minute)").onlyOnServer().define("trackInterval", 1200, 200, 6000);
        homeClusterInterval = config.comment("How often to cluster hot points and detect homes (in ticks, 12000 = 10 minutes)").onlyOnServer().define("clusterInterval", 12000, 2400, 72000);
        homeMinClusterPoints = config.comment("Minimum points in a cluster to be considered a home candidate").onlyOnServer().define("minClusterPoints", 5, 2, 50);
        homeStayChecks = config.comment("Consecutive checks player must be at home to trigger backup").onlyOnServer().define("stayChecks", 5, 1, 30);
        homeBackupCooldown = config.comment("Minimum seconds between auto-backups of the same home").onlyOnServer().define("backupCooldown", 21600, 600, 604800);
        homeMaxBackupsPerHome = config.comment("Max backups per home before oldest gets deleted").onlyOnServer().define("maxBackupsPerHome", 90, 5, 1000);
        homeQuitDelay = config.comment("Seconds to wait after player quits before processing their data").onlyOnServer().define("quitDelay", 300, 30, 1800);
        config.pop();

        config.build();
    }
}
