scoreboard players set #problem enchanted_timber.data 0
execute unless score #base enchanted_timber.data = #load enchanted_timber.data run scoreboard players set #problem enchanted_timber.data 1
execute unless score #base_version enchanted_timber.data matches 10100.. run scoreboard players set #problem enchanted_timber.data 1
execute if score #registered enchanted_timber.data matches 0 run scoreboard players set #problem enchanted_timber.data 2
tag @a remove enchanted_timber.warned
function enchanted_timber:warn
