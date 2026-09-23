execute if score #problem enchanted_timber.data matches 0 run return fail
tag @a[scores={enchanted_timber.left=1..}] remove enchanted_timber.warned
scoreboard players reset @a enchanted_timber.left
execute if score #problem enchanted_timber.data matches 1 run tellraw @a[tag=!enchanted_timber.warned] ["",{text:"✦ Enchanted Timber ",color:"light_purple"},{text:"needs the Timber data pack, version 1.1.0 or newer. Install or update it, then run /reload. Until then, the Timber enchantment does nothing. ",color:"gray"},{text:"[Get Timber]",color:"aqua",hover_event:{action:"show_text",value:"modrinth.com/datapack/vanilla-timber"},click_event:{action:"open_url",url:"https://modrinth.com/datapack/vanilla-timber"}}]
execute if score #problem enchanted_timber.data matches 2 run tellraw @a[tag=!enchanted_timber.warned] ["",{text:"✦ Enchanted Timber ",color:"light_purple"},{text:"was added without a restart, so the Timber enchantment doesn't exist yet. Reopen the world or restart the server. Until then, felling works without the enchantment.",color:"gray"}]
tag @a add enchanted_timber.warned
schedule function enchanted_timber:warn 2s replace
