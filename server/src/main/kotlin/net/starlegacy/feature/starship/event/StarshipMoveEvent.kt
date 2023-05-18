package net.starlegacy.feature.starship.event

import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import net.starlegacy.feature.starship.movement.StarshipMovement
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList

abstract class StarshipMoveEvent(
	val ship: ActiveEntityStarship,
	val player: Player,
	open val movement: StarshipMovement<Any?>
) : EntityStarshipEvent(ship), Cancellable {
	private var cancelled: Boolean = false

	override fun getHandlers(): HandlerList {
		return handlerList
	}

	override fun isCancelled(): Boolean {
		return cancelled
	}

	override fun setCancelled(cancelled: Boolean) {
		this.cancelled = cancelled
	}

	companion object {
		@JvmStatic
		val handlerList = HandlerList()
	}
}
