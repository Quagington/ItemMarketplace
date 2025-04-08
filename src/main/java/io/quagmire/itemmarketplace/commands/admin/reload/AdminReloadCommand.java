package io.quagmire.itemmarketplace.commands.admin.reload;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.commands.admin.AdminCommand;
import io.quagmire.itemmarketplace.messages.Message;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

public class AdminReloadCommand extends AdminCommand {
  public AdminReloadCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender);
    setDescription("Reloads the configuration.");
    setPermission(getPermissionPrefix() + ".reload");
    setSyntax("");
  }

  @Override
  public boolean validate() {
    if (!sender.hasPermission(permission)) {
      messageSender(Message.NO_PERMISSIONS);
      return false;
    }
    return true;
  }

  @Override
  public void execute() {
    try {
      plugin.reloadConfig();
      plugin.getMessagesManager().reload();

      if (!plugin.getConfigurationManager().reloadAll()) {
        messageSender(Message.RELOAD_FAILURE);
        return;
      }

      plugin.getMenuManager().reload();

      messageSender(Message.RELOAD_SUCCESS);
    } catch (Exception ex) {
      ex.printStackTrace();
      messageSender(Message.RELOAD_FAILURE);
    }
  }

  @Override
  public List<String> tab() {
    return Collections.emptyList();
  }

  @Override
  public String subcommand() {
    return "reload";
  }
}