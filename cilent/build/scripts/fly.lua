-- Fly module for Stella Client
-- C++ brain: allows player to fly in survival mode

Fly = {}

Fly.settings = {
    speed = 2.0,
    mode = "vanilla",
}

function Fly.tick()
    if not world or not world.player then return end

    local player = world.player
    local vy = 0

    if not player.onGround then
        if player.velY > 0.1 then
            vy = Fly.settings.speed * 0.1
        elseif player.velY < -0.1 then
            vy = -Fly.settings.speed * 0.1
        else
            vy = -0.05
        end

        local yawRad = player.yaw * math.pi / 180.0
        local moveX = -math.sin(yawRad) * Fly.settings.speed * 0.1
        local moveZ = math.cos(yawRad) * Fly.settings.speed * 0.1

        velocity(moveX, vy, moveZ)
    end
end
