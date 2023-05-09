package net.horizonsend.ion.server.features.npc.event

import net.citizensnpcs.api.CitizensAPI
import net.horizonsend.ion.server.features.npc.traits.SFTrait

object NpcInit {
	fun onEnable() {
		CitizensAPI.getTraitFactory()
			.registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(SFTrait::class.java))
	}
}
