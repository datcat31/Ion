package net.horizonsend.ion.server.features.npc

import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import net.minecraft.world.entity.player.Player
import org.bukkit.Location
import kotlin.math.pow
import kotlin.math.sqrt

class Checks {
	companion object {
		fun getMoveRange(starship: ActiveEntityStarship): Int {
			return starship.cruiseData.targetSpeed
		}
		fun getPlayerDistFromLoc(player: Player, loc: Location):Double{
			val dx = loc.x - player.x
			val dy = loc.y - player.y
			val dz = loc.z - player.z
			return getDist(dx,dy,dz)
		}
		fun getDist(x:Double, y:Double, z:Double):Double {
			return sqrt(x.pow(2)+y.pow(2)+z.pow(2))
		}
	}
}
