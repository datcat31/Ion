package net.horizonsend.ion.server.database.schema.starships

import net.starlegacy.database.DbObject
import net.starlegacy.database.Oid
import net.starlegacy.database.OidDbObjectCompanion
import net.starlegacy.database.schema.misc.SLPlayerId
import net.starlegacy.feature.starship.StarshipType
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.litote.kmongo.*

/**
 * This can either represent an unpiloted ship, which is stored in database,
 * or a piloted ship, which is only stored in memory.
 *
 * In general this should be cached and only one instance should exist per ship, even if it is piloted and
 * not currently in the database.
 */
data class StarshipData(
	override val _id: Oid<StarshipData>,
	/** Player UUID of the captain of the ship */
	var captain: SLPlayerId,

	var starshipType: StarshipType,

	var serverName: String?,
	var levelName: String,
	var blockKey: Long,
	var npcId: Int,
	/** UUIDs of players who have been added to the ship by the captain. Should never include the captain. */
	val pilots: MutableSet<SLPlayerId> = mutableSetOf(),
	val isNpc: Boolean = false,
	var name: String? = null,
	/** Chunk combined coordinates, of each chunk the detected blocks reside in */
	var containedChunks: Set<Long>? = null,

	var lastUsed: Long = System.currentTimeMillis(),
	var isLockEnabled: Boolean = false,
) : DbObject {
	companion object : OidDbObjectCompanion<StarshipData>(StarshipData::class, setup = {
		ensureIndex(StarshipData::captain)
		ensureIndex(StarshipData::isNpc)
		ensureIndex(StarshipData::npcId)
		ensureIndex(StarshipData::name)
		ensureIndex(StarshipData::serverName)
		ensureIndex(StarshipData::levelName)
		ensureUniqueIndex(StarshipData::levelName, StarshipData::blockKey)
	}) {
		const val LOCK_TIME_MS = 1_000 * 300

		fun add(data: StarshipData) {
			col.insertOne(data)
		}

		fun remove(dataId: Oid<StarshipData>) {
			col.deleteOneById(dataId)
		}


	}

	fun bukkitWorld(): World = requireNotNull(Bukkit.getWorld(levelName)) {
		"World $levelName is not loaded, but tried getting it for computer $_id"
	}


	/** assumes that it's also deactivated */
	fun isLockActive(): Boolean {
		return isLockEnabled && System.currentTimeMillis() - lastUsed >= LOCK_TIME_MS
	}
	fun isPilot(pCheck: Player): Boolean {
		for(pilot in pilots)
		if (!isNpc){
			if( pilot == pCheck) {
				return true
			}
		}
		return false
	}
}
