package net.starlegacy.feature.starship

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.horizonsend.ion.server.IonServer
import net.starlegacy.SLComponent
import net.starlegacy.database.objId
import net.starlegacy.database.schema.misc.SLPlayerId
import net.horizonsend.ion.server.database.schema.starships.StarshipData
import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.starship.active.ActiveStarshipFactory
import net.starlegacy.feature.starship.active.ActiveStarships
import net.starlegacy.listen
import net.starlegacy.util.Tasks
import net.starlegacy.util.blockKey
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent
import org.litote.kmongo.addToSet
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import java.io.File
import java.util.UUID

object DeactivatedPlayerStarships : SLComponent() {
	private val DEACTIVATED_SHIP_WORLD_CACHES: MutableMap<World, DeactivatedShipWorldCache> = Object2ObjectOpenHashMap()

	private fun getCache(world: World) = requireNotNull(DEACTIVATED_SHIP_WORLD_CACHES[world])

	operator fun get(world: World, x: Int, y: Int, z: Int): StarshipData? {
		synchronized(lock) {
			return getCache(world)[x, y, z]
		}
	}

	fun getInChunk(chunk: Chunk): List<StarshipData> {
		synchronized(lock) {
			return getCache(chunk.world).getInChunk(chunk)
		}
	}

	fun getLockedContaining(world: World, x: Int, y: Int, z: Int): StarshipData? {
		synchronized(lock) {
			return getCache(world).getLockedContaining(x, y, z)
		}
	}

	fun createAsync(
		world: World,
		x: Int,
		y: Int,
		z: Int,
		playerId: UUID,
		name: String? = null,
		callback: (StarshipData) -> Unit
	) = Tasks.async {
		synchronized(lock) {
			require(getCache(world)[x, y, z] == null)
			val captain = playerId.slPlayerId
			val type = StarshipType.SHUTTLE
			val id = objId<StarshipData>()
			val blockKey = blockKey(x, y, z)
			val worldName = world.name
			val data = StarshipData(
				id, captain, type,
				IonServer.configuration.serverName, worldName, blockKey, npcId = -1, isNpc = false, name = name

			)
			StarshipData.add(data)
			getCache(world).add(data)

			Tasks.sync { callback(data) }
		}
	}

	fun getSavedState(data: StarshipData): PlayerStarshipState? {
		return getCache(data.bukkitWorld()).savedStateCache[data].orElse(null)
	}

	fun removeState(data: StarshipData) {
		synchronized(lock) {
			getCache(data.bukkitWorld()).removeState(data)
		}
	}

	fun updateState(data: StarshipData, state: PlayerStarshipState) {
		synchronized(lock) {
			getCache(data.bukkitWorld()).updateState(data, state)
		}
	}

	fun updateType(data: StarshipData, newType: StarshipType) {
		data.starshipType = newType

		Tasks.async {
			StarshipData.updateById(data._id, setValue(StarshipData::starshipType, newType))
		}

		// remove the current state in case the new type no longer matches the ship's state
		removeState(data)
	}

	fun updateName(data: StarshipData, newName: String?) {
		data.name = newName

		Tasks.async {
			StarshipData.updateById(data._id, setValue(StarshipData::name, newName))
		}
	}

	fun updateLockEnabled(data: StarshipData, newValue: Boolean) {
		data.isLockEnabled = newValue

		Tasks.async {
			StarshipData.updateById(data._id, setValue(StarshipData::isLockEnabled, newValue))
		}
	}

	fun addPilot(data: StarshipData, pilotID: SLPlayerId) {
		data.pilots += pilotID
		Tasks.async {
			StarshipData.updateById(data._id, addToSet(StarshipData::pilots, pilotID))
		}
	}

	override fun onEnable() {
		for (world in IonServer.server.worlds) {
			load(world)
		}

		listen<WorldLoadEvent> { event ->
			load(event.world)
		}

		listen<WorldUnloadEvent> { event ->
			DEACTIVATED_SHIP_WORLD_CACHES.remove(event.world)
		}
	}

	private fun load(world: World) {
		val cache = DeactivatedShipWorldCache(world)
		// retrieve all starship data from the database and add it to the cache
		StarshipData.find(StarshipData::levelName eq world.name).forEach { cache.add(it) }
		DEACTIVATED_SHIP_WORLD_CACHES[world] = cache
	}

	fun getSaveFile(world: World, data: StarshipData): File {
		return File(getCache(world).dataFolder, "${data._id}.dat")
	}

	private val lock = Any()

	fun activateAsync(
		data: StarshipData,
		state: PlayerStarshipState,
		carriedShips: List<StarshipData>,
		callback: (ActiveEntityStarship) -> Unit = {}
	): Unit = Tasks.async {
		synchronized(lock) {
			require(!carriedShips.contains(data)) { "Carried ships can't contain the ship itself!" }

			val world: World = data.bukkitWorld()
			val cache: DeactivatedShipWorldCache = getCache(world)

			if (cache[data.blockKey] != data) {
				return@async // probably already piloted bc they spam clicked
			}

			StarshipData.remove(data._id)

			cache.remove(data)

			val carriedShipMap = captureCarriedShips(carriedShips, cache)

			Tasks.sync {
				val starship =
					ActiveStarshipFactory.createPlayerStarship(data, state.blockMap.keys, carriedShipMap) ?: return@sync
				ActiveStarships.add(starship)
				callback.invoke(starship)
			}
		}
	}

	private fun captureCarriedShips(carriedShips: List<StarshipData>, cache: DeactivatedShipWorldCache): MutableMap<StarshipData, LongOpenHashSet> {
		val carriedShipMap = mutableMapOf<StarshipData, LongOpenHashSet>()

		for (carried: StarshipData in carriedShips) {
			StarshipData.remove(carried._id)
			cache.remove(carried)
			val state: PlayerStarshipState? = getSavedState(carried)
			val blocks = if (state == null) LongOpenHashSet(0) else LongOpenHashSet(state.blockMap.keys)
			carriedShipMap[carried] = blocks
		}

		return carriedShipMap
	}

	fun deactivateAsync(starship: ActiveEntityStarship, callback: () -> Unit = {}) {
		Tasks.checkMainThread()

		if (PilotedStarships.isPiloted(starship)) {
			PilotedStarships.unpilot(starship)
		}

		Tasks.async {
			deactivateNow(starship)
			Tasks.sync(callback)
		}
	}

	fun deactivateNow(starship: ActiveEntityStarship) {
		if (PilotedStarships.isPiloted(starship)) {
			Tasks.getSyncBlocking {
				PilotedStarships.unpilot(starship)
			}
		}

		val world: World = starship.serverLevel.world

		val carriedShipStateMap = Object2ObjectOpenHashMap<StarshipData, PlayerStarshipState>()

		val state: PlayerStarshipState = Tasks.getSyncBlocking {
			// this needs to be removed sync!
			ActiveStarships.remove(starship)

			for ((ship: StarshipData, blocks: Set<Long>) in starship.carriedShips) {
				if (!blocks.isEmpty()) {
					carriedShipStateMap[ship] = PlayerStarshipState.createFromBlocks(world, blocks)
				}
			}

			return@getSyncBlocking PlayerStarshipState.createFromActiveShip(starship)
		}

		saveDeactivatedData(world, starship, state, carriedShipStateMap)
	}

	private fun saveDeactivatedData(
		world: World,
		starship: ActiveEntityStarship,
		state: PlayerStarshipState,
		carriedShipStateMap: Object2ObjectOpenHashMap<StarshipData, PlayerStarshipState>
	) {
		synchronized(lock) {
			val cache: DeactivatedShipWorldCache = getCache(world)

			val data: StarshipData = starship.data
			data.lastUsed = System.currentTimeMillis()

			// this prevents it from being added to the chunk->saved ship cache in worldCache.add
			data.containedChunks = null
			// add to the deactivated ship world cache
			cache.add(data)
			// this sets the contained chunks to those of the provided state, and saved the state to disk
			cache.updateState(data, state)

			StarshipData.add(data)

			for (carriedData: StarshipData in starship.carriedShips.keys) {
				carriedData.containedChunks = null
				cache.add(carriedData)
				carriedShipStateMap[carriedData]?.let { carriedDataState: PlayerStarshipState ->
					cache.updateState(carriedData, carriedDataState)
				}
				StarshipData.add(carriedData)
			}
		}
	}

	fun destroyAsync(data: StarshipData, callback: () -> Unit = {}): Unit = Tasks.async {
		synchronized(lock) {
			destroy(data)

			Tasks.sync(callback)
		}
	}

	fun destroyManyAsync(datas: List<StarshipData>, callback: () -> Unit = {}): Unit = Tasks.async {
		synchronized(lock) {
			for (data in datas) {
				destroy(data)
			}

			Tasks.sync(callback)
		}
	}

	private fun destroy(data: StarshipData) {
		require(ActiveStarships[data._id] == null) { "Can't delete an active starship, but tried deleting ${data._id}" }

		val world: World = data.bukkitWorld()
		val cache: DeactivatedShipWorldCache = getCache(world)
		cache.remove(data)
		getSaveFile(world, data).delete()
		StarshipData.remove(data._id)
	}
}
