package at.lorenz.mod.events

import at.lorenz.mod.utils.LorenzUtils
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.Event

abstract class LorenzEvent: Event() {
    val eventName by lazy {
        this::class.simpleName
    }

    fun postAndCatch(): Boolean {
        return runCatching {
            MinecraftForge.EVENT_BUS.post(this)
        }.onFailure {
            it.printStackTrace()
            LorenzUtils.chat("§cLorenz Mod caught and logged an ${it::class.simpleName ?: "error"} at ${eventName}.")
        }.getOrDefault(isCanceled)
    }
}