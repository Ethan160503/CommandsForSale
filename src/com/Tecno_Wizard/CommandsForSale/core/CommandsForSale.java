package com.Tecno_Wizard.CommandsForSale.core;

import com.Tecno_Wizard.CommandsForSale.language.Language;
import com.skionz.dataapi.DataFile;
import me.Tecno_Wizard.CommandsForSale.GUI.GUIClickListener;
import me.Tecno_Wizard.CommandsForSale.GUI.GUIConstructor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.BoughtCmdsExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.CmdsToBuyExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.IncommingCommandProcessor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.VaultFailedBuyCmdsExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.buyCommand.BuyCommandController;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.buyCommand.BuyCommandOnce.BuyOnceExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.modCommands.ModCommandsController;
import me.Tecno_Wizard.CommandsForSale.core.Metrics;
import me.Tecno_Wizard.CommandsForSale.core.Resources;
import me.Tecno_Wizard.CommandsForSale.core.Updater;
import me.Tecno_Wizard.CommandsForSale.core.Updater.UpdateType;
import me.Tecno_Wizard.CommandsForSale.saveConvertSystems.ConvertSave;
import me.Tecno_Wizard.CommandsForSale.updateWarning.ModUpdateWarner;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Main class of CommandsForSale
 *
 * @author Ethan Zeigler
 *
 */
public class CommandsForSale extends JavaPlugin {
    private static Resources resources;
    private static final Logger log = Bukkit.getLogger();
    public static Economy econ;
    private static DataFile save;
    private static me.Tecno_Wizard.CommandsForSale.core.Metrics pm;
    private static Updater updater;
    private static boolean vaultIsReady;
    private Language lang;

    @Override
    public void onEnable() {
        lang = new Language(this);
        if (!setupEconomy()) {
            // if the economy could not be loaded at the fault
            // of Vault (heh, rhyme) or the economy plugin
            Bukkit.getConsoleSender().sendMessage(
                    ChatColor.RED
                            + "[CommandsForSale] VAULT DEPENDENCY HAS FAILED.\n"
                            + "[CommandsForSale] UNTIL VAULT IS REPAIRED, USERS WILL NOT BE ABLE TO BUY COMMANDS!");
            vaultIsReady = false;
        } else
            vaultIsReady = true;

        checkDirectories();
        setUpConfig();
        initiateSave();
        startPluginMetrics();
        registerListeners();
        registerCmds();
        writeReadMeFiles();
        checkForUpdate();

        if(!Bukkit.getOnlineMode()){
            log.warning("[CommandsForSale] WARNING: Server is in offline mode. This may result in severe errors if a player changes their name." +
                    "\nPlayer will lose ALL OF THEIR DATA!");
        }

        // final operations
        resources.logString("The plugin was enabled.");
    }

    @Override
    public void onDisable(){
        Date date = new Date();
        resources.logString("The plugin was shut down at " + date.toString());
    }

    /**
     * Reloads the plugin entirely
     */
    public void reloadSystem(){
        checkDirectories();
        setUpConfig();
        initiateSave();
        resetResources();
        registerCmds();
		/* no call to register listeners, as there will just be duplicates.
		 Listeners have been adjusted to automatically reflect changes. */
        checkForUpdate();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    //File structures
    /////////////////////////////////////////////////////////////////////////////////////////////////////

    // controls setup of config file
    public void setUpConfig() {

        super.reloadConfig();// makes sure that the current version of the file is loaded

        getConfig().addDefault("UpdaterOn", true);
        getConfig().addDefault("CurrencyPlural", "PLEASE SET TO THE PLURAL FORM OF YOUR CURRENCY!");
        getConfig().addDefault("PluginPrefix", "CommandsForSale");

        // adds the Commands section of config
        getConfig().addDefault("Commands", new ArrayList<String>());

        // if a value with the same name as the default exists, it will not be
        // touched, otherwise the default will be added
        getConfig().options().copyDefaults(true);
        saveConfig();

        ArrayList<String> inLower = new ArrayList<>();

        // changes commands to lower case
        for (String toChange : getConfig().getStringList("Commands")) {
            inLower.add(toChange.toLowerCase());
        }

        getConfig().set("Commands", inLower);
        saveConfig();

        // controls and generates the command options section
        for (String commandName : getConfig().getStringList("Commands")) {
            getConfig().addDefault("CommandOptions." + commandName + ".commandRegex", commandName);
            getConfig().addDefault("CommandOptions." + commandName + ".description", "ENTER DESCRIPTION");
            getConfig().addDefault("CommandOptions." + commandName + ".price", 0);
            getConfig().addDefault("CommandOptions." + commandName + ".canBeOneTimeUsed", true);
            getConfig().addDefault("CommandOptions." + commandName + ".oneTimeUsePrice", 0);
            getConfig().addDefault("CommandOptions." + commandName + ".permission", "");
            getConfig().addDefault("CommandOptions." + commandName + ".GUIIcon", "WEB");
        }

        if (!getConfig().contains("CommandOptions")) {
            log.severe("[CommandsForSale] It seems your config is empty.\n"
                    + "[CommandsForSale] The plugin will not operate without any commands. Please add a command.");
        }

        // save again
        getConfig().options().copyDefaults(true);
        saveConfig();

        // save again
        getConfig().options().copyDefaults(true);
        saveConfig();

        // generates all commands to prevent searching through the entire config
        // on each run of a command
        saveConfig();
        resources = new Resources(this);
    }

    // controls mainsave file
    public void initiateSave() {

        save = new DataFile("plugins" + File.separator + "CommandsForSale"
                + File.separator + "MainSave", "txt");
        // if counter for times ran exists/file existed
        if (!save.contains("NumberOfTimesRan")) {
            // since the plugin has not run before or someone was an idiot and
            // corrupted their save file, we shall attack with characters
            log.info("[CommandsForSale] Welcome to CommandsForSale!\n"
                    + "[CommandsForSale] You are seeing this because there was no main save file.\n"
                    + "[CommandsForSale] Make sure to configure your settings in the config if you have not already!\n"
                    + "[CommandsForSale] Instructions on how to use the config are on the Bukkit page in PDF "
                    + "form under the config section! Just Google bukkit commandsforsale or search for it in BukkitDev\n"
                    + "[CommandsForSale] ENJOY IT!");
            save.set("NumberOfTimesRan", 1);
            save.set("V1.2.3HasRan", false);
            save.save();

        } else {
            int newTimesRan = save.getInt("NumberOfTimesRan") + 1;
            save.set("NumberOfTimesRan", newTimesRan);
            save.save();
        }

        Boolean hasRunVersion = save.getBoolean("V1.2.3HasRan");
        if(hasRunVersion == null)
            hasRunVersion = false;
        resources.setDisplayVerisonInfo(!hasRunVersion);
        save.set("V1.2.3HasRan", true);

        // feedback message (I like feedback)
        if (save.getInt("NumberOfTimesRan") == 5
                || save.getInt("NumberOfTimesRan") == 10) {
            log.info("[CommandsForSale] Now that you have used CommandsForSale a few times, PLEASE GIVE FEEDBACK!\n"
                    + "[CommandsForSale] Use this link: http://goo.gl/forms/kCB7H2LNZw\n"
                    + "[CommandsForSale] This message will appear at 5 and 10 runs of the plugin, and never again.");
        }
        save.save();
    }

    public void checkDirectories() {
        // log folder
        File dir = new File("plugins/CommandsForSale/PurchaseLogs");
        dir.mkdirs();

        // player permanent folder
        dir = new File("plugins/CommandsForSale/Players");
        dir.mkdirs();

        // player pass folder
        dir = new File("plugins/CommandsForSale/PlayerPasses");
        dir.mkdirs();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    //Command/listener forwarding and setup
    ////////////////////////////////////////////////////////////////////////////////////////////

    // registers listeners
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new IncommingCommandProcessor(this), this);
        getServer().getPluginManager().registerEvents(new ModUpdateWarner(this), this);
        getServer().getPluginManager().registerEvents(new GUIClickListener(this), this);
    }

    // registers commands throughout plugin
    private void registerCmds() {
        commandPurchaseSetup();
        getCommand("cmdsforsale").setExecutor(new ModCommandsController(this));
        getCommand("cmdstobuy").setExecutor(new CmdsToBuyExecutor(this));
        getCommand("boughtcmds").setExecutor(new BoughtCmdsExecutor(this));

    }

    // used for organizational reasons, delegates objects to use based on
    // whether economy is present
    private void commandPurchaseSetup() {
        if (vaultIsReady) {
            BuyCommandController bce = new BuyCommandController(this);
            if(getConfig().getBoolean("GUIEnabled", true)){
                getCommand("buycmd").setExecutor(new GUIConstructor(this));
            } else {
                getCommand("buycmd").setExecutor(bce);
            }
            getCommand("confirm").setExecutor(bce);
            getCommand("deny").setExecutor(bce);
            getCommand("buyonce").setExecutor(new BuyOnceExecutor(this));
        } else {
            VaultFailedBuyCmdsExecutor vce = new VaultFailedBuyCmdsExecutor(this);
            getCommand("buycmd").setExecutor(vce);
            getCommand("confirm").setExecutor(vce);
            getCommand("deny").setExecutor(vce);
            getCommand("buyonce").setExecutor(vce);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //External Resource initiation
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void startPluginMetrics() {
        try {
            pm = new Metrics(this);
        } catch (IOException e) {
        }

        boolean didMetricsLoad = pm.start();

        if (!didMetricsLoad) {
            log.info(String.format("[%s] Plugin metrics is disabled. This will not affect the performance of CommandsForSale.",
                    getConfig().getString("PluginPrefix")));
        }

    }

    // initiates economy from Vault
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @SuppressWarnings("incomplete-switch")
    // Checks for plugin update, using gravity_low's Updater API
    private void checkForUpdate() {
        if (getConfig().getBoolean("UpdaterOn", true)) {
            UpdateType type = UpdateType.NO_DOWNLOAD;
            updater = new Updater(this, 86562, this.getFile(), type, true);
            switch (updater.getResult()) {
                case UPDATE_AVAILABLE:
                    Bukkit.getConsoleSender().sendMessage(ChatColor.AQUA + "[CommandsForSale] There is an update ready to download. Download it from BukkitDev or turn AutomaticallyUpdate to true.");
                    break;
                case SUCCESS:
                    Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[CommandsForSale] The next version of CommandsForSale has been downloaded! Reload to implement it.");
                    break;
                case FAIL_DOWNLOAD:
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[CommandsForSale] There is a new version of CommandsForSale, but it failed to download.\nYou May need to download it from the site.");
                    break;
            }
        } else
            updater = null;
    }

    private void writeReadMeFiles(){
        saveResource("MaterialList.txt", true);
    }

    public static Updater getUpdater() {
        return updater;
    }

    public Resources getResources(){
        return resources;
    }

    public void resetResources(){
        Resources.refresh(this);
    }

}