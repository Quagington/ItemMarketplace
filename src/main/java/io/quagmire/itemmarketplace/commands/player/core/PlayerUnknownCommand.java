package io.quagmire.itemmarketplace.commands.player.core;

import io.quagmire.core.commands.CoreCommandRegistry;
import io.quagmire.core.commands.unknown.UnknownCommand;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class PlayerUnknownCommand extends UnknownCommand<ItemMarketplacePlugin> {
  public PlayerUnknownCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender, plugin.getCommandRegistry());
  }

  @Override
  protected String getMessage() {
    CoreCommandRegistry<ItemMarketplacePlugin> registry = plugin.getCommandRegistry();
    String primaryColor = registry.getPrimaryColor();
    String secondaryColor = registry.getSecondaryColor();
    return primaryColor + "Unknown command provided. " + secondaryColor + "Use " + primaryColor + "/" + registry.getAlias() + " help" + secondaryColor + " for a list of commands.";
  }
}
