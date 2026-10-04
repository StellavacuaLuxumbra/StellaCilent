-- KillAura module for Stella Client
-- C++ brain: targets nearest player, rotates and attacks

KillAura = {}

KillAura.settings = {
    range = 4.0,
    cps = 10,
    playersOnly = true,
}

KillAura.lastAttackTime = 0

function KillAura.tick()
    if not world or not world.player then return end
    if not world.entities then return end

    local player = world.player
    local settings = KillAura.settings
    local now = world.time
    local attackInterval = math.floor(20 / settings.cps)

    if now - KillAura.lastAttackTime < attackInterval then
        return
    end

    local bestTarget = nil
    local bestDist = settings.range + 1

    for _, entity in ipairs(world.entities) do
        if settings.playersOnly and not entity.isPlayer then
            -- skip non-players
        else
            local dx = entity.x - player.x
            local dy = entity.y - player.y
            local dz = entity.z - player.z
            local dist = math.sqrt(dx * dx + dy * dy + dz * dz)

            if dist <= settings.range and dist < bestDist then
                if entity.health > 0 then
                    bestDist = dist
                    bestTarget = entity
                end
            end
        end
    end

    if bestTarget then
        local eyeHeight = 1.62
        local aimY = bestTarget.y + eyeHeight * 0.75
        look_at(bestTarget.x, aimY, bestTarget.z)
        attack(bestTarget.id)
        swing_hand(0)
        KillAura.lastAttackTime = now
    end
end
