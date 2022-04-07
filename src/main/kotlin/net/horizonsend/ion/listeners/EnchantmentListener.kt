package net.horizonsend.ion.listeners

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment.SILK_TOUCH
import org.bukkit.enchantments.EnchantmentOffer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.inventory.PrepareAnvilEvent

internal class EnchantmentListener: Listener {
	@EventHandler
	fun onPrepareItemEnchantEvent(event: PrepareItemEnchantEvent) {
		event.offers!![0] = EnchantmentOffer(SILK_TOUCH, 1, 120)
		event.offers!![1] = null
		event.offers!![2] = null
	}

	@EventHandler
	fun onPrepareAnvilEvent(event: PrepareAnvilEvent) {
		if (event.inventory.firstItem == null) return
		if (event.inventory.secondItem == null) return

		if (event.inventory.secondItem!!.type == Material.ENCHANTED_BOOK) {
			if (!event.inventory.secondItem!!.enchantments.containsKey(SILK_TOUCH)) event.result = null
			else {
				event.result = event.inventory.firstItem!!.clone()
				event.result!!.enchantments[SILK_TOUCH] = 1
			}
		}
	}
}