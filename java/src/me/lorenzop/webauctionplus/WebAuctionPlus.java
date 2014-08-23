package me.lorenzop.webauctionplus;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import me.lorenzop.webauctionplus.dao.waStats;
import me.lorenzop.webauctionplus.listeners.WebAuctionBlockListener;
import me.lorenzop.webauctionplus.listeners.WebAuctionCommands;
import me.lorenzop.webauctionplus.listeners.WebAuctionPlayerListener;
import me.lorenzop.webauctionplus.listeners.failPlayerListener;
import me.lorenzop.webauctionplus.mysql.DataQueries;
import me.lorenzop.webauctionplus.mysql.MySQLTables;
import me.lorenzop.webauctionplus.mysql.MySQLUpdate;
import me.lorenzop.webauctionplus.tasks.AnnouncerTask;
import me.lorenzop.webauctionplus.tasks.PlayerAlertTask;
import me.lorenzop.webauctionplus.tasks.RecentSignTask;
import me.lorenzop.webauctionplus.tasks.ShoutSignTask;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WebAuctionPlus extends JavaPlugin {

    private static volatile WebAuctionPlus  instance                   = null;
    private static volatile boolean         isOk                       = false;

    private static final String             loggerPrefix               = "[WebAuction+] ";
    public static final String              chatPrefix                 = ChatColor.DARK_GREEN + "[" + ChatColor.WHITE
                                                                               + "WebAuction+" + ChatColor.DARK_GREEN
                                                                               + "] ";

    private static volatile Plugins3rdParty plugins3rd                 = null;

    public static pxnMetrics                metrics;
    public static waStats                   stats;

    // plugin version
    public static String                    currentVersion             = null;
    public static String                    newVersion                 = null;
    public static boolean                   newVersionAvailable        = false;

    // config
    public FileConfiguration                config                     = null;
    public static waSettings                settings                   = null;

    // language
    public static volatile Language         Lang;

    public static volatile DataQueries      dataQueries                = null;
    public volatile WebAuctionCommands      WebAuctionCommandsListener = new WebAuctionCommands(this);

    public Map<String, Long>                lastSignUse                = new HashMap<String, Long>();
    public Map<Location, Integer>           recentSigns                = new HashMap<Location, Integer>();
    public Map<Location, Integer>           shoutSigns                 = new HashMap<Location, Integer>();

    public int                              signDelay                  = 0;
    public int                              numberOfRecentLink         = 0;

    // use recent signs
    private static boolean                  useOriginalRecent          = false;
    // // sign link
    // private static boolean useSignLink = false;
    // tim the enchanter
    private static boolean                  timEnabled                 = false;
    // globally announce new auctions (vs using shout signs)
    private static boolean                  announceGlobal             = false;

    // JSON Server
    // public waJSONServer jsonServer;

    // recent sign task
    public static RecentSignTask            recentSignTask             = null;

    // announcer
    public AnnouncerTask                    waAnnouncerTask            = null;
    public boolean                          announcerEnabled           = false;

    public WebAuctionPlus() {
    }

    @Override
    public void onEnable() {
        if (WebAuctionPlus.isOk) {
            getServer().getConsoleSender().sendMessage(ChatColor.RED + "********************************************");
            getServer().getConsoleSender().sendMessage(ChatColor.RED + "*** WebAuctionPlus is already running!!! ***");
            getServer().getConsoleSender().sendMessage(ChatColor.RED + "********************************************");
            return;
        }
        WebAuctionPlus.instance = this;
        WebAuctionPlus.isOk = false;
        WebAuctionPlus.failMsg = null;
        WebAuctionPlus.currentVersion = getDescription().getVersion();

        // 3rd party plugins
        if (WebAuctionPlus.plugins3rd == null) {
            WebAuctionPlus.plugins3rd = new Plugins3rdParty(WebAuctionPlus.getLog());
        }

        // Command listener
        getCommand("wa").setExecutor(WebAuctionCommandsListener);

        // load config.yml
        if (!onLoadConfig()) {
            return;
        }

        // load more services
        onLoadMetrics();
        checkUpdateAvailable();

        final PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new WebAuctionPlayerListener(this), this);
        pm.registerEvents(new WebAuctionBlockListener(this), this);
        WebAuctionPlus.isOk = true;
    }

    @Override
    public void onDisable() {
        WebAuctionPlus.isOk = false;
        failPlayerListener.stop();
        // unregister listeners
        HandlerList.unregisterAll(this);
        // stop schedulers
        try {
            getServer().getScheduler().cancelTasks(this);
        } catch (final Exception ignore) {}
        if (waAnnouncerTask != null) {
            waAnnouncerTask.clearMessages();
        }
        if (shoutSigns != null) {
            shoutSigns.clear();
        }
        if (recentSigns != null) {
            recentSigns.clear();
        }
        // close inventories
        try {
            WebInventory.ForceCloseAll();
        } catch (final Exception ignore) {}
        // close mysql connection
        try {
            if (WebAuctionPlus.dataQueries != null) {
                WebAuctionPlus.dataQueries.forceCloseConnections();
            }
        } catch (final Exception ignore) {}
        // close config
        try {
            config = null;
        } catch (final Exception ignore) {}
        WebAuctionPlus.settings = null;
        WebAuctionPlus.Lang = null;
        WebAuctionPlus.getLog().info("Disabled, bye for now :-)");
    }

    public void onReload() {
        WebAuctionPlus.failMsg = null;
        onDisable();
        // load config.yml
        if (!onLoadConfig()) {
            return;
        }
        WebAuctionPlus.isOk = true;
    }

    public static WebAuctionPlus getPlugin() {
        return WebAuctionPlus.instance;
    }

    public static boolean isOk() {
        return WebAuctionPlus.isOk;
    }

    // public static boolean isDebug() {return isDebug;}

    /**
     * 3rd party plugins
     */
    public static Plugins3rdParty getPlugins() {
        return WebAuctionPlus.plugins3rd;
    }

    /**
     * Logger bootstrap
     */
    private static volatile logBoots bLog    = null;
    private static final Object      logLock = new Object();

    public static logBoots getLog() {
        if (WebAuctionPlus.bLog == null) {
            synchronized (WebAuctionPlus.logLock) {
                if (WebAuctionPlus.bLog == null) {
                    WebAuctionPlus.bLog = new logBoots(WebAuctionPlus.getPlugin(), WebAuctionPlus.loggerPrefix);
                }
            }
        }
        return WebAuctionPlus.bLog;
    }

    private static volatile String failMsg = null;

    public static void fail(String msg) {
        if (msg != null && !msg.isEmpty()) {
            WebAuctionPlus.getLog().severe(msg);
            if (WebAuctionPlus.failMsg == null || WebAuctionPlus.failMsg.isEmpty()) {
                WebAuctionPlus.failMsg = msg;
            } else {
                WebAuctionPlus.failMsg += "|" + msg;
            }
        }
        {
            final JavaPlugin plugin = WebAuctionPlus.getPlugin();
            plugin.onDisable();
            failPlayerListener.start(plugin);
        }
    }

    public static String getFailMsg() {
        if (WebAuctionPlus.failMsg == null || WebAuctionPlus.failMsg.isEmpty()) {
            return null;
        }
        return WebAuctionPlus.failMsg;
    }

    public boolean onLoadConfig() {
        // init configs
        if (config != null) {
            config = null;
        }
        config = getConfig();
        configDefaults();

        // connect MySQL
        if (WebAuctionPlus.dataQueries == null) {
            if (!ConnectDB()) {
                return false;
            }
        }

        // load stats class
        if (WebAuctionPlus.stats == null) {
            WebAuctionPlus.stats = new waStats();
        }

        // load settings from db
        if (WebAuctionPlus.settings != null) {
            WebAuctionPlus.settings = null;
        }
        WebAuctionPlus.settings = new waSettings(this);
        WebAuctionPlus.settings.LoadSettings();
        if (!WebAuctionPlus.settings.isOk()) {
            WebAuctionPlus.fail("Failed to load settings from database.");
            return false;
        }

        // update the version in db
        if (!WebAuctionPlus.currentVersion.equals(WebAuctionPlus.settings.getString("Version"))) {
            final String oldVersion = WebAuctionPlus.settings.getString("Version");
            // update database
            MySQLUpdate.doUpdate(oldVersion);
            // update version number
            WebAuctionPlus.settings.setString("Version", WebAuctionPlus.currentVersion);
            WebAuctionPlus.getLog().info("Updated version from " + oldVersion + " to " + WebAuctionPlus.currentVersion);
        }

        // load language file
        if (WebAuctionPlus.Lang != null) {
            WebAuctionPlus.Lang = null;
        }
        WebAuctionPlus.Lang = new Language(this);
        WebAuctionPlus.Lang.loadLanguage(WebAuctionPlus.settings.getString("Language"));
        if (!WebAuctionPlus.Lang.isOk()) {
            WebAuctionPlus.fail("Failed to load language file.");
            return false;
        }

        try {
            if (config.getBoolean("Development.Debug")) {
                WebAuctionPlus.getLog().setDebug();
            }
            // addComment("debug_mode",
            // Arrays.asList("# This is where you enable debug mode"))
            signDelay = config.getInt("Misc.SignClickDelay");
            WebAuctionPlus.timEnabled = config.getBoolean("Misc.UnsafeEnchantments");
            WebAuctionPlus.announceGlobal = config.getBoolean("Misc.AnnounceGlobally");
            // numberOfRecentLink = config.getInt
            // ("SignLink.NumberOfLatestAuctionsToTrack");
            // useSignLink = config.getBoolean("SignLink.Enabled");
            // if(useSignLink && !plugins3rd.isLoaded_SignLink()) {
            // getLog().warning("SignLink is found but plugin is not loaded!");
            // useSignLink = false;
            // }

            // scheduled tasks
            final BukkitScheduler scheduler = Bukkit.getScheduler();
            final boolean UseMultithreads = config.getBoolean("Development.UseMultithreads");

            // announcer
            announcerEnabled = config.getBoolean("Announcer.Enabled");
            long announcerMinutes = 20 * 60 * config.getLong("Tasks.AnnouncerMinutes");
            if (announcerEnabled) {
                waAnnouncerTask = new AnnouncerTask(this);
            }
            if (announcerEnabled && announcerMinutes > 0) {
                if (announcerMinutes < 6000) {
                    announcerMinutes = 6000; // minimum 5 minutes
                }
                waAnnouncerTask.chatPrefix = config.getString("Announcer.Prefix");
                waAnnouncerTask.announceRandom = config.getBoolean("Announcer.Random");
                waAnnouncerTask.addMessages(config.getStringList("Announcements"));
                scheduler.runTaskTimerAsynchronously(this, waAnnouncerTask, (announcerMinutes / 2), announcerMinutes);
                WebAuctionPlus.getLog().info("Enabled Task: Announcer (always multi-threaded)");
            }

            long saleAlertSeconds = 20 * config.getLong("Tasks.SaleAlertSeconds");
            final long shoutSignUpdateSeconds = 20 * config.getLong("Tasks.ShoutSignUpdateSeconds");
            final long recentSignUpdateSeconds = 20 * config.getLong("Tasks.RecentSignUpdateSeconds");
            WebAuctionPlus.useOriginalRecent = config.getBoolean("Misc.UseOriginalRecentSigns");

            // Build shoutSigns map
            if (shoutSignUpdateSeconds > 0) {
                shoutSigns.putAll(WebAuctionPlus.dataQueries.getShoutSignLocations());
            }
            // Build recentSigns map
            if (recentSignUpdateSeconds > 0) {
                recentSigns.putAll(WebAuctionPlus.dataQueries.getRecentSignLocations());
            }

            // report sales to players (always multi-threaded)
            if (saleAlertSeconds > 0) {
                if (saleAlertSeconds < 3 * 20) {
                    saleAlertSeconds = 3 * 20;
                }
                scheduler.runTaskTimerAsynchronously(this, new PlayerAlertTask(), saleAlertSeconds, saleAlertSeconds);
                WebAuctionPlus.getLog().info("Enabled Task: Sale Alert (always multi-threaded)");
            }
            // shout sign task
            if (shoutSignUpdateSeconds > 0) {
                if (UseMultithreads) {
                    scheduler.runTaskTimerAsynchronously(this, new ShoutSignTask(this), shoutSignUpdateSeconds,
                            shoutSignUpdateSeconds);
                } else {
                    scheduler.scheduleSyncRepeatingTask(this, new ShoutSignTask(this), shoutSignUpdateSeconds,
                            shoutSignUpdateSeconds);
                }
                WebAuctionPlus.getLog().info(
                        "Enabled Task: Shout Sign (using " + (UseMultithreads ? "multiple threads" : "single thread")
                                + ")");
            }
            // update recent signs
            if (recentSignUpdateSeconds > 0 && WebAuctionPlus.useOriginalRecent) {
                WebAuctionPlus.recentSignTask = new RecentSignTask(this);
                if (UseMultithreads) {
                    scheduler.runTaskTimerAsynchronously(this, WebAuctionPlus.recentSignTask, 5 * 20,
                            recentSignUpdateSeconds);
                } else {
                    scheduler.scheduleSyncRepeatingTask(this, WebAuctionPlus.recentSignTask, 5 * 20,
                            recentSignUpdateSeconds);
                }
                WebAuctionPlus.getLog().info(
                        "Enabled Task: Recent Sign (using " + (UseMultithreads ? "multiple threads" : "single thread")
                                + ")");
            }
        } catch (final Exception e) {
            e.printStackTrace();
            WebAuctionPlus.fail("Failed loading the config.");
            return false;
        }
        return true;
    }

    public void onSaveConfig() {
    }

    // Init database
    public synchronized boolean ConnectDB() {
        if (config.getString("MySQL.Password").equals("password123")) {
            WebAuctionPlus.fail("Please set the database connection info in the config.");
            return false;
        }
        WebAuctionPlus.getLog().info("MySQL Initializing.");
        if (WebAuctionPlus.dataQueries != null) {
            WebAuctionPlus.fail("Database connection already made?");
            return false;
        }
        try {
            int port = config.getInt("MySQL.Port");
            if (port < 1) {
                port = Integer.valueOf(config.getString("MySQL.Port"));
            }
            if (port < 1) {
                port = 3306;
            }
            WebAuctionPlus.dataQueries = new DataQueries(config.getString("MySQL.Host"), port,
                    config.getString("MySQL.Username"), config.getString("MySQL.Password"),
                    config.getString("MySQL.Database"), config.getString("MySQL.TablePrefix"));
            WebAuctionPlus.dataQueries.setConnPoolSizeWarn(config.getInt("MySQL.ConnectionPoolSizeWarn"));
            WebAuctionPlus.dataQueries.setConnPoolSizeHard(config.getInt("MySQL.ConnectionPoolSizeHard"));
            // create/update tables
            MySQLTables dbTables = new MySQLTables(this);
            if (!dbTables.isOk()) {
                WebAuctionPlus.fail("Error loading db updater class.");
                return false;
            }
            dbTables = null;
            // } catch (SQLException e) {
        } catch (final Exception e) {
            WebAuctionPlus.fail("Unable to connect to MySQL database.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void configDefaults() {
        config.addDefault("MySQL.Host", "localhost");
        config.addDefault("MySQL.Username", "minecraft");
        config.addDefault("MySQL.Password", "password123");
        config.addDefault("MySQL.Port", 3306);
        config.addDefault("MySQL.Database", "minecraft");
        config.addDefault("MySQL.TablePrefix", "WA_");
        config.addDefault("MySQL.ConnectionPoolSizeWarn", 5);
        config.addDefault("MySQL.ConnectionPoolSizeHard", 10);
        config.addDefault("Misc.ReportSales", true);
        config.addDefault("Misc.UseOriginalRecentSigns", true);
        config.addDefault("Misc.SignClickDelay", 500);
        config.addDefault("Misc.UnsafeEnchantments", false);
        config.addDefault("Misc.AnnounceGlobally", true);
        config.addDefault("Tasks.SaleAlertSeconds", 20L);
        config.addDefault("Tasks.ShoutSignUpdateSeconds", 20L);
        config.addDefault("Tasks.RecentSignUpdateSeconds", 60L);
        config.addDefault("Tasks.AnnouncerMinutes", 60L);
        // config.addDefault("SignLink.Enabled", false);
        // config.addDefault("SignLink.NumberOfLatestAuctionsToTrack", 10);
        config.addDefault("Development.UseMultithreads", false);
        config.addDefault("Development.Debug", false);
        config.addDefault("Announcer.Enabled", false);
        config.addDefault("Announcer.Prefix", "&c[Info] ");
        config.addDefault("Announcer.Random", false);
        config.addDefault("Announcements", new String[] {"This server is running WebAuctionPlus!"});
        config.options().copyDefaults(true);
        saveConfig();
    }

    public static boolean useOriginalRecent() {
        return WebAuctionPlus.useOriginalRecent;
    }

    public static boolean useSignLink() {
        // return useSignLink;
        return false;
    }

    public static boolean timEnabled() {
        return WebAuctionPlus.timEnabled;
    }

    public static boolean announceGlobal() {
        return WebAuctionPlus.announceGlobal;
    }

    @SuppressWarnings("deprecation")
    public static synchronized void doUpdateInventory(Player p) {
        p.updateInventory();
    }

    public static long getCurrentMilli() {
        return System.currentTimeMillis();
    }

    // format chat colors
    public static String ReplaceColors(String text) {
        return text.replaceAll("&([0-9a-fA-F])", "\247$1");
    }

    // add strings with delimiter
    public static String addStringSet(String baseString, String addThis, String Delim) {
        if (addThis.isEmpty()) {
            return baseString;
        }
        if (baseString.isEmpty()) {
            return addThis;
        }
        return baseString + Delim + addThis;
    }

    // public static String format(double amount) {
    // DecimalFormat formatter = new DecimalFormat("#,##0.00");
    // String formatted = formatter.format(amount);
    // if (formatted.endsWith("."))
    // formatted = formatted.substring(0, formatted.length() - 1);
    // return Common.formatted(formatted, Constants.Nodes.Major.getStringList(),
    // Constants.Nodes.Minor.getStringList());
    // }

    // work with doubles
    public static String FormatPrice(double value) {
        return WebAuctionPlus.settings.getString("Currency Prefix") + WebAuctionPlus.FormatDouble(value)
                + WebAuctionPlus.settings.getString("Currency Postfix");
    }

    public static String FormatDouble(double value) {
        final DecimalFormat decim = new DecimalFormat("##,###,##0.00");
        return decim.format(value);
    }

    public static double ParseDouble(String value) {
        return Double.parseDouble(value.replaceAll("[^0-9.]+", ""));
    }

    public static double RoundDouble(double value, int precision, int roundingMode) {
        final BigDecimal bd = new BigDecimal(value);
        final BigDecimal rounded = bd.setScale(precision, roundingMode);
        return rounded.doubleValue();
    }

    public static int getNewRandom(int oldNumber, int maxNumber) {
        if (maxNumber == 0) {
            return maxNumber;
        }
        if (maxNumber == 1) {
            return 1 - oldNumber;
        }
        final Random randomGen = new Random();
        int newNumber = 0;
        while (true) {
            newNumber = randomGen.nextInt(maxNumber + 1);
            if (newNumber != oldNumber) {
                return newNumber;
            }
        }
    }

    // min/max value
    public static int MinMax(int value, int min, int max) {
        if (value < min) {
            value = min;
        }
        if (value > max) {
            value = max;
        }
        return value;
    }

    public static long MinMax(long value, long min, long max) {
        if (value < min) {
            value = min;
        }
        if (value > max) {
            value = max;
        }
        return value;
    }

    public static double MinMax(double value, double min, double max) {
        if (value < min) {
            value = min;
        }
        if (value > max) {
            value = max;
        }
        return value;
    }

    // min/max by object
    public static boolean MinMax(Integer value, int min, int max) {
        boolean changed = false;
        if (value < min) {
            value = min;
            changed = true;
        }
        if (value > max) {
            value = max;
            changed = true;
        }
        return changed;
    }

    public static boolean MinMax(Long value, long min, long max) {
        boolean changed = false;
        if (value < min) {
            value = min;
            changed = true;
        }
        if (value > max) {
            value = max;
            changed = true;
        }
        return changed;
    }

    public static boolean MinMax(Double value, double min, double max) {
        boolean changed = false;
        if (value < min) {
            value = min;
            changed = true;
        }
        if (value > max) {
            value = max;
            changed = true;
        }
        return changed;
    }

    public static String MD5(String str) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (final NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        md.update(str.getBytes());
        final byte[] byteData = md.digest();
        final StringBuffer hexString = new StringBuffer();
        for (final byte element: byteData) {
            final String hex = Integer.toHexString(0xFF & element);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static void PrintProgress(double progress, int width) {
        String output = "[";
        int prog = (int) (progress * width);
        if (prog > width) {
            prog = width;
        }
        int i = 0;
        for (; i < prog; i++) {
            output += ".";
        }
        for (; i < width; i++) {
            output += " ";
        }
        WebAuctionPlus.getLog().info(output + "]");
    }

    public static void PrintProgress(int count, int total, int width) {
        try {
            // finished 100%
            if (count == total) {
                WebAuctionPlus.PrintProgress(1D, width);
            } else if (total < (width / 2)) {}
            // print only when adding a .
            else if (count % (total / width) == 0) {
                WebAuctionPlus.PrintProgress((double) count / (double) total, width);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public static void PrintProgress(int count, int total) {
        WebAuctionPlus.PrintProgress(count, total, 20);
    }

    // announce radius
    public static void BroadcastRadius(String msg, Location loc, int radius) {
        final Collection<? extends Player> playerList = Bukkit.getOnlinePlayers();
        final Double x = loc.getX();
        final Double z = loc.getZ();
        for (final Player player: playerList) {
            final Double playerX = player.getLocation().getX();
            final Double playerZ = player.getLocation().getZ();
            if ((playerX < x + radius) && (playerX > x - radius) && (playerZ < z + radius) && (playerZ > z - radius)) {
                player.sendMessage(WebAuctionPlus.chatPrefix + msg);
            }
        }
    }

    public void onLoadMetrics() {
        // usage stats
        try {
            WebAuctionPlus.metrics = new pxnMetrics(this);
            if (WebAuctionPlus.metrics.isOptOut()) {
                WebAuctionPlus.getLog().info("Plugin metrics are disabled, you bum");
                return;
            }
            // log.info(logPrefix+"Starting metrics");
            // Create graphs for total Buy Nows / Auctions
            final pxnMetrics.Graph lineGraph = WebAuctionPlus.metrics.createGraph("Stacks For Sale");
            final pxnMetrics.Graph pieGraph = WebAuctionPlus.metrics.createGraph("Selling Method");
            final pxnMetrics.Graph stockTrend = WebAuctionPlus.metrics.createGraph("Stock Trend");
            // buy now count
            final pxnMetrics.Plotter plotterBuyNows = new pxnMetrics.Plotter("Buy Nows") {
                @Override
                public int getValue() {
                    return WebAuctionPlus.stats.getTotalBuyNows();
                }
            };
            // auction count
            final pxnMetrics.Plotter plotterAuctions = new pxnMetrics.Plotter("Auctions") {
                @Override
                public int getValue() {
                    return WebAuctionPlus.stats.getTotalAuctions();
                }
            };
            // total selling
            lineGraph.addPlotter(plotterBuyNows);
            lineGraph.addPlotter(plotterAuctions);
            // selling ratio
            pieGraph.addPlotter(plotterBuyNows);
            pieGraph.addPlotter(plotterAuctions);
            // stock trends
            stockTrend.addPlotter(new pxnMetrics.Plotter("New") {
                @Override
                public int getValue() {
                    return WebAuctionPlus.stats.getNewAuctionsCount();
                }
            });
            stockTrend.addPlotter(new pxnMetrics.Plotter("Ended") {
                @Override
                public int getValue() {
                    return WebAuctionPlus.stats.getEndedAuctionsCount();
                }
            });
            // start reporting
            WebAuctionPlus.metrics.start();
        } catch (final IOException e) {
            // Failed to submit the stats :-(
            if (WebAuctionPlus.getLog().isDebug()) {
                e.printStackTrace();
            }
        }
    }

    // updateCheck() from MilkBowl's Vault
    // modified for my compareVersions() function
    private static String doUpdateCheck() throws Exception {
        final String pluginUrlString = "http://dev.bukkit.org/server-mods/webauctionplus/files.rss";
        try {
            final URL url = new URL(pluginUrlString);
            final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(url.openConnection().getInputStream());
            doc.getDocumentElement().normalize();
            final NodeList nodes = doc.getElementsByTagName("item");
            final Node firstNode = nodes.item(0);
            if (firstNode.getNodeType() == 1) {
                final Element firstElement = (Element) firstNode;
                final NodeList firstElementTagName = firstElement.getElementsByTagName("title");
                final Element firstNameElement = (Element) firstElementTagName.item(0);
                final NodeList firstNodes = firstNameElement.getChildNodes();
                final String version = firstNodes.item(0).getNodeValue();
                return version.substring(version.lastIndexOf(" ") + 1);
            }
        } catch (final Exception ignored) {}
        return null;
    }

    // compare versions
    public static String compareVersions(String oldVersion, String newVersion) {
        if (oldVersion == null || newVersion == null) {
            return null;
        }
        oldVersion = WebAuctionPlus.normalisedVersion(oldVersion);
        newVersion = WebAuctionPlus.normalisedVersion(newVersion);
        final int cmp = oldVersion.compareTo(newVersion);
        return cmp < 0 ? "<" : cmp > 0 ? ">" : "=";
    }

    public static String normalisedVersion(String version) {
        final String delim = ".";
        final int maxWidth = 5;
        final String[] split = Pattern.compile(delim, Pattern.LITERAL).split(version);
        String output = "";
        for (final String s: split) {
            output += String.format("%" + maxWidth + 's', s);
        }
        return output;
    }

    // check for an updated version
    private void checkUpdateAvailable() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    WebAuctionPlus.newVersion = WebAuctionPlus.doUpdateCheck();
                    final String cmp = WebAuctionPlus.compareVersions(WebAuctionPlus.currentVersion,
                            WebAuctionPlus.newVersion);
                    if (cmp == "<") {
                        WebAuctionPlus.newVersionAvailable = true;
                        final logBoots log = WebAuctionPlus.getLog();
                        log.warning("An update is available!");
                        log.warning("You're running " + WebAuctionPlus.currentVersion + " new version available is "
                                + WebAuctionPlus.newVersion);
                        log.warning("http://dev.bukkit.org/server-mods/webauctionplus");
                    }
                } catch (final Exception ignored) {}
            }
        }, 5 * 20, 14400 * 20); // run every 4 hours
    }

}
