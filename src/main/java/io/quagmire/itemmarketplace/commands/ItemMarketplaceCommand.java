package io.quagmire.itemmarketplace.commands;

import io.quagmire.core.commands.CoreCommand;
import io.quagmire.core.commands.CoreCommandRegistry;
import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.messages.Message;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Map;

public abstract class ItemMarketplaceCommand extends CoreCommand<ItemMarketplacePlugin> {
  public ItemMarketplaceCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender, CoreCommandRegistry<ItemMarketplacePlugin> registry) {
    super(plugin, command, args, sender, registry);
  }

  protected void messageSender(Message message) {
    super.messageSender(message.name(), true);
  }

  protected void messageSender(Message message, Map<String, String> renders) {
    super.messageSender(message.name(), true, renders);
  }
}
