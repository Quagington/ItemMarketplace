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
import io.quagmire.itemmarketplace.databases.DatabaseCollection;
import io.quagmire.itemmarketplace.manager.ListingManager;
import io.quagmire.itemmarketplace.messages.Message;
import lombok.Getter;
import org.bukkit.event.HandlerList;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
    setupAdminCommands();
    setupMenus();
    
    // Initialize the listing manager
    initializeListingManager();
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
    menuManager.reload();
    getServer().getPluginManager().registerEvents(menuManager, this);
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

  @Override
  public void onDisable() {
    menuManager.closeAllMenusSynchronously();

    HandlerList.unregisterAll(this);
    scheduler.cancelAllTasks();

    if (databaseConnectionPool != null) {
      databaseConnectionPool.close();
    }
  }
}