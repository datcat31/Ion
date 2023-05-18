package net.starlegacy.command.starship

import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Optional
import co.aikar.commands.annotation.Subcommand
import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.world.block.BlockState
import net.horizonsend.ion.common.extensions.success
import net.horizonsend.ion.common.extensions.userError
import net.horizonsend.ion.server.features.starship.Starship
import net.horizonsend.ion.server.features.starship.active.ActiveEntityStarship
import net.horizonsend.ion.server.legacy.ShipFactoryMaterialCosts
import net.minecraft.world.level.block.BaseEntityBlock
import net.starlegacy.cache.nations.NationCache
import net.starlegacy.command.SLCommand
import net.starlegacy.database.schema.starships.Blueprint
import net.starlegacy.database.slPlayerId
import net.starlegacy.feature.nations.gui.playerClicker
import net.starlegacy.feature.progression.Levels
import net.starlegacy.feature.starship.DeactivatedPlayerStarships
import net.starlegacy.feature.starship.PilotedStarships
import net.starlegacy.feature.starship.StarshipComputers
import net.starlegacy.feature.starship.StarshipDetection
import net.starlegacy.feature.starship.StarshipSchematic
import net.starlegacy.feature.starship.StarshipType
import net.starlegacy.feature.starship.active.ActiveStarship
import net.starlegacy.feature.starship.factory.PrintItem
import net.starlegacy.feature.starship.factory.StarshipFactories
import net.starlegacy.util.MenuHelper
import net.starlegacy.util.Notify
import net.starlegacy.util.Tasks
import net.starlegacy.util.Vec3i
import net.starlegacy.util.isAlphanumeric
import net.starlegacy.util.nms
import net.starlegacy.util.placeSchematicEfficiently
import net.starlegacy.util.toBukkitBlockData
import org.bukkit.Material
import org.bukkit.entity.Player
import org.litote.kmongo.and
import org.litote.kmongo.descendingSort
import org.litote.kmongo.eq
import org.litote.kmongo.save
import java.util.*
import kotlin.collections.set

@CommandAlias("blueprint")
object BlueprintCommand : SLCommand() {

	private fun getMaxBlueprints(player: Player): Int {
		return Levels[player] * 3 + 20
	}

	private fun validateName(name: String) {
		failIf(name != name.lowercase(Locale.getDefault())) {
			"Name must be lowercase"
		}
		failIf(name.length !in 2..50) {
			"Name length is ${name.length}, must be between 2 and 50"
		}
		failIf(!name.replace('-', ' ').replace('_', ' ').isAlphanumeric()) {
			"Name must only contain letters, numbers, and - or _"
		}
	}

	@Subcommand("save")
	fun onSave(sender: Player, name: String, @Optional confirm: String?) = asyncCommand(sender) {
		val createNew = Blueprint.none(and(Blueprint::owner eq sender.slPlayerId, Blueprint::name eq name))
		// TODO: confirm accept rules
		val slPlayerId = sender.slPlayerId
		val starship = getStarshipPiloting(sender)
		validateName(name)
		var pilotLoc = Vec3i(sender.location)
		failIf(!starship.isWithinHitbox(pilotLoc.x, pilotLoc.y, pilotLoc.z, 1)) {
			"Must be inside the ship."
		}

		val schem = Tasks.getSyncBlocking { StarshipSchematic.createSchematic(starship) }
		val data = Blueprint.createData(schem)

		pilotLoc = Vec3i(pilotLoc.x - schem.origin.x, pilotLoc.y - schem.origin.y, pilotLoc.z - schem.origin.z)

		if (createNew) {
			failIf(Blueprint.count(Blueprint::owner eq slPlayerId) > getMaxBlueprints(sender)) {
				"You can only have up to ${getMaxBlueprints(sender)} blueprints."
			}
			Blueprint.create(slPlayerId, name, starship.data.starshipType, pilotLoc, starship.initialBlockCount, data)
			sender.success("Saved blueprint $name")
		} else {
			val blueprint = getBlueprint(sender, name)
			blueprint.blockData = data
			blueprint.pilotLoc = pilotLoc
			blueprint.type = starship.data.starshipType
			saveBlueprint(blueprint)
			sender.success("Updated blueprint $name")
		}

		failIf(confirm != "confirm") {
			"To save a blueprint, you must acknowledge that you understand that you cannot save blueprints for any purpose of, for example, copying ship designs, or basically saving blueprints of any ships that you didn't design or get permission from the designer to save. This also applies to schematica and similar mods. To acknowledge this, do /blueprint save <name> confirm."
		}
	}

	private fun getBlueprint(sender: Player, name: String): Blueprint {
		return Blueprint.find(and(Blueprint::owner eq sender.slPlayerId, Blueprint::name eq name)).first()
			?: fail { "You don't have a blueprint named $name." }
	}

	private fun saveBlueprint(blueprint: Blueprint) {
		Blueprint.col.save(blueprint)
	}

	@Subcommand("delete")
	@CommandCompletion("@blueprints")
	fun onDelete(sender: Player, name: String) = asyncCommand(sender) {
		val blueprint = getBlueprint(sender, name)
		// TODO: confirm menu
		Blueprint.delete(blueprint._id)
		sender.success("Deleted blueprint ${blueprint.name}")
	}

	private fun blueprintInfo(blueprint: Blueprint): List<String> {
		val list = LinkedList<String>()
		val cost = calculateBlueprintCost(blueprint)
		list.add("<gray>Size<dark_gray>: <gold>${blueprint.size}")
		list.add("<gray>Cost<dark_gray>: <gold>$$cost")
		list.add("<gray>Class<dark_gray>: <light_purple>${blueprint.type}")
		if (blueprint.trustedNations.isNotEmpty()) {
			list.add("<gray>Trusted Players<dark_gray>: <aqua>${blueprint.trustedPlayers.joinToString { getPlayerName(it) }}}")
			list.add("<gray>Trusted Nations<dark_gray>: <aqua>${blueprint.trustedNations.joinToString { NationCache[it].name }}")
		}
		return list
	}

	private fun calculateBlueprintCost(blueprint: Blueprint): Int {
		val clipboard = blueprint.loadClipboard()

		return clipboard.region
			.map { clipboard.getBlock(it).toBukkitBlockData() }
			.filter { !it.material.isAir }
			.sumOf { ShipFactoryMaterialCosts.getPrice(it) }
			.toInt()
	}

	@Suppress("Unused")
	@Subcommand("list")
	fun onList(sender: Player) = asyncCommand(sender) {
		val slPlayerId = sender.slPlayerId
		val blueprints: List<Blueprint> = Blueprint
			.find(Blueprint::owner eq slPlayerId)
			.descendingSort(Blueprint::size)
			.toList()
		failIf(blueprints.isEmpty()) {
			"You have no blueprints"
		}
		MenuHelper.apply {
			val items: List<GuiItem> = blueprints.map { blueprint ->
				guiButton(blueprint.type.menuItem) {
					playerClicker.closeInventory()
					Tasks.async { showMaterials(playerClicker, blueprint) }
				}.setName(blueprint.name).setRichLore(blueprintInfo(blueprint))
			}
			Tasks.sync {
				sender.openPaginatedMenu("Your Blueprints", items)
			}
		}
	}

	@Suppress("Unused")
	@Subcommand("info")
	@CommandCompletion("@blueprints")
	fun onInfo(sender: Player, name: String) = asyncCommand(sender) {
		val blueprint = getBlueprint(sender, name)
		sender.sendRichMessage(blueprintInfo(blueprint).joinToString("\n"))
	}

	@Suppress("Unused")
	@Subcommand("materials")
	@CommandCompletion("@blueprints")
	fun onMaterials(sender: Player, name: String) = asyncCommand(sender) {
		val blueprint = getBlueprint(sender, name)
		showMaterials(sender, blueprint)
	}

	@Suppress("Unused")
	@Subcommand("load")
	@CommandPermission("starships.blueprint.load")
	@CommandCompletion("@blueprints")
	fun onLoad(sender: Player, name: String) = asyncCommand(sender) {
		val blueprint = getBlueprint(sender, name)
		val schematic: Clipboard = blueprint.loadClipboard()
		val pilotLoc = blueprint.pilotLoc

		Tasks.syncBlocking {
			checkObstruction(sender, schematic, pilotLoc)

			loadSchematic(sender, schematic, pilotLoc) { origin ->
				tryPilot(sender, origin, blueprint.type, blueprint.name)
			}
		}
	}

	@Suppress("Unused")
	@Subcommand("fix")
	@CommandPermission("starships.blueprint.load")
	@CommandCompletion("@blueprints")
	fun onFix(sender: Player, name: String) = asyncCommand(sender) {
		val blueprint = getBlueprint(sender, name)
		val schematic: Clipboard = blueprint.loadClipboard()
		val pilotLoc = blueprint.pilotLoc

		Tasks.syncBlocking {
			checkObstruction(sender, schematic, pilotLoc)

			loadSchematic(sender, schematic, pilotLoc) { origin ->
				tryPilot(sender, origin, blueprint.type, blueprint.name) { starship: ActiveStarship ->

					starship.iterateBlocks { x, y, z ->
						val block = starship.serverLevel.world.getBlockAt(x, y, z)
						val blockData = block.blockData
						if (blockData.nms.block is BaseEntityBlock) {
							return@iterateBlocks
						}
						block.setType(Material.AIR, false)
						block.setBlockData(blockData, true)
					}
					onSave(sender, name, "confirm")
				}
			}
		}
	}

	fun checkObstruction(sender: Player, schematic: Clipboard, pilotLoc: Vec3i) {
		val world = BukkitAdapter.adapt(sender.world)
		val vec: BlockVector3 = getPasteVector(sender, pilotLoc)
		val region = schematic.region.clone()
		val offset = vec.subtract(schematic.origin)
		for (point in region) {
			failIf(!isAir(schematic.getBlock(point)) && !isAir(world.getBlock(point.add(offset)))) {
				"Obstructed at $point"
			}
		}
	}

	fun loadSchematic(sender: Player, schematic: Clipboard, pilotLoc: Vec3i, callback: (Vec3i) -> Unit = {}) {
		val vec: BlockVector3 = getPasteVector(sender, pilotLoc)
		val vec3i = Vec3i(vec.blockX, vec.blockY, vec.blockZ)

		placeSchematicEfficiently(schematic, sender.world, vec3i, true) {
			callback(vec3i)
		}
	}

	private fun getPasteVector(sender: Player, pilotLoc: Vec3i): BlockVector3 {
		val playerLocation = sender.location

		return BukkitAdapter.asVector(playerLocation).toBlockPoint()
			.subtract(BlockVector3.at(pilotLoc.x, pilotLoc.y, pilotLoc.z))
	}

	fun tryPilot(
		sender: Player,
		origin: Vec3i,
		type: StarshipType,
		name: String,
		callback: (ActiveEntityStarship) -> Unit = {}
	) {
		val block = sender.world.getBlockAtKey(origin.toBlockKey())

		if (block.type != StarshipComputers.COMPUTER_TYPE) {
			sender.userError("${block.type} at $origin was not a starship computer, failed to pilot")
			return
		}

		DeactivatedPlayerStarships.createAsync(block.world, block.x, block.y, block.z, sender.uniqueId, name) { data ->
			Tasks.async {
				try {
					DeactivatedPlayerStarships.updateType(data, type)
					val state = StarshipDetection.detectNewState(data)
					DeactivatedPlayerStarships.updateState(data, state)
					Tasks.sync {
						PilotedStarships.tryPilot(sender, data) {
							callback(it)
						}
					}
				} catch (e: StarshipDetection.DetectionFailedException) {
					sender.userError("Detection failed: ${e.message}")
				}
			}
		}
	}

	private fun isAir(state: BlockState?) = state?.blockType?.material?.isAir != false

	private fun showMaterials(sender: Player, blueprint: Blueprint) {
		val clipboard = blueprint.loadClipboard()

		val map = mutableMapOf<PrintItem, Int>()

		for (vec in clipboard.region) {
			val state = clipboard.getBlock(vec) ?: continue
			val blockData = state.toBukkitBlockData()

			if (blockData.material.isAir) {
				continue
			}

			val printItem = PrintItem[blockData] ?: continue
			val amount = StarshipFactories.getRequiredAmount(blockData)

			map[printItem] = map.getOrDefault(printItem, 0) + amount
		}

		sender.sendRichMessage(StarshipFactories.getPrintItemCountString(map))
	}

	@Suppress("Unused")
	@Subcommand("trust player")
	@CommandCompletion("@blueprints @players")
	fun onTrustPlayer(sender: Player, name: String, player: String) {
		val blueprint = getBlueprint(sender, name)
		val playerId: UUID = resolveOfflinePlayer(player)
		val slPlayerId = playerId.slPlayerId
		failIf(blueprint.trustedPlayers.contains(slPlayerId)) {
			"$player is already trusted, you might be looking for /blueprint untrust player $name $player"
		}
		blueprint.trustedPlayers.add(slPlayerId)
		saveBlueprint(blueprint)
		Notify.player(playerId, "&b${sender.name} &7trusted you to their blueprint &b$name")
		sender.success("Trusted $player to blueprint $name")
	}

	@Suppress("Unused")
	@Subcommand("untrust player")
	@CommandCompletion("@blueprints @players")
	fun onUntrustPlayer(sender: Player, name: String, player: String) {
		val blueprint = getBlueprint(sender, name)
		val playerId: UUID = resolveOfflinePlayer(player)
		val slPlayerId = playerId.slPlayerId
		failIf(!blueprint.trustedPlayers.contains(slPlayerId)) {
			"$player is not trusted, you might be looking for /blueprint trust player $name $player"
		}
		blueprint.trustedPlayers.remove(slPlayerId)
		saveBlueprint(blueprint)
		Notify.player(playerId, "&b${sender.name} &7un-trusted you from their blueprint &b$name")
		sender.success("Un-trusted $player from blueprint $name")
	}

	@Suppress("Unused")
	@Subcommand("trust nation")
	@CommandCompletion("@blueprints @nations")
	fun onTrustNation(sender: Player, name: String, nation: String) {
		val blueprint = getBlueprint(sender, name)
		val nationId = resolveNation(nation)
		failIf(blueprint.trustedNations.contains(nationId)) {
			"$nation is already trusted, you might be looking for /blueprint untrust nation $name $nation"
		}
		blueprint.trustedNations.add(nationId)
		saveBlueprint(blueprint)
		sender.success("Trusted nation $nation to blueprint $name")
	}

	@Suppress("Unused")
	@Subcommand("untrust nation")
	@CommandCompletion("@blueprints @nations")
	fun onUntrustNation(sender: Player, name: String, nation: String) {
		val blueprint = getBlueprint(sender, name)
		val nationId = resolveNation(nation)
		failIf(!blueprint.trustedNations.contains(nationId)) {
			"$nation is not trusted, you might be looking for /blueprint trust nation $name $nation"
		}
		blueprint.trustedNations.remove(nationId)
		saveBlueprint(blueprint)
		sender.success("Un-trusted nation $nation from blueprint $name")
	}
}
