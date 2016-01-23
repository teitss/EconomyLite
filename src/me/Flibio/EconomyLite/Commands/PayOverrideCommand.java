package me.Flibio.EconomyLite.Commands;

import me.Flibio.EconomyLite.EconomyLite;
import me.Flibio.EconomyLite.Utils.BusinessManager;
import me.Flibio.EconomyLite.Utils.PlayerManager;
import me.Flibio.EconomyLite.Utils.TextUtils;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.scheduler.Task.Builder;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.text.format.TextColors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

public class PayOverrideCommand implements CommandExecutor{
	
	private TextUtils textUtils = new TextUtils();
	private EconomyService economyService = EconomyLite.getService();
	private Currency currency = EconomyLite.getService().getDefaultCurrency();
	private PlayerManager playerManager = new PlayerManager();
	private BusinessManager businessManager = new BusinessManager();
	private Builder taskBuilder = EconomyLite.access.game.getScheduler().createTaskBuilder();
	
	@Override
	public CommandResult execute(CommandSource source, CommandContext args)
			throws CommandException {
		//Run in a seperate thread
		taskBuilder.execute(new Runnable() {
			public void run() {
				//Make sure the source is a player
				if(!(source instanceof Player)) {
					source.sendMessage(textUtils.basicText("You must be a player to use /pay!", TextColors.RED));
					return;
				}
				
				Player player = (Player) source;
				
				String uuid = player.getUniqueId().toString();
				if(!playerManager.playerExists(uuid)) {
					player.sendMessage(textUtils.basicText("An internal error has occurred!", TextColors.RED));
					return;
				}
				
				Optional<String> rawWhoType = args.<String>getOne("whoType");
				Optional<Integer> rawAmount = args.<Integer>getOne("amount");
				Optional<String> rawWho = args.<String>getOne("who");
				if(rawWhoType.isPresent()&&rawWho.isPresent()&&rawAmount.isPresent()) {
					//Both parameters are present
					String whoType = rawWhoType.get();
					int amount = rawAmount.get();
					String who = rawWho.get();
					
					if(whoType.equalsIgnoreCase("player")) {
						//Who is a player
						if(who.equalsIgnoreCase(player.getName())) {
							player.sendMessage(textUtils.basicText("Why would you pay yourself!?", TextColors.RED));
							return;
						}
						String playerName = who;
						String playerUUID = playerManager.getUUID(playerName);
						if(playerManager.playerExists(playerUUID)) {
							//Pay player
							payPlayer(uuid, amount, player, playerName, playerUUID);
							return;
						} else {
							//Player not found
							player.sendMessage(textUtils.basicText("That player could not be found!", TextColors.RED));
							return;
						}
					} else if(whoType.equalsIgnoreCase("business")) {
						//Who is a business
						String businessName = who;
						if(businessManager.businessExists(businessName)) {
							//Pay business
							payBusiness(uuid, amount, player, businessName);
							return;
						} else {
							//Business not found
							player.sendMessage(textUtils.basicText("That business could not be found!", TextColors.RED));
							return;
						}
					} else {
						//An error occured
						player.sendMessage(textUtils.basicText("An internal error has occured!", TextColors.RED));
						return;
					}
				} else {
					//An error occured
					player.sendMessage(textUtils.basicText("An internal error has occured!pp", TextColors.RED));
					return;
				}

			}
		}).async().submit(EconomyLite.access);
		return CommandResult.success();
	}
	
	private void payBusiness(String uuid, int amount, Player player, String businessName) {
		UniqueAccount account = economyService.getAccount(UUID.fromString(uuid)).get();
		int balance = account.getBalance(currency).setScale(0, RoundingMode.HALF_UP).intValue();
		//Check for an error
		if(balance>-1) {
			//Check if the player has enough money
			if(amount>balance) {
				//Player doesn't have enough funds
				player.sendMessage(textUtils.basicText("You don't have enough money to pay!", TextColors.RED));
				return;
			} else {
				//Check if the new balance is within parameters
				int newBalance = businessManager.getBusinessBalance(businessName) + amount;
				if(newBalance<0||newBalance>1000000) {
					//Out of range
					player.sendMessage(textUtils.basicText("The new balance must be in-between 0 and 1,000,000 "+EconomyLite.access.currencyPlural+"!", TextColors.RED));
					return;
				} else {
					//Process transaction
					if(account.withdraw(currency,BigDecimal.valueOf(amount),Cause.of("EconomyLite")).getResult().equals(ResultType.SUCCESS)&&
							businessManager.setBusinessBalance(businessName, newBalance)) {
						//Success
						player.sendMessage(textUtils.paySuccess(businessName, amount));
						return;
					} else {
						//Error
						player.sendMessage(textUtils.basicText("An internal error has occurred!", TextColors.RED));
						return;
					}
				}
			}
		} else {
			//Error
			player.sendMessage(textUtils.basicText("An internal error has occurred!", TextColors.RED));
			return;
		}
	}
	
	private void payPlayer(String uuid, int amount, Player player, String playerName, String targetUUID) {
		UniqueAccount account = economyService.getAccount(UUID.fromString(uuid)).get();
		UniqueAccount targetAccount = economyService.getAccount(UUID.fromString(targetUUID)).get();
		int balance = account.getBalance(currency).setScale(0, RoundingMode.HALF_UP).intValue();
		//Check for an error
		if(balance>-1) {
			//Check if the player has enough money
			if(amount>balance) {
				//Player doesn't have enough funds
				player.sendMessage(textUtils.basicText("You don't have enough money to pay!", TextColors.RED));
				return;
			} else {
				//Check if the new balance is within parameters
				int newBalance = targetAccount.getBalance(currency).setScale(0, RoundingMode.HALF_UP).intValue() + amount;
				if(newBalance<0||newBalance>1000000) {
					//Out of range
					player.sendMessage(textUtils.basicText("The new balance must be in-between 0 and 1,000,000 "+EconomyLite.access.currencyPlural+"!", TextColors.RED));
					return;
				} else {
					//Process transaction
					if(account.withdraw(currency,BigDecimal.valueOf(amount),Cause.of("EconomyLite")).getResult().equals(ResultType.SUCCESS)&&
							targetAccount.setBalance(currency,BigDecimal.valueOf(newBalance),Cause.of("EconomyLite")).getResult().equals(ResultType.SUCCESS)) {
						//Success
						player.sendMessage(textUtils.paySuccess(playerName, amount));
						return;
					} else {
						//Error
						player.sendMessage(textUtils.basicText("An internal error has occurred!", TextColors.RED));
						return;
					}
				}
			}
		} else {
			//Error
			player.sendMessage(textUtils.basicText("An internal error has occurred!", TextColors.RED));
			return;
		}
	}
	
}