package net.starlegacy.feature.starship.event

import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList

class StarshipStopCruisingEvent(
	ship: ActiveEntityStarship,
	val player: Player
) : EntityStarshipEvent(ship) {
	override fun getHandlers(): HandlerList {
		return handlerList
	}

	companion object {
		@JvmStatic
		val handlerList = HandlerList()
	}
}
