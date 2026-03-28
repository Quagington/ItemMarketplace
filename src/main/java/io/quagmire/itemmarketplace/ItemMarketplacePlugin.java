package io.quagmire.itemmarketplace;

import io.quagmire.core.CorePlugin;
import io.quagmire.core.chat.ChatToolkit;
import io.quagmire.core.commands.CoreCommandExecutor;
import io.quagmire.core.commands.CoreCommandRegistry;
import io.quagmire.core.commands.CoreCommandTabCompleter;
import io.quagmire.core.configuration.ConfigurationManager;
import io.quagmire.core.databases.DatabaseConnectionPool;
import io.quagmire.core.databases.DatabaseMigrations;
import io.quagmire.core.databases.dialects.MySQLDatabaseConnectionPool;
import io.quagmire.core.folia.impl.PlatformScheduler;
import io.quagmire.core.menu.MenuManager;
import io.quagmire.core.menu.MenuPluginInterface;
import io.quagmire.core.messages.MessagesManager;
import io.quagmire.itemmarketplace.commands.admin.core.AdminHelpCommand;
import io.quagmire.itemmarketplace.commands.admin.core.AdminUnknownCommand;
import io.quagmire.itemmarketplace.commands.admin.reload.AdminReloadCommand;
import io.quagmire.itemmarketplace.commands.player.core.PlayerHelpCommand;
import io.quagmire.itemmarketplace.commands.player.core.PlayerUnknownCommand;
import io.quagmire.itemmarketplace.commands.player.listings.PlayerListingsCommand;
import io.quagmire.itemmarketplace.commands.player.blackmarket.BlackMarketCommand;
import io.quagmire.itemmarketplace.commands.player.sell.SellCommand;
import io.quagmire.itemmarketplace.commands.player.transactions.TransactionsCommand;
import io.quagmire.itemmarketplace.databases.DatabaseCollection;
import io.quagmire.itemmarketplace.integrations.accountmanagement.AccountManagementLoader;
import io.quagmire.itemmarketplace.manager.ListingManager;
import io.quagmire.itemmarketplace.menu.BlackMarketMenu;
import io.quagmire.itemmarketplace.menu.ConfirmationMenu;
import io.quagmire.itemmarketplace.menu.ListingsMenu;
import io.quagmire.itemmarketplace.menu.TransactionsMenu;
import io.quagmire.itemmarketplace.messages.Message;
import io.quagmire.itemmarketplace.model.MarketplaceListing;
import io.quagmire.itemmarketplace.model.MarketplaceTransaction;
import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;
import java.math.BigDecimal;

import io.quagmire.core.command.AliasCommand;

public class ItemMarketplacePlugin extends CorePlugin implements MenuPluginInterface {
  @Getter private final ChatToolkit chatToolkit;

  @Getter private final MenuManager<ItemMarketplacePlugin> menuManager;

  @Getter private final ConfigurationManager configurationManager;
  @Getter private final MessagesManager messagesManager;
  @Getter private PlatformScheduler scheduler;

  @Getter private CoreCommandRegistry<ItemMarketplacePlugin> commandRegistry;
  @Getter private CoreCommandRegistry<ItemMarketplacePlugin> adminCommandRegistry;

  private DatabaseConnectionPool databaseConnectionPool;
  @Getter private DatabaseCollection databaseCollection;
  
  @Getter private ListingManager listingManager;

  public ItemMarketplacePlugin() {
    chatToolkit = new ChatToolkit(this);

    configurationManager = new ConfigurationManager(this);
    commandRegistry = new CoreCommandRegistry<>(this, ItemMarketplacePlugin.class);
    adminCommandRegistry = new CoreCommandRegistry<>(this, ItemMarketplacePlugin.class);

    menuManager = new MenuManager<>(this, ItemMarketplacePlugin.class);
    messagesManager = new MessagesManager(this);
  }
  
  @Override
  public void onLoad() {
    databaseConnectionPool = new MySQLDatabaseConnectionPool(this);
    if (!databaseConnectionPool.test()) {
      getLogger().severe("Could not connect to database. Disabling plugin.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    databaseCollection = new DatabaseCollection(this, databaseConnectionPool);
    DatabaseMigrations<?> migrations = new DatabaseMigrations<>(this, ItemMarketplacePlugin.class, databaseConnectionPool);
    migrations.run();
  }

  @Override
  public void onEnable() {
    /* If the plugin data folder does not exist, create it */
    if (!getDataFolder().exists()) getDataFolder().mkdir();

    scheduler = folia.getScheduler();
    setupConfigurations();

    messagesManager.initialize(Message.getInitializers());

    setupCommands();
    setupAliasCommands();
    setupAdminCommands();
    setupMenus();
    
    // Initialize the listing manager
    initializeListingManager();

    setupAccountManagement();

    getLogger().info("ItemMarketplace has been enabled!");
  }
  
  /**
   * Initialize the listing manager and load active listings
   */
  private void initializeListingManager() {
    listingManager = new ListingManager(this);
    try {
      listingManager.initialize();
      getLogger().info("Loaded " + listingManager.getAllListings().size() + " active listings from database");
      
      // Schedule task to clean expired listings every 10 minutes
      getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
        try {
          listingManager.cleanExpiredListings();
        } catch (SQLException e) {
          getLogger().severe("Failed to clean expired listings: " + e.getMessage());
        }
      }, 0L, 12000L); // Run every 10 minutes (12000 ticks)
      
    } catch (SQLException e) {
      getLogger().severe("Failed to initialize listing manager: " + e.getMessage());
    }
  }

  public void setupMenus() {
    Set<String> menus = new HashSet<>();
//    menus.add("root");
//
//    menus.addAll(getConfig().getStringList("menus.extra-roots"));
//    menus.addAll(shopManager.getShopNames());
//
//    menus.forEach(menu -> {
//      menuManager.register(menu, new ShopMenu(this, menu));
//    });
//
//    List<String> itemFilterMenuNames = playerFilterManager.getMenuNames();
//    itemFilterMenuNames.forEach(menu -> {
//      menuManager.register(menu, new ItemFilterMenu(this, menu));
//    });
//
    
    // Register the listings menu
    menuManager.register("listings", new ListingsMenu(this, "listings"));
    
    // Register the black market menu
    menuManager.register("blackmarket", new BlackMarketMenu(this, "blackmarket"));
    
    // Register the transactions menu
    menuManager.register("transactions", new TransactionsMenu(this, "transactions"));
    
    menuManager.reload();
    getServer().getPluginManager().registerEvents(menuManager, this);
  }

  private void setupAccountManagement() {
    if (getServer().getPluginManager().isPluginEnabled("AccountManagement")) {
      AccountManagementLoader.load(
              databaseCollection.getListingsDatabase(),
              databaseCollection.getTransactionHistoryDatabase()
      );
      getLogger().info("AccountManagement found! Registered ItemMarketplace data provider.");
    }
  }

  private void setupConfigurations() {
    configurationManager.reloadAll();
  }

  private void setupCommands() {
    commandRegistry = new CoreCommandRegistry<>(this, ItemMarketplacePlugin.class);
    commandRegistry.setAlias("itemmarket");
    commandRegistry.setPermissionPrefix("itemmarket");
    commandRegistry.setDisplayName("Item Marketplace");

    commandRegistry.register(PlayerHelpCommand.class);
    commandRegistry.register(PlayerUnknownCommand.class);
    commandRegistry.register(PlayerListingsCommand.class);
    
    // Register new commands
    commandRegistry.register(BlackMarketCommand.class);
    commandRegistry.register(SellCommand.class);
    commandRegistry.register(TransactionsCommand.class);

    commandRegistry.setDefaultCommand("help");
    commandRegistry.setFallbackCommand("unknown");

    Objects.requireNonNull(getCommand("itemmarket")).setExecutor(new CoreCommandExecutor<>(this));
    Objects.requireNonNull(getCommand("itemmarket")).setTabCompleter(new CoreCommandTabCompleter<>(this));
  }

  private void setupAdminCommands() {
    adminCommandRegistry = new CoreCommandRegistry<>(this, ItemMarketplacePlugin.class);
    adminCommandRegistry.setAlias("itemmarketadmin");
    adminCommandRegistry.setPermissionPrefix("itemmarket");
    adminCommandRegistry.setDisplayName("Item Marketplace Administrator");

    adminCommandRegistry.register(AdminHelpCommand.class);
    adminCommandRegistry.register(AdminUnknownCommand.class);
    adminCommandRegistry.register(AdminReloadCommand.class);

    adminCommandRegistry.setDefaultCommand("help");
    adminCommandRegistry.setFallbackCommand("unknown");

    Objects.requireNonNull(getCommand("itemmarketadmin")).setExecutor(new CoreCommandExecutor<>(this, adminCommandRegistry));
    Objects.requireNonNull(getCommand("itemmarketadmin")).setTabCompleter(new CoreCommandTabCompleter<>(this, adminCommandRegistry));
  }

  /**
   * Setup alias commands for direct access to specific features
   */
  private void setupAliasCommands() {
    // Marketplace command
    AliasCommand<ItemMarketplacePlugin> marketplaceCommand = new AliasCommand<>(this, "marketplace");
    Objects.requireNonNull(getCommand("marketplace")).setExecutor(marketplaceCommand);
    Objects.requireNonNull(getCommand("marketplace")).setTabCompleter(marketplaceCommand);
    marketplaceCommand.setCommandHandler((sender, args) -> {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }
        menuManager.openMenu(player, player, "listings");
        return true;
    });
    
    // Black Market command
    AliasCommand<ItemMarketplacePlugin> blackMarketCommand = new AliasCommand<>(this, "blackmarket");
    Objects.requireNonNull(getCommand("blackmarket")).setExecutor(blackMarketCommand);
    Objects.requireNonNull(getCommand("blackmarket")).setTabCompleter(blackMarketCommand);
    blackMarketCommand.setCommandHandler((sender, args) -> {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }
        BlackMarketMenu blackMarketMenu = (BlackMarketMenu) menuManager.getMenu("blackmarket");
        if (blackMarketMenu != null) {
            blackMarketMenu.refreshBlackMarket(player);
        }
        return true;
    });
    
    // Transactions command
    AliasCommand<ItemMarketplacePlugin> transactionsCommand = new AliasCommand<>(this, "transactions");
    Objects.requireNonNull(getCommand("transactions")).setExecutor(transactionsCommand);
    Objects.requireNonNull(getCommand("transactions")).setTabCompleter(transactionsCommand);
    transactionsCommand.setCommandHandler((sender, args) -> {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }
        menuManager.openMenu(player, player, "transactions");
        return true;
    });
    
    // Sell command
    AliasCommand<ItemMarketplacePlugin> sellCommand = new AliasCommand<>(this, "sell");
    Objects.requireNonNull(getCommand("sell")).setExecutor(sellCommand);
    Objects.requireNonNull(getCommand("sell")).setTabCompleter(sellCommand);
    sellCommand.setCommandHandler((sender, args) -> {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players");
            return true;
        }
        
        if (args.length < 1) {
            player.sendMessage("Usage: /sell <price>");
            return true;
        }
        
        double price;
        try {
            price = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("Invalid price format. Please use a valid number");
            return true;
        }
        
        if (price <= 0) {
            player.sendMessage("Price must be greater than 0");
            return true;
        }
        
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage("You must be holding an item to sell");
            return true;
        }
        
        // Create confirmation menu
        ConfirmationMenu confirmationMenu = new ConfirmationMenu("Sell Item", "Are you sure you want to sell " + 
                itemInHand.getAmount() + "x " + 
                itemInHand.getType().toString().toLowerCase().replace("_", " ") + 
                " for $" + price + "?");
        
        confirmationMenu.setConfirmAction(() -> {
            try {
                listingManager.createListing(
                    player,
                    itemInHand,
                    BigDecimal.valueOf(price),
                    null
                );
                player.getInventory().setItemInMainHand(null);
                player.sendMessage("Your item has been listed on the marketplace for $" + price);
            } catch (SQLException e) {
                player.sendMessage(messagesManager.get(Message.SELL_FAILURE.name().toLowerCase()));
                e.printStackTrace();
            }
        });
        
        confirmationMenu.setCancelAction(() -> 
            player.sendMessage("Selling cancelled"));
        
        confirmationMenu.open(player);
        return true;
    });
  }

  @Override
  public void onDisable() {
    if (getServer().getPluginManager().isPluginEnabled("AccountManagement")) {
      AccountManagementLoader.unload();
    }

    menuManager.closeAllMenusSynchronously();

    HandlerList.unregisterAll(this);
    scheduler.cancelAllTasks();

    if (databaseConnectionPool != null) {
      databaseConnectionPool.close();
    }
    
    getLogger().info("ItemMarketplace has been disabled!");
  }
}