scoreboard objectives add enchanted_timber.data dummy
execute store result score #base enchanted_timber.data run time query gametime
execute store result score #base_version enchanted_timber.data run data get storage timber:meta version_id
data modify storage timber:meta requires append value {text:"Your axe needs the ",color:"gray",extra:[{text:"Timber",color:"light_purple",hover_event:{action:"show_text",value:"From the Enchanted Timber add-on. Find it in the enchanting table, from librarians, or in loot."}},{text:" enchantment. "}]}
