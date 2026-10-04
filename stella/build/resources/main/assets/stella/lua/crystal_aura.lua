-- CrystalAura module for Stella Client
-- C++ brain: attacks nearest crystal within range

CrystalAura = {}

CrystalAura.settings = {
    range = 5.0,
    minDmg = 6.0,
    antiSelf = true,
    maxSelfDmg = 12.0,
}

function CrystalAura.tick()
    if not world or not world.player then return end
    if not world.crystals then return end

    local player = world.player
    local settings = CrystalAura.settings
    local bestCrystal = nil
    local bestScore = -1

    for _, crystal in ipairs(world.crystals) do
        local dx = crystal.x - player.x
        local dy = crystal.y - player.y
        local dz = crystal.z - player.z
        local dist = math.sqrt(dx * dx + dy * dy + dz * dz)

        if dist <= settings.range then
            -- Check base block (obsidian or bedrock)
            local baseKey = math.floor(crystal.x) .. "," .. math.floor(crystal.y - 1) .. "," .. math.floor(crystal.z)
            local base = world.blocks[baseKey]
            if base == "obsidian" or base == "bedrock" then
                local score = 100.0 - dist * 2.0
                if score > bestScore then
                    bestScore = score
                    bestCrystal = crystal
                end
            end
        end
    end

    if bestCrystal then
        look_at(bestCrystal.x, bestCrystal.y + 0.5, bestCrystal.z)
        attack(bestCrystal.id)
        swing_hand(0)
    end
end
