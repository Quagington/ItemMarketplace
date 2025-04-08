package io.quagmire.itemmarketplace.commands.admin;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.commands.ItemMarketplaceCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public abstract class AdminCommand extends ItemMarketplaceCommand {
  public AdminCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender, plugin.getAdminCommandRegistry());
  }
}
