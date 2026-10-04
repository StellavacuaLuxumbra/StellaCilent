-- Surround module for Stella Client
-- C++ brain: places obsidian at foot level around player

Surround = {}

Surround.settings = {
    expand = false,
}

function Surround.tick()
    if not world or not world.player then return end
    if not world.blocks then return end

    local player = world.player
    local px = math.floor(player.x)
    local py = math.floor(player.y)
    local pz = math.floor(player.z)

    -- Check if player has obsidian in inventory (selectedSlot check is simplified)
    -- Place obsidian at 4 cardinal + 4 corners around player feet
    local targets = {
        {px, py, pz - 1},  -- north
        {px, py, pz + 1},  -- south
        {px + 1, py, pz},  -- east
        {px - 1, py, pz},  -- west
    }

    for _, pos in ipairs(targets) do
        local key = pos[1] .. "," .. pos[2] .. "," .. pos[3]
        local block = world.blocks[key]
        if block == nil or block == "air" then
            -- Check if there's a solid block below
            local belowKey = pos[1] .. "," .. (pos[2] - 1) .. "," .. pos[3]
            local below = world.blocks[belowKey]
            if below and below ~= "air" then
                -- Place obsidian: face=1 means UP (place on top of the block below)
                place(pos[1], pos[2], pos[3], 1)
                swing_hand(0)
            end
        end
    end
end
