package io.quagmire.itemmarketplace.commands.player;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.commands.ItemMarketplaceCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public abstract class PlayerCommand extends ItemMarketplaceCommand {
  public PlayerCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender, plugin.getCommandRegistry());
  }
}
