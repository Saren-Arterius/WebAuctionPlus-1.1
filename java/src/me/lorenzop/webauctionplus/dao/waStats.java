package me.lorenzop.webauctionplus.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import me.lorenzop.webauctionplus.WebAuctionPlus;
import me.lorenzop.webauctionplus.mysql.MySQLConnPool;

public class waStats {

    // long cycle stats
    private volatile int  totalBuyNowCount        = 0;
    private volatile int  totalAuctionCount       = 0;
    private volatile int  maxAuctionId            = -1;
    private volatile int  newAuctionsCount        = 0;
    private volatile int  newAuctionsCount_lastId = 0;
    private volatile int  endAuctionsCount        = 0;
    private volatile int  endAuctionsCount_lastId = 0;

    private volatile long lastUpdate              = -1;
    private final Object  lock                    = new Object();

    public waStats() {
    }

    private boolean Update() {
        synchronized (lock) {
            final long tim = WebAuctionPlus.getCurrentMilli();
            final long sinceLast = tim - lastUpdate;
            // update no more than every 5 seconds
            if (lastUpdate == -1 || sinceLast >= 5000) {
                lastUpdate = tim;
                doUpdate();
                return true;
            }
        }
        return false;
    }

    private void doUpdate() {
        WebAuctionPlus.getLog().debug("Updating stats..");
        final Connection conn = WebAuctionPlus.dataQueries.getConnection();

        // total buy nows
        {
            PreparedStatement st = null;
            ResultSet rs = null;
            totalBuyNowCount = 0;
            try {
                WebAuctionPlus.getLog().debug("WA Query: Stats::count buy nows");
                st = conn.prepareStatement("SELECT COUNT(*) AS `count` FROM `" + WebAuctionPlus.dataQueries.dbPrefix()
                        + "Auctions`");
                rs = st.executeQuery();
                if (rs.next()) {
                    totalBuyNowCount = rs.getInt("count");
                }
            } catch (final SQLException e) {
                WebAuctionPlus.getLog().warning("Unable to get total buy now count");
                e.printStackTrace();
            } finally {
                MySQLConnPool.closeResources(st, rs);
            }
        }

        // total auctions
        {
            PreparedStatement st = null;
            ResultSet rs = null;
            totalAuctionCount = 0;
            try {
                WebAuctionPlus.getLog().debug("WA Query: Stats::count auctions");
                st = conn.prepareStatement("SELECT COUNT(*) AS `count` FROM `" + WebAuctionPlus.dataQueries.dbPrefix()
                        + "Auctions`");
                rs = st.executeQuery();
                if (rs.next()) {
                    totalAuctionCount = rs.getInt("count");
                }
            } catch (final SQLException e) {
                WebAuctionPlus.getLog().warning("Unable to get total auction count");
                e.printStackTrace();
            } finally {
                MySQLConnPool.closeResources(st, rs);
            }
        }

        // get max auction id
        {
            PreparedStatement st = null;
            ResultSet rs = null;
            maxAuctionId = -1;
            try {
                WebAuctionPlus.getLog().debug("WA Query: Stats::getMaxAuctionID");
                st = conn.prepareStatement("SELECT MAX(`id`) AS `id` FROM `" + WebAuctionPlus.dataQueries.dbPrefix()
                        + "Auctions`");
                rs = st.executeQuery();
                if (rs.next()) {
                    maxAuctionId = rs.getInt("id");
                }
            } catch (final SQLException e) {
                WebAuctionPlus.getLog().warning("Unable to query for max Auction ID");
                e.printStackTrace();
            } finally {
                MySQLConnPool.closeResources(st, rs);
            }
        }

        // get new auctions count
        {
            PreparedStatement st = null;
            ResultSet rs = null;
            newAuctionsCount = 0;
            try {
                final boolean isFirst = (newAuctionsCount_lastId < 1);
                WebAuctionPlus.getLog().debug("WA Query: Stats::getNewAuctionsCount" + (isFirst ? " -first-" : ""));
                if (isFirst) {
                    // first query
                    st = conn.prepareStatement("SELECT MAX(`id`) AS `id` FROM `"
                            + WebAuctionPlus.dataQueries.dbPrefix() + "Auctions`");
                    rs = st.executeQuery();
                    if (rs.next()) {
                        newAuctionsCount = 0;
                        newAuctionsCount_lastId = rs.getInt("id");
                    }
                } else {
                    // refresher query
                    st = conn.prepareStatement("SELECT COUNT(*) AS `count`, MAX(`id`) AS `id` FROM `"
                            + WebAuctionPlus.dataQueries.dbPrefix() + "Auctions` WHERE `id` > ?");
                    st.setInt(1, newAuctionsCount_lastId);
                    rs = st.executeQuery();
                    if (rs.next()) {
                        newAuctionsCount = rs.getInt("count");
                        if (newAuctionsCount > 0) {
                            newAuctionsCount_lastId = rs.getInt("id");
                        }
                    }
                }
            } catch (final SQLException e) {
                WebAuctionPlus.getLog().warning("Unable to query for new auctions count");
                e.printStackTrace();
            } finally {
                MySQLConnPool.closeResources(st, rs);
            }
        }

        // get ended auctions count
        {
            PreparedStatement st = null;
            ResultSet rs = null;
            endAuctionsCount = 0;
            try {
                final boolean isFirst = (endAuctionsCount_lastId < 1);
                WebAuctionPlus.getLog().debug("WA Query: Stats::getNewSalesCount" + (isFirst ? " -first-" : ""));
                if (isFirst) {
                    // first query
                    st = conn.prepareStatement("SELECT MAX(`id`) AS `id` FROM `"
                            + WebAuctionPlus.dataQueries.dbPrefix() + "LogSales`");
                    rs = st.executeQuery();
                    if (rs.next()) {
                        endAuctionsCount = 0;
                        endAuctionsCount_lastId = rs.getInt("id");
                    }
                } else {
                    // refresher query
                    st = conn.prepareStatement("SELECT COUNT(*) AS `count`, MAX(`id`) AS `id` FROM `"
                            + WebAuctionPlus.dataQueries.dbPrefix() + "LogSales` WHERE `id` > ?");
                    st.setInt(1, endAuctionsCount_lastId);
                    rs = st.executeQuery();
                    if (rs.next()) {
                        endAuctionsCount = rs.getInt("count");
                        if (endAuctionsCount > 0) {
                            endAuctionsCount_lastId = rs.getInt("id");
                        }
                    }
                }
            } catch (final SQLException e) {
                WebAuctionPlus.getLog().warning("Unable to query for new sales count");
                e.printStackTrace();
            } finally {
                MySQLConnPool.closeResources(st, rs);
            }
        }

        WebAuctionPlus.dataQueries.closeResources(conn);
    }

    // data access layer
    public int getTotalBuyNows() {
        Update();
        return totalBuyNowCount;
    }

    public int getTotalAuctions() {
        Update();
        return totalAuctionCount;
    }

    public int getMaxAuctionID() {
        Update();
        return maxAuctionId;
    }

    public int getNewAuctionsCount() {
        Update();
        return newAuctionsCount;
    }

    public int getEndedAuctionsCount() {
        Update();
        return endAuctionsCount;
    }

}
