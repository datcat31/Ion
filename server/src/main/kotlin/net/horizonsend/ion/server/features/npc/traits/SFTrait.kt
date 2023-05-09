package net.horizonsend.ion.server.features.npc.traits

import net.citizensnpcs.api.trait.Trait
import net.citizensnpcs.api.trait.TraitName
import net.horizonsend.ion.server.features.npc.Checks
import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.feature.starship.movement.RotationMovement
import net.starlegacy.feature.starship.movement.TranslateMovement
import net.starlegacy.util.isInRange
import net.starlegacy.util.squared
import net.starlegacy.util.toLocation
import kotlin.math.atan
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt


@TraitName("SFTrait")
class SFTrait(var starship: ActiveEntityStarship) : Trait("SFTrait") {
	override fun run() {
		npc = getNPC()
		starship = npc.data().get("starship")
		for (activeStarship in ActiveStarships.all()){
			if(activeStarship.serverLevel.world == starship.serverLevel.world){
				if(activeStarship.centerOfMass.toLocation(activeStarship.serverLevel.world)
					.isInRange(starship.centerOfMass.toLocation(starship.serverLevel.world), 200.0)) {
					val range = Checks.getMoveRange(starship)
					val dist = min(range, Checks.getDist(activeStarship.centerOfMass.x.toDouble(),activeStarship.centerOfMass.y.toDouble(),activeStarship.centerOfMass.z.toDouble()).toInt())
					val angle: Float = atan((
							(starship.centerOfMass.y - activeStarship.centerOfMass.y)
							/
							(starship.centerOfMass.x/activeStarship.centerOfMass.x))
						.toFloat()- 90F)
					val dx = dist * (sin(angle)/ sin(90.0))
					val dz = sqrt(dist.squared()* dx.squared())
					starship.moveAsync(TranslateMovement(starship,dx.toInt(),0,dz.toInt()))
					if(angle > 45){
						starship.moveAsync(RotationMovement(starship, true))
					}else if(angle < -45){
						starship.moveAsync(RotationMovement(starship, false))
					}

				}
			}
		}
	}
	override fun onAttach() {

	}
	override fun onSpawn() {

	}
	override fun onDespawn() {

	}
	override fun onRemove() {

	}
}
