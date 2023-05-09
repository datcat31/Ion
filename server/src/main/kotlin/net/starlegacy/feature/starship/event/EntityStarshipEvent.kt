package net.starlegacy.feature.starship.event

import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import net.horizonsend.ion.server.legacy.NewPlayerProtection.updateProtection

abstract class EntityStarshipEvent(
	override val starship: ActiveEntityStarship
) : StarshipEvent(starship) {
	init {
		starship.pilot?.player?.updateProtection()
	}
}
