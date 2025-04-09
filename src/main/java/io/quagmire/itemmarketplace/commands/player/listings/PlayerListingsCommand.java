package io.quagmire.itemmarketplace.commands.player.listings;

import io.quagmire.itemmarketplace.ItemMarketplacePlugin;
import io.quagmire.itemmarketplace.commands.player.PlayerCommand;
import io.quagmire.itemmarketplace.messages.Message;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class PlayerListingsCommand extends PlayerCommand {
  public PlayerListingsCommand(ItemMarketplacePlugin plugin, Command command, String[] args, CommandSender sender) {
    super(plugin, command, args, sender);
    setDescription("Open the marketplace listings menu.");
    setPermission(getPermissionPrefix() + ".listings");
    setSyntax("");
  }

  @Override
  public boolean validate() {
    if (!(sender instanceof Player)) {
      messageSender(Message.PLAYER_ONLY);
      return false;
    }
    
    if (!sender.hasPermission(permission)) {
      messageSender(Message.NO_PERMISSIONS);
      return false;
    }
    
    return true;
  }

  @Override
  public void execute() {
    try {
      Player player = (Player) sender;
      plugin.getScheduler().runAtEntity(player, (task) -> {
        plugin.getMenuManager().openMenu(player, player, "listings");
      });
    }
    catch (Exception e) {
      e.printStackTrace();
      messageSender(Message.ERROR_GENERIC);
    }

  }

  @Override
  public List<String> tab() {
    return Collections.emptyList();
  }

  @Override
  public String subcommand() {
    return "listings";
  }
} 