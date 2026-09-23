data modify storage enchanted_timber:meta version set value "1.0.0"
scoreboard objectives add enchanted_timber.data dummy
scoreboard objectives add enchanted_timber.left minecraft.custom:minecraft.leave_game
scoreboard players set #registered enchanted_timber.data 0
function #enchanted_timber:registered
execute store result score #load enchanted_timber.data run time query gametime
schedule function enchanted_timber:check 1t replace
