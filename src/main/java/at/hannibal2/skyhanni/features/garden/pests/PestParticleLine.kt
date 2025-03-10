package at.hannibal2.skyhanni.features.garden.pests

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.events.ItemClickEvent
import at.hannibal2.skyhanni.events.LorenzRenderWorldEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.features.garden.GardenAPI
import at.hannibal2.skyhanni.test.GriffinUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.DelayedRun
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzUtils.isAnyOf
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.draw3DLine_nea
import at.hannibal2.skyhanni.utils.RenderUtils.drawDynamicText
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import net.minecraft.util.EnumParticleTypes
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.time.Duration.Companion.seconds

// TODO remove this workaround once PestParticleWaypoint does work again
class PestParticleLine {
    private val config get() = SkyHanniMod.feature.garden.pests.pestWaypoint

    class ParticleLocation(val location: LorenzVec, val spawnTime: SimpleTimeMark)

    private var lastPestTrackerUse = SimpleTimeMark.farPast()
    private val locations = mutableListOf<MutableList<ParticleLocation>>()

    @SubscribeEvent
    fun onItemClick(event: ItemClickEvent) {
        if (!isEnabled()) return
        if (PestAPI.hasVacuumInHand()) {
            if (event.clickType == ClickType.LEFT_CLICK) {
                lastPestTrackerUse = SimpleTimeMark.now()
            }
        }
    }

    @SubscribeEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!isEnabled()) return
        // TODO time in config
        if (lastPestTrackerUse.passedSince() > 5.seconds) return

        if (event.type.isAnyOf(EnumParticleTypes.ENCHANTMENT_TABLE, EnumParticleTypes.VILLAGER_ANGRY)) {
            if (config.hideParticles) event.cancel()
        }

        if (event.type != EnumParticleTypes.VILLAGER_ANGRY) return
        val location = event.location

        // run in main thread to avoid concurrent errors
        DelayedRun.runNextTick {
            getCurrentList(location).add(ParticleLocation(location, SimpleTimeMark.now()))
        }
    }

    private fun getCurrentList(location: LorenzVec): MutableList<ParticleLocation> {
        locations.lastOrNull()?.let {
            val distance = it.last().location.distance(location)
            if (distance < 4) {
                return it
            }
        }

        val newList = mutableListOf<ParticleLocation>()
        locations.add(newList)
        if (locations.size == 5) {
            locations.removeAt(0)
        }
        return newList
    }

    @SubscribeEvent
    fun onRenderWorld(event: LorenzRenderWorldEvent) {
        if (!isEnabled()) return
        // TODO time in config
        if (lastPestTrackerUse.passedSince() > 10.seconds) {
            locations.clear()
            return
        }

        for (list in locations) {
            draw(event, list)
        }
    }

    private fun draw(event: LorenzRenderWorldEvent, list: List<ParticleLocation>) {
        val color = LorenzColor.YELLOW.toColor()
        for ((prev, next) in list.asSequence().zipWithNext()) {
            // TODO time in config
            if (next.spawnTime.passedSince() > 5.seconds) continue
            val location = next.location
            event.draw3DLine_nea(
                prev.location,
                location,
                color,
                3,
                false
            )
            val isVeryLast = list == locations.lastOrNull() && next == list.lastOrNull()
            if (isVeryLast) {
                val lastLocation = location.add(-0.5, -0.5, -0.5)
                event.drawWaypointFilled(lastLocation, color, beacon = true)
                event.drawDynamicText(lastLocation, "§ePest Guess", 1.3)
            }
        }
    }

    fun isEnabled() = GardenAPI.inGarden() && config.enabled
}
