-- HoleESP module for Stella Client
-- Detects safe holes (obsidian/bedrock surrounded)

HoleESP = {}

HoleESP.settings = {
    range = 16.0,
    showObbyHoles = true,
    showBedrockHoles = true,
    showMixedHoles = true,
}

function HoleESP.tick()
    if not world or not world.player then return end
    if not world.blocks then return end

    local player = world.player
    local px = math.floor(player.x)
    local py = math.floor(player.y)
    local pz = math.floor(player.z)
    local rangeXZ = 16
    local rangeY = 4

    local BLOCK_OBSIDIAN = "obsidian"
    local BLOCK_BEDROCK = "bedrock"

    for dx = -rangeXZ, rangeXZ do
        for dy = -rangeY, rangeY do
            for dz = -rangeXZ, rangeXZ do
                local bx = px + dx
                local by = py + dy
                local bz = pz + dz

                local block = world.blocks[bx .. "," .. by .. "," .. bz]
                if block == nil or block == "air" then
                    local north = world.blocks[bx .. "," .. by .. "," .. (bz - 1)]
                    local south = world.blocks[bx .. "," .. by .. "," .. (bz + 1)]
                    local east = world.blocks[(bx + 1) .. "," .. by .. "," .. bz]
                    local west = world.blocks[(bx - 1) .. "," .. by .. "," .. bz]
                    local below = world.blocks[bx .. "," .. (by - 1) .. "," .. bz]

                    local obbyCount = 0
                    local bedrockCount = 0
                    local airCount = 0

                    local sides = {north, south, east, west}
                    for _, side in ipairs(sides) do
                        if side == BLOCK_OBSIDIAN then obbyCount = obbyCount + 1
                        elseif side == BLOCK_BEDROCK then bedrockCount = bedrockCount + 1
                        else airCount = airCount + 1 end
                    end

                    local belowSolid = (below == BLOCK_OBSIDIAN or below == BLOCK_BEDROCK)

                    if belowSolid and airCount == 0 then
                        local holeType = "unknown"
                        if bedrockCount == 5 then holeType = "bedrock"
                        elseif obbyCount == 5 then holeType = "obsidian"
                        elseif bedrockCount + obbyCount == 5 then holeType = "mixed"
                        end

                        if holeType ~= "unknown" then
                            log(string.format("HOLE: %s at %d,%d,%d", holeType, bx, by, bz))
                        end
                    end
                end
            end
        end
    end
end
