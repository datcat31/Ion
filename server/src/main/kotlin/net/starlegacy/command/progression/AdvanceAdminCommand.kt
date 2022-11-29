package net.starlegacy.command.progression

import co.aikar.commands.ConditionFailedException
import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Description
import co.aikar.commands.annotation.Subcommand
import net.horizonsend.ion.server.legacy.feedback.FeedbackType
import net.horizonsend.ion.server.legacy.feedback.sendFeedbackMessage
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.economy.CargoCrateShipment
import net.starlegacy.database.schema.misc.SLPlayer
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.database.slPlayerId
import net.starlegacy.database.uuid
import net.starlegacy.feature.progression.Levels
import net.starlegacy.feature.progression.PlayerXPLevelCache
import net.starlegacy.feature.progression.SLXP
import net.starlegacy.util.toCreditsString
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.util.UUID
import kotlin.math.abs

/**
 * Admin only commands for manipulating player Advance data
 */
@CommandAlias("advanceadmin")
@CommandPermission("advance.admin")
object AdvanceAdminCommand : SLCommand() {
	@Suppress("Unused")
	@Subcommand("xp get")
	@CommandCompletion("@players")
	fun onXPGet(sender: CommandSender, player: String) = asyncCommand(sender) {
		val playerId: UUID = resolveOfflinePlayer(player)

		val xp: Int = SLPlayer.getXP(playerId.slPlayerId) ?: throw InvalidCommandArgument("Player not stored")

		sender.sendFeedbackMessage(FeedbackType.INFORMATION, "{0} has {1} XP", player, xp)

		Bukkit.getPlayer(playerId)?.let {
			val cached: PlayerXPLevelCache.CachedAdvancePlayer = PlayerXPLevelCache[playerId]
				?: throw ConditionFailedException("$player has no cache!")

			if (cached.xp != xp) {
				throw ConditionFailedException("$player's cached XP is ${cached.xp} instead of $xp")
			}
		}
	}

	@Suppress("Unused")
	@Subcommand("xp give")
	@CommandCompletion("@players @nothing")
	fun onXPGive(sender: CommandSender, player: String, amount: Int) = asyncCommand(sender) {
		val playerId: UUID = resolveOfflinePlayer(player)

		// If it's a negative amount, we need to make sure we're not accidentally giving them negative XP
		val oldXP: Int = PlayerXPLevelCache.fetchSLXP(playerId)
		if (oldXP + amount < 0) {
			throw InvalidCommandArgument("$player does not have ${abs(amount)} XP, only $oldXP XP")
		}

		PlayerXPLevelCache.addSLXP(playerId, amount)

		val newXP: Int = PlayerXPLevelCache.fetchSLXP(playerId)
		sender.sendFeedbackMessage(FeedbackType.SUCCESS, "Gave {0} XP to {1}. Now they have {2} XP.", amount, player, newXP)
	}

	@Suppress("Unused")
	@Subcommand("xp set")
	@CommandCompletion("@players @nothing")
	fun onXPSet(sender: CommandSender, player: String, amount: Int) = asyncCommand(sender) {
		val playerId = resolveOfflinePlayer(player)
		val oldXP = PlayerXPLevelCache.fetchSLXP(playerId)
		SLXP.setAsync(playerId, amount)
		sender.sendFeedbackMessage(FeedbackType.SUCCESS, "Changed {0}'s XP from {1} to {2}.", player, oldXP, amount)
	}

	@Suppress("Unused")
	@Subcommand("rebalance")
	@Description("Reload the levels config")
	fun onRebalance(sender: CommandSender) {
		Levels.reloadConfig()
		sender.sendFeedbackMessage(FeedbackType.SUCCESS, "Reloaded level balancing configs")
	}

	@Suppress("Unused")
	@Subcommand("level get")
	@CommandCompletion("@players")
	fun onLevelGet(sender: CommandSender, player: String) = asyncCommand(sender) {
		val playerId = resolveOfflinePlayer(player)

		val level: Int = SLPlayer.getLevel(playerId.slPlayerId) ?: throw InvalidCommandArgument("Player not stored")

		sender.sendFeedbackMessage(FeedbackType.INFORMATION, "{0}'s level is {1}", player, level)

		Bukkit.getPlayer(playerId)?.let {
			val cached = PlayerXPLevelCache[playerId] ?: throw ConditionFailedException("$player has no cache!")
			if (cached.level != level) throw ConditionFailedException("$player's cached level is ${cached.level} instead of $level")
		}
	}

	@Suppress("Unused")
	@Subcommand("level set")
	@CommandCompletion("@players @levels")
	fun onLevelSet(sender: CommandSender, player: String, level: Int) = asyncCommand(sender) {
		val playerId: UUID = resolveOfflinePlayer(player)
		val oldLevel: Int = PlayerXPLevelCache.fetchLevel(playerId)
		PlayerXPLevelCache.setLevel(playerId, level)
		sender.sendFeedbackMessage(FeedbackType.SUCCESS, "Changed {0}'s level from {1} to {2}.", player, oldLevel, level)
	}

	@Suppress("Unused")
	@Subcommand("listplayers")
	fun onListPlayers(sender: CommandSender) = asyncCommand(sender) {
		val ids: List<UUID> = SLPlayer.all().map { it._id.uuid }.toList()

		val text = ids.joinToString { id: UUID ->
			val player = Bukkit.getOfflinePlayer(id)
			val color = if (player.isOnline) "<green>" else "<gray>"
			return@joinToString "$color${player.name}"
		}

		sender.sendFeedbackMessage(FeedbackType.INFORMATION, text)
	}

	@Suppress("Unused")
	@Subcommand("scanabuse")
	fun onScanAbuse(sender: CommandSender) = asyncCommand(sender) {
		val cratesMap = mutableMapOf<SLPlayerId, Int>()
		val creditsMap = mutableMapOf<SLPlayerId, Double>()

		for (shipment in CargoCrateShipment.all()) {
			if (shipment.soldCrates > shipment.totalCrates) {
				val extraCrates = shipment.soldCrates - shipment.totalCrates
				cratesMap[shipment.player] = extraCrates + cratesMap.getOrDefault(shipment.player, 0)
				val extraCredits = shipment.crateRevenue * extraCrates
				creditsMap[shipment.player] = extraCredits + creditsMap.getOrDefault(shipment.player, 0.0)
			}
		}

		for (key in creditsMap.keys.sortedByDescending { cratesMap.getValue(it) }) {
			val extraCredits = creditsMap.getValue(key)
			val extraCrates = cratesMap.getValue(key)
			sender.sendFeedbackMessage(
				FeedbackType.INFORMATION,
				"{0} has {1} extra money from {2}",
				SLPlayer.getName(key).toString(),
				extraCredits.toCreditsString(),
				extraCrates
			)
		}
	}
}