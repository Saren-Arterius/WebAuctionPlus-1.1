package me.lorenzop.webauctionplus.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.lorenzop.webauctionplus.WebAuctionPlus;
import me.lorenzop.webauctionplus.dao.Auction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ShoutSignTask implements Runnable {

    private int                  lastAuction;

    private final WebAuctionPlus plugin;

    public ShoutSignTask(WebAuctionPlus plugin) {
        this.plugin = plugin;
        // Get current auction ID
        lastAuction = WebAuctionPlus.stats.getMaxAuctionID();
        WebAuctionPlus.getLog().debug("Current Auction id = " + lastAuction);
    }

    @Override
    public void run() {
        // check for new auctions
        final int latestAuctionID = WebAuctionPlus.stats.getMaxAuctionID();
        if (lastAuction >= latestAuctionID) {
            return;
        }
        lastAuction = latestAuctionID;
        WebAuctionPlus.getLog().debug("Current Auction id = " + lastAuction);
        if (plugin.getServer().getOnlinePlayers().size() == 0) {
            return;
        }

        final Auction auction = WebAuctionPlus.dataQueries.getAuction(latestAuctionID);
        final ItemStack stack = auction.getItemStack();

        // TODO: language here
        String msg;
        if (auction.getAllowBids()) {
            msg = "New auction: ";
        } else {
            msg = "For sale: ";
        }
        msg += Integer.toString(stack.getAmount()) + "x " + auction.getItemTitle() + " ";
        if (stack.getEnchantments().size() == 1) {
            msg += "(with 1 enchantment) ";
        } else if (stack.getEnchantments().size() > 1) {
            msg += "(with " + Integer.toString(stack.getEnchantments().size()) + " enchantments) ";
        }
        if (auction.getAllowBids()) {
            msg += "has started!";
        } else {
            msg += "selling for " + WebAuctionPlus.FormatPrice(auction.getPrice()) + " each.";
        }
        WebAuctionPlus.getLog().info(msg);

        // announce globally
        if (WebAuctionPlus.announceGlobal()) {
            Bukkit.broadcastMessage(WebAuctionPlus.chatPrefix + msg);
        } else {

            // Loop each shout sign, sending the New Auction message to each
            final List<Location> SignsToRemove = new ArrayList<Location>();
            for (final Map.Entry<Location, Integer> entry: plugin.shoutSigns.entrySet()) {
                final Location loc = entry.getKey();
                final int radius = entry.getValue();
                if (loc.getBlock().getType() != Material.SIGN && loc.getBlock().getType() != Material.WALL_SIGN) {
                    SignsToRemove.add(loc);
                    continue;
                }
                WebAuctionPlus.BroadcastRadius(msg, loc, radius);
            }
            try {
                for (final Location signLoc: SignsToRemove) {
                    plugin.shoutSigns.remove(signLoc);
                    WebAuctionPlus.dataQueries.removeShoutSign(signLoc);
                    WebAuctionPlus.getLog().info("Removed invalid sign at location: " + signLoc);
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }

        }
    }

}
