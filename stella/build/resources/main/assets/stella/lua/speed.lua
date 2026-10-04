-- Speed module for Stella Client
-- C++ brain: boosts player movement speed when walking

Speed = {}

Speed.settings = {
    speed = 1.5,
    mode = "vanilla",
}

function Speed.tick()
    if not world or not world.player then return end

    local player = world.player

    local horizSpeed = math.sqrt(player.velX * player.velX + player.velZ * player.velZ)

    if horizSpeed > 0.01 and player.onGround then
        local velLen = math.sqrt(player.velX * player.velX + player.velZ * player.velZ)
        local moveX = player.velX / velLen
        local moveZ = player.velZ / velLen

        local boostX = moveX * Speed.settings.speed * 0.2
        local boostZ = moveZ * Speed.settings.speed * 0.2

        local maxSpeed = 0.6
        local boostLen = math.sqrt(boostX * boostX + boostZ * boostZ)
        if boostLen > maxSpeed then
            boostX = boostX / boostLen * maxSpeed
            boostZ = boostZ / boostLen * maxSpeed
        end

        velocity(boostX, player.velY, boostZ)
    end
end
