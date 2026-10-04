-- SelfTrap module for Stella Client
-- C++ brain: places obsidian at head level around player

SelfTrap = {}

SelfTrap.settings = {
    head = true,
}

function SelfTrap.tick()
    if not world or not world.player then return end
    if not world.blocks then return end

    local player = world.player
    local px = math.floor(player.x)
    local py = math.floor(player.y)
    local pz = math.floor(player.z)

    -- Place obsidian around head level (y+1)
    local headY = py + 1
    local targets = {
        {px, headY, pz - 1},
        {px, headY, pz + 1},
        {px + 1, headY, pz},
        {px - 1, headY, pz},
    }

    for _, pos in ipairs(targets) do
        local key = pos[1] .. "," .. pos[2] .. "," .. pos[3]
        local block = world.blocks[key]
        if block == nil or block == "air" then
            -- Check for solid support
            local belowKey = pos[1] .. "," .. (pos[2] - 1) .. "," .. pos[3]
            local below = world.blocks[belowKey]
            if below and below ~= "air" then
                place(pos[1], pos[2], pos[3], 1)
                swing_hand(0)
            end
        end
    end
end
