schedule clear enchanted_timber:check
schedule clear enchanted_timber:warn
scoreboard objectives remove enchanted_timber.data
scoreboard objectives remove enchanted_timber.left
tag @a remove enchanted_timber.warned
data remove storage enchanted_timber:meta version
tellraw @s ["",{text:"✦ Enchanted Timber data removed. ",color:"light_purple"},{text:"Now delete EnchantedTimber-1.0.0.zip from the datapacks folder and reopen the world or restart the server. Timber then works without the enchantment again.",color:"gray"}]
