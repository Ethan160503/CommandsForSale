package me.Tecno_Wizard.CommandsForSale.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

import me.Tecno_Wizard.CommandsForSale.GUI.GUIClickListener;
import me.Tecno_Wizard.CommandsForSale.GUI.GUIConstructor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.BoughtCmdsExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.CmdsToBuyExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.modCommands.ModCommandsController;
import me.Tecno_Wizard.CommandsForSale.saveConvertSystems.ConvertSave;
import me.Tecno_Wizard.CommandsForSale.updateWarning.ModUpdateWarner;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.IncommingCommandProcessor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.VaultFailedBuyCmdsExecutor;
import me.Tecno_Wizard.CommandsForSale.commandProcessors.buyCommand.BuyCommandController;
import me.Tecno_Wizard.CommandsForSale.core.Updater.UpdateType;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.skionz.dataapi.DataFile;

/**
 * Main class of CommandsForSale
 * 
 * @author Ethan Zeigler
 *
 */
public class Main extends JavaPlugin {
	private static Resources resources;
	private static final Logger log = Bukkit.getLogger();
	public static Economy econ;
	private static DataFile save;
	private static Metrics pm;
	private static Updater updater;
	private static boolean vaultIsReady;

	@Override
	public void onEnable() {
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
		checkForUpdate();
	}

	/**
	 * Reloads the plugin entirely
	 */
	public void reloadSystem(){
		checkDirectories();
		setUpConfig();
		initiateSave();
		resetResources();
		checkForUpdate();
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////////
	//File structures
	/////////////////////////////////////////////////////////////////////////////////////////////////////

	// controls setup of config file
	public void setUpConfig() {

		super.reloadConfig();// makes sure that the current version of the file
		// is loaded

		// these 2 values are set to comply with the Curse rules, or i'd have
		// them always on (sorry)
		getConfig().addDefault("UpdaterOn", true);
		getConfig().addDefault("AutomaticallyUpdate", false);
		getConfig().addDefault("CurrencyPlural", "void");
		
		// other values
		getConfig().addDefault("PluginPrefix", "me/Tecno_Wizard/CommandsForSale");
		getConfig().addDefault("GUIEnabled", true);

		// adds the MainCommands section of config
		getConfig().addDefault("MainCommands", new ArrayList<String>());

		// if a value with the same name as the default exists, it will not be
		// touched, otherwise the default will be added
		getConfig().options().copyDefaults(true);
		saveConfig();

		if (!getConfig().contains("CommandOptions")) {
			log.severe("[CommandsForSale] It seems that either your configuration has been corrupted or is empty.\n"
					+ "[CommandsForSale] The plugin will not operate without any commands. Make sure you are " +
					"regularly saving a copy of the configuration in case of corruption.");
		}

		ArrayList<String> inLower = new ArrayList<>();
		
		// changes commands to lower case
		for (String toChange : getConfig().getStringList("MainCommands")) {
			inLower.add(toChange.toLowerCase());
		}
		getConfig().set("MainCommands", inLower);
		saveConfig();
		
		// controls and generates the command options section
		for (String commandName : getConfig().getStringList("MainCommands")) {
			getConfig().addDefault("CommandOptions." + commandName + ".price", 0);
			getConfig().addDefault("CommandOptions." + commandName + ".permission", "void");
		}
		// save again
		getConfig().options().copyDefaults(true);
		saveConfig();
		// controls aliases section
		for (String cmd : getConfig().getStringList("MainCommands"))

		{
			getConfig().addDefault("Aliases." + cmd, new ArrayList<String>());
		}
		// save again
		getConfig().options().copyDefaults(true);
		saveConfig();

		// generates all commands to prevent searching through the entire config
		// on each run of a command
		ArrayList<String> allCmds = new ArrayList<>();
		for (String cmd : getConfig().getStringList("MainCommands")) {
			allCmds.add(cmd);
			for (String aliase : getConfig().getStringList("Aliases." + cmd)) {
				allCmds.add(aliase);
			}
		}
		getConfig().set("AllCommands", allCmds);
		saveConfig();
		resources = new Resources(this);
	}

	// controls mainsave file
	public void initiateSave() {
		ConvertSave.attemptConvert(this);

		save = new DataFile("plugins" + File.separator + "me/Tecno_Wizard/CommandsForSale"
				+ File.separator + "MainSave", "txt");
		// if counter for times ran exists/file evisted
		if (!save.contains("NumberOfTimesRan")) {

			// since the plugin has not run before or someone was an idiot and
			// corrupted their save file, we shall attack with characters
			log.info("[CommandsForSale] Welcome to CommandsForSale!\n"
					+ "[CommandsForSale] You are seeing this because there was no main save file or you just updated\n"
					+ "[CommandsForSale] Make sure to configure your settings in the config if you have not already!\n"
					+ "[CommandsForSale] Instructions on how to use the config are on the Bukkit page in PDF "
					+ "form under the config section! Just Google bukkit commandsforsale or search for it in BukkitDev\n"
					+ "[CommandsForSale] ENJOY IT!");
			save.set("NumberOfTimesRan", 1);
			save.save();

		}// if counter did not exist
		else {
			int newTimesRan = save.getInt("NumberOfTimesRan") + 1;
			save.set("NumberOfTimesRan", newTimesRan);
			save.save();
		}
		// feedback message (I like feedback)
		if (save.getInt("NumberOfTimesRan") == 5
				|| save.getInt("NumberOfTimesRan") == 10) {
			log.info("[CommandsForSale] Now that you have used CommandsForSale a few times, PLEASE GIVE FEEDBACK!\n"
					+ "[CommandsForSale] Use this link: http://goo.gl/forms/kCB7H2LNZw\n"
					+ "[CommandsForSale] This message will appear at 5 and 10 runs of the plugin, and never again.");
		}
	}

	public void checkDirectories() {
		// player folder
		File dir = new File("plugins" + File.separator + "me/Tecno_Wizard/CommandsForSale" + File.separator + "Players");
		if(!dir.exists())
			dir.mkdir();

		// log folder
		dir = new File("plugins/CommandsForSale/PurchaseLogs");
		if(!dir.exists())
			dir.mkdir();
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
		} else {
			VaultFailedBuyCmdsExecutor vce = new VaultFailedBuyCmdsExecutor(
					this);
			getCommand("buycmd").setExecutor(vce);
			getCommand("confirm").setExecutor(vce);
			getCommand("deny").setExecutor(vce);
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

		if (didMetricsLoad) {
			Metrics.Graph previousTimesRan = pm.createGraph("PreviousTimesRan");
			previousTimesRan.addPlotter(new Metrics.Plotter() {
				@Override
				// returns data for McStats on # of times the plugin was ran
				public int getValue() {
					return save.getInt("NumberOfTimesRan");
				}
			});
		} else {
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
			UpdateType type;
			if (getConfig().getBoolean("AutomaticallyUpdate", false))
				type = UpdateType.DEFAULT;
			else
				type = UpdateType.NO_DOWNLOAD;

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