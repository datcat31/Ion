package net.horizonsend.ion.server.features.npc.event

import net.citizensnpcs.api.npc.NPC
import net.starlegacy.feature.starship.active.ActiveStarship
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class NpcUpdate(npcin: NPC, starshipin: ActiveStarship): Event() {
	override fun getHandlers(): HandlerList = NpcUpdate.handlerList

	val npc = npcin
	val starship = starshipin


	companion object {
		@JvmStatic
		val handlerList = HandlerList()
	}
}
