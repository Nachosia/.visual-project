package com.visualproject.client.hud.target

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult

internal object TargetHudTargeting {

    fun currentTarget(client: Minecraft): Player? {
        val hit = client.hitResult
        if (hit !is EntityHitResult) return null

        val entity = hit.entity
        if (entity !is Player) return null
        if (!entity.isAlive) return null
        if (entity == client.player) return null

        return entity
    }
}

