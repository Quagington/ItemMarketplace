package io.quagmire.itemmarketplace.commands.admin.core;

import io.quagmire.core.commands.help.HelpCommand;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class AdminHelpCommand extends HelpCommand<ItemMarketplacePlugin> {
  public AdminHelpCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender, plugin.getAdminCommandRegistry());
  }
}
