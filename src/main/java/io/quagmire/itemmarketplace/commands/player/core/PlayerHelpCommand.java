package io.quagmire.itemmarketplace.commands.player.core;

import io.quagmire.core.commands.help.HelpCommand;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class PlayerHelpCommand extends HelpCommand<ItemMarketplacePlugin> {
  public PlayerHelpCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender, plugin.getCommandRegistry());
  }
}
