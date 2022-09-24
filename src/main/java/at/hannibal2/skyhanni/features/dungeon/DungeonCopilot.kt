package at.hannibal2.skyhanni.features.dungeon

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.events.*
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.matchRegex
import at.hannibal2.skyhanni.utils.RenderUtils.renderString
import net.minecraft.client.Minecraft
import net.minecraft.entity.item.EntityArmorStand
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class DungeonCopilot {

    var nextStep = ""
    var searchForKey = false

    @SubscribeEvent
    fun onChatMessage(event: LorenzChatEvent) {
        if (!LorenzUtils.inDungeons) return

        val message = event.message

        if (message.matchRegex("(.*) has started the dungeon countdown. The dungeon will begin in 1 minute.")) {
            changeNextStep("Ready up")
        }
        if (message.endsWith("§a is now ready!")) {
            var name = Minecraft.getMinecraft().thePlayer.name
            if (message.contains(name)) {
                changeNextStep("Wait for the dungeon to start!")
            }
        }

        var foundKeyOrDoor = false

        //key pickup
        if (message.matchRegex("(.*) §r§ehas obtained §r§a§r§6§r§8Wither Key§r§e!") ||
            message == "§eA §r§a§r§6§r§8Wither Key§r§e was picked up!"
        ) {
            changeNextStep("Open Wither Door")
            foundKeyOrDoor = true

        }
        if (message.matchRegex("(.*) §r§ehas obtained §r§a§r§c§r§cBlood Key§r§e!") ||
            message == "§eA §r§a§r§c§r§cBlood Key§r§e was picked up!"
        ) {
            changeNextStep("Open Blood Door")
            foundKeyOrDoor = true
        }


        if (message.matchRegex("(.*) opened a §r§8§lWITHER §r§adoor!")) {
            changeNextStep("Clear next room")
            searchForKey = true
            foundKeyOrDoor = true
        }

        if (message == "§cThe §r§c§lBLOOD DOOR§r§c has been opened!") {
            changeNextStep("Wait for Blood Room to fully spawn")
            foundKeyOrDoor = true
        }

        if (foundKeyOrDoor && SkyHanniMod.feature.dungeon.messageFilterKeysAndDoors) {
            event.blockedReason = "dungeon_keys_and_doors"
        }


        if (message == "§c[BOSS] The Watcher§r§f: That will be enough for now.") {
            changeNextStep("Clear Blood Room")
        }

        if (message == "§c[BOSS] The Watcher§r§f: You have proven yourself. You may pass.") {
            changeNextStep("Enter Boss Room")
        }
    }

    private fun changeNextStep(step: String) {
        nextStep = step
    }

    @SubscribeEvent
    fun onCheckRender(event: CheckRenderEntityEvent<*>) {
        if (!LorenzUtils.inDungeons) return

        val entity = event.entity
        if (entity !is EntityArmorStand) return

        if (!searchForKey) return

        if (entity.name == "§6§8Wither Key") {
            changeNextStep("Pick up Wither Key")
            searchForKey = false
        }
        if (entity.name == "§c§cBlood Key") {
            changeNextStep("Pick up Blood Key")
            searchForKey = false
        }
    }

    @SubscribeEvent
    fun onDungeonStart(event: DungeonStartEvent) {
        changeNextStep("Clear first room")
        searchForKey = true
    }

    @SubscribeEvent
    fun onDungeonStart(event: DungeonEnterEvent) {
        changeNextStep("Talk to Mort")
        searchForKey = true
    }

    @SubscribeEvent
    fun onDungeonBossRoomEnter(event: DungeonBossRoomEnterEvent) {
        changeNextStep("Defeat the boss! Good luck :)")
    }

    @SubscribeEvent
    fun onWorldChange(event: WorldEvent.Load) {
        changeNextStep("")
    }

    private fun isEnabled(): Boolean {
        return LorenzUtils.inDungeons && SkyHanniMod.feature.dungeon.copilotEnabled
    }

    @SubscribeEvent
    fun renderOverlay(event: RenderGameOverlayEvent.Post) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return
        if (!LorenzUtils.inDungeons) return
        if (!SkyHanniMod.feature.dungeon.copilotEnabled) return

        SkyHanniMod.feature.dungeon.copilotPos.renderString(nextStep)
    }
}