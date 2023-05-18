package net.horizonsend.ion.server.features.npc.tree

import net.citizensnpcs.api.ai.tree.Sequence
import net.citizensnpcs.api.npc.NPC
import net.horizonsend.ion.server.features.npc.behavior.MasterBehavior

class NPCTree {
	fun setupTree(npc: NPC) {
		npc.defaultGoalController.addGoal(Sequence.createSequence(
				MasterBehavior()
			), 1
		)
	}

}
