package at.hannibal2.skyhanni.features.garden.visitor

import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.features.garden.GardenAPI
import at.hannibal2.skyhanni.features.garden.visitor.VisitorAPI.ACCEPT_SLOT
import at.hannibal2.skyhanni.features.garden.visitor.VisitorAPI.REFUSE_SLOT
import at.hannibal2.skyhanni.features.garden.visitor.VisitorAPI.VisitorBlockReason
import at.hannibal2.skyhanni.features.garden.visitor.VisitorAPI.lastClickedNpc
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.KeyboardManager
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.NumberUtil
import at.hannibal2.skyhanni.utils.RenderUtils.drawBorder
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import net.minecraft.inventory.Slot
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.absoluteValue

class VisitorRewardWarning {
    private val config get() = VisitorAPI.config.rewardWarning

    @SubscribeEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.ForegroundDrawnEvent) {
        if (!VisitorAPI.inInventory) return

        val visitor = VisitorAPI.getVisitor(lastClickedNpc) ?: return
        val refuseOfferSlot = event.gui.inventorySlots.getSlot(REFUSE_SLOT)
        val acceptOfferSlot = event.gui.inventorySlots.getSlot(ACCEPT_SLOT)
        val blockReason = visitor.blockReason ?: return

        if (blockReason.blockRefusing) {
            renderColor(refuseOfferSlot, acceptOfferSlot, LorenzColor.GREEN)
        } else {
            renderColor(acceptOfferSlot, refuseOfferSlot, LorenzColor.RED)
        }
    }

    private fun renderColor(backgroundSlot: Slot?, outlineSlot: Slot?, outlineColor: LorenzColor) {
        if (!config.bypassKey.isKeyHeld() && backgroundSlot != null) {
            backgroundSlot highlight LorenzColor.DARK_GRAY.addOpacity(config.opacity)
        }
        if (config.optionOutline && outlineSlot != null) {
            outlineSlot drawBorder outlineColor.addOpacity(200)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onStackClick(event: GuiContainerEvent.SlotClickEvent) {
        if (!VisitorAPI.inInventory) return
        val stack = event.slot?.stack ?: return

        val visitor = VisitorAPI.getVisitor(lastClickedNpc) ?: return
        val blockReason = visitor.blockReason

        val isRefuseSlot = stack.name == "§cRefuse Offer"
        val isAcceptSlot = stack.name == "§aAccept Offer"

        val shouldBlock = blockReason?.run { blockRefusing && isRefuseSlot || !blockRefusing && isAcceptSlot } ?: false
        if (!config.bypassKey.isKeyHeld() && shouldBlock) {
            event.isCanceled = true
            return
        }

        // clicktypes 0, 2, 3, and 4 work for interacting with visitor, but not 1
        if (event.clickTypeEnum == GuiContainerEvent.ClickType.NORMAL) return
        if (isRefuseSlot) {
            VisitorAPI.changeStatus(visitor, VisitorAPI.VisitorStatus.REFUSED, "refused")
            return
        }
        if (isAcceptSlot) {
            if (stack.name != "§eClick to give!") return
            VisitorAPI.changeStatus(visitor, VisitorAPI.VisitorStatus.ACCEPTED, "accepted")
            return
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onTooltip(event: ItemTooltipEvent) {
        if (!GardenAPI.onBarnPlot) return
        if (!VisitorAPI.inInventory) return
        val visitor = VisitorAPI.getVisitor(lastClickedNpc) ?: return
        if (config.bypassKey.isKeyHeld()) return

        val isRefuseSlot = event.itemStack.name == "§cRefuse Offer"
        val isAcceptSlot = event.itemStack.name == "§aAccept Offer"

        val blockReason = visitor.blockReason ?: return
        if (blockReason.blockRefusing && !isRefuseSlot) return
        if (!blockReason.blockRefusing && !isAcceptSlot) return

        if (visitor.blockedLore.isEmpty()) {
            updateBlockedLore(event.toolTip.toList(), visitor, blockReason)
        }
        event.toolTip.clear()
        event.toolTip.addAll(visitor.blockedLore)
    }

    private fun updateBlockedLore(
        copiedTooltip: List<String>,
        visitor: VisitorAPI.Visitor,
        blockReason: VisitorBlockReason,
    ) {
        val blockedToolTip = mutableListOf<String>()
        for (line in copiedTooltip) {
            if (line.contains("§aAccept Offer§r")) {
                blockedToolTip.add(line.replace("§aAccept Offer§r", "§7Accept Offer§8"))
            } else if (line.contains("§cRefuse Offer§r")) {
                blockedToolTip.add(line.replace("§cRefuse Offer§r", "§7Refuse Offer§8"))
            } else if (!line.contains("minecraft:") && !line.contains("NBT:")) {
                blockedToolTip.add("§8" + line.removeColor())
            }
        }

        blockedToolTip.add("")
        val pricePerCopper = visitor.pricePerCopper?.let { NumberUtil.format(it) }
        // TODO remove !! - best by creating new class LoadedVisitor without any nullable objects
        val loss = visitor.totalPrice!! - visitor.totalReward!!
        val formattedLoss = NumberUtil.format(loss.absoluteValue)
        blockedToolTip.add(blockReason(blockReason, pricePerCopper, loss, formattedLoss))
        blockedToolTip.add("  §7(Bypass by holding ${KeyboardManager.getKeyName(config.bypassKey)})")

        visitor.blockedLore = blockedToolTip
    }

    private fun blockReason(
        blockReason: VisitorBlockReason,
        pricePerCopper: String?,
        loss: Double,
        formattedLoss: String,
    ) = when (blockReason) {
        VisitorBlockReason.CHEAP_COPPER, VisitorBlockReason.EXPENSIVE_COPPER ->
            "${blockReason.description} §7(§6$pricePerCopper §7per)"

        VisitorBlockReason.LOW_LOSS, VisitorBlockReason.HIGH_LOSS ->
            if (loss > 0)
                "${blockReason.description} §7(§6$formattedLoss §7selling §9Green Thumb I§7)"
            else
                "§7(§6$formattedLoss §7profit §7selling §9Green Thumb I§7)"

        else -> blockReason.description
    }
}
