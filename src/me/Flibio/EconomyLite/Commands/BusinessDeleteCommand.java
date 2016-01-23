package me.Flibio.EconomyLite.Commands;

import me.Flibio.EconomyLite.EconomyLite;
import me.Flibio.EconomyLite.Utils.BusinessManager;
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
import org.spongepowered.api.text.format.TextColors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

public class BusinessDeleteCommand implements CommandExecutor {
	
	private TextUtils textUtils = new TextUtils();
	private BusinessManager businessManager = new BusinessManager();private EconomyService economyService = EconomyLite.getService();
	private Currency currency = EconomyLite.getService().getDefaultCurrency();
	//private PlayerManager playerManager = new PlayerManager();
	private Builder taskBuilder = EconomyLite.access.game.getScheduler().createTaskBuilder();

	@Override
	public CommandResult execute(CommandSource source, CommandContext args)
			throws CommandException {
		//Run in a new thread
		taskBuilder.execute(new Runnable() {
			public void run() {
				//Make sure the source is a player
				if(!(source instanceof Player)) {
					source.sendMessage(textUtils.basicText("You must be a player to delete a business!", TextColors.RED));
					return;
				}
				
				Player player = (Player) source;
				
				//Retrieve arguments
				Optional<String> businessNameOptional = args.<String>getOne("business");
				if(businessNameOptional.isPresent()) {
					String businessName = businessNameOptional.get();
					//Check if the business already exists
					if(businessManager.businessExists(businessName)) {
						//Check if the player is an owner
						if(businessManager.ownerExists(businessName, player.getUniqueId().toString())) {
							String correctName = businessManager.getCorrectBusinessName(businessName);
							//Check if the business needs confirmation
							if(businessManager.confirmationNeeded(businessName)) {
								//Tell user that the business needs confirmation
								businessManager.setConfirmationNeeded(businessName, false);
								//Expire in 1 minute
								Thread expireThread = new Thread(new Runnable(){
									public void run() {
										try{
											Thread.sleep(60000);
											businessManager.setConfirmationNeeded(businessName, true);
										} catch(InterruptedException e) {
											businessManager.setConfirmationNeeded(businessName, true);
										}
									}
								});
								expireThread.start();
								player.sendMessage(textUtils.aboutToDelete(correctName));
								player.sendMessage(textUtils.clickToContinue("/business delete "+businessName));
								return;
							} else {
								//Get balance
								int balance = businessManager.getBusinessBalance(businessName);
								if(balance<0) {
									//Error occured
									player.sendMessage(textUtils.basicText("An internal error has occured!", TextColors.RED));
									return;
								}
								int eachGet = (int) Math.floor(balance/businessManager.getBusinessOwners(businessName).size());
								ArrayList<String> owners = businessManager.getBusinessOwners(businessName);
								//Try to delete business
								if(businessManager.deleteBusiness(businessName)) {
									//Success
									player.sendMessage(textUtils.deleteSuccess(correctName));
									//Distribute funds to all owners
									for(String uuid : owners) {
										Optional<UniqueAccount> uOpt = economyService.getAccount(UUID.fromString(uuid));
										if(!uOpt.isPresent()) {
											//Account is not present
											source.sendMessage(textUtils.basicText("An internal error has occured!", TextColors.RED));
											return;
										} else {
											UniqueAccount account = uOpt.get();
											account.deposit(currency, BigDecimal.valueOf(eachGet), Cause.of("EconomyLite"));
										}
									}
									return;
								} else {
									//Error occured
									player.sendMessage(textUtils.basicText("An internal error has occured!", TextColors.RED));
									return;
								}
							}
						} else {
							//Player doesn't have permission
							player.sendMessage(textUtils.basicText("You don't have permission to delete that business!", TextColors.RED));
							return;
						}
					} else {
						//Business doesn't exist
						player.sendMessage(textUtils.basicText("That business could not be found!", TextColors.RED));
						return;
					}
				} else {
					//Send error message
					player.sendMessage(textUtils.basicText("An internal error has occured!", TextColors.RED));
					return;
				}
			}
		}).async().submit(EconomyLite.access);
		return CommandResult.success();
	}

}