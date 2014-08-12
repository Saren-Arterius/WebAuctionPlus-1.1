package me.lorenzop.webauctionplus;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

public class Plugins3rdParty {

    private final logBoots log;

    public Plugins3rdParty(final logBoots log) {
        if (log == null) {
            throw new NullPointerException();
        }
        this.log = log;
        if (setupVault()) {
            log.info("Found Vault.");
        } else {
            log.warning("Failed to find Vault.");
        }
    }

    /**
     * Vault
     */
    private Plugin  vault = null;
    private Chat    chat  = null;
    private Economy econ  = null;

    private boolean setupVault() {
        vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null) {
            return false;
        }
        final ServicesManager service = Bukkit.getServicesManager();
        // chat
        {
            final RegisteredServiceProvider<Chat> provider = service.getRegistration(Chat.class);
            chat = provider.getProvider();
            if (chat == null) {
                log.info("Found chat plugin.");
            } else {
                log.warning("Failed to find chat plugin.");
            }
        }
        // economy
        {
            final RegisteredServiceProvider<Economy> provider = service.getRegistration(Economy.class);
            econ = provider.getProvider();
            if (econ == null) {
                log.info("Found economy plugin.");
            } else {
                log.warning("Failed to find economy plugin.");
            }
        }
        return isLoaded_Vault();
    }

    public boolean isLoaded_Vault() {
        return (vault != null);
    }

    public Chat getChat() {
        return chat;
    }

    public Economy getEconomy() {
        return econ;
    }

    // /**
    // * SignLink
    // */
    // private Plugin signlink = null;

    // private boolean setupSignLink() {
    // this.signlink = Bukkit.getPluginManager().getPlugin("SignLink");
    // if(this.signlink == null) return false;
    // return isLoaded_SignLink();
    // }
    public boolean isLoaded_SignLink() {
        // return (this.signlink != null);
        return false;
    }

}
