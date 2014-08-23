package me.lorenzop.webauctionplus.tasks;

import java.util.ArrayList;
import java.util.List;

import me.lorenzop.webauctionplus.WebAuctionPlus;

import org.bukkit.Server;

public class AnnouncerTask implements Runnable {

    private int                  currentAnnouncement  = 0;
    // private int numberOfAnnouncements = 0;

    public boolean               announceRandom       = false;
    private final List<String>   announcementMessages = new ArrayList<String>();
    public String                chatPrefix           = "";

    private final WebAuctionPlus plugin;

    public AnnouncerTask(WebAuctionPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (plugin.getServer().getOnlinePlayers().size() == 0) {
            return;
        }
        if (announcementMessages.isEmpty()) {
            return;
        }
        // random
        if (announceRandom) {
            currentAnnouncement = WebAuctionPlus.getNewRandom(currentAnnouncement, announcementMessages.size() - 1);
            announce(currentAnnouncement);
            // sequential
        } else {
            while (currentAnnouncement > announcementMessages.size() - 1) {
                currentAnnouncement -= announcementMessages.size();
            }
            announce(currentAnnouncement);
            currentAnnouncement++;
        }
        // numberOfAnnouncements++;
    }

    public void addMessages(List<String> addMsg) {
        for (final String msg: addMsg) {
            addMessages(msg);
        }
    }

    public void addMessages(String addMsg) {
        announcementMessages.add(addMsg);
    }

    public void clearMessages() {
        announcementMessages.clear();
    }

    public void announce(int lineNumber) {
        if (announcementMessages.isEmpty() || lineNumber < 0) {
            return;
        }
        WebAuctionPlus.getLog().info("Announcement # " + Integer.toString(lineNumber));
        announce(announcementMessages.get(lineNumber));
    }

    public void announce(String line) {
        if (line.isEmpty()) {
            return;
        }
        final Server server = plugin.getServer();
        final String[] messages = line.split("&n");
        for (String message: messages) {
            // is command
            if (message.startsWith("/")) {
                server.dispatchCommand(server.getConsoleSender(), message.substring(1));
            } else if (server.getOnlinePlayers().size() > 0) {
                message = WebAuctionPlus.ReplaceColors(chatPrefix + message);
                server.broadcast(message, "wa.announcer.receive");
            }
        }
    }

}
