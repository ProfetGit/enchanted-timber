![Enchanted Timber](https://raw.githubusercontent.com/ProfetGit/enchanted-timber/main/docs/banner.gif)

**Tree felling becomes an enchantment.**

Enchanted Timber is an add-on for **[Timber](https://modrinth.com/datapack/vanilla-timber)**, the vanilla data pack for **Minecraft Java 26.2 and 26.3**. It adds a new axe enchantment, **Timber**. Only an axe that has it brings the whole tree down. Without it, an axe chops one log at a time, as in vanilla.

It needs **Timber 1.1.0 or newer**. Install both.

## Why this add-on?

**Earn it, don't just have it.** Felling whole trees turns from a free rule into a reward. Early on you chop log by log. Later you find the enchantment and put it on your best axe.

**A real vanilla enchantment.** Timber is a proper enchantment, not a renamed book or a lore line. The enchanting table offers it, librarians sell it, it turns up in chest loot and fishing, and anvils combine it like any other enchantment. The tooltip shows it by name.

**Plays fair with everything else.** It works alongside Efficiency, Unbreaking, Mending, Fortune, Silk Touch and Sharpness. Timber's own rules and settings still apply on top: sneaking, durability, placed-log protection and the size limits.

**One zip, server-side only.** Like Timber, it needs no mods, no resource pack, no client install and no experimental features.

**Tested, not hoped.** Every release is checked by 60 automated tests on real 26.2 and 26.3 servers, running together with Timber: a simulated player chops trees with and without the enchantment, and the tests check the anvil, the enchanting table, `/enchant`, the loot and trade tags, and what happens when Timber is missing or out of date.

## How to use

1. Get the **Timber** enchantment on an axe.
2. Chop a log of a natural tree.

Sneak to take a single log instead. Timber's join hint and settings menu tell players that the enchantment is needed.

## The enchantment

| | |
|---|---|
| Name | Timber |
| Max level | I |
| Goes on | Any axe: wood, stone, copper, iron, gold, diamond, netherite |
| Rarity | Rare, the same as Fortune |
| Enchanting table | Yes, at the same levels as Fortune I |
| Librarians | Yes, as an enchanted book |
| Loot and fishing | Yes, like other non-treasure enchantments |
| Enchanted axe trades | Toolsmiths' and weaponsmiths' enchanted axes can come with it |
| Combines with | Every other axe enchantment, including Efficiency, Fortune, Silk Touch and Sharpness |

It can't go on pickaxes, shovels, swords or other tools, and the anvil refuses those combinations.

## Commands

| Command | What it does |
|---|---|
| `/enchant @s enchanted_timber:timber` | Enchants the axe in your hand |
| `/give @s enchanted_book[stored_enchantments={"enchanted_timber:timber":1}]` | Gives the enchanted book |
| `/function enchanted_timber:uninstall` | Removes the add-on's scoreboards and data |

All of Timber's commands and settings work as before. See its page.

## Installation

You need both zips: **Timber 1.1.0+** and **Enchanted Timber**.

**Singleplayer**
- New world: under **More → Data Packs**, drag both `.zip` files into the window.
- Existing world: put both `.zip` files in the world's `datapacks/` folder, then **close and reopen the world**.

**Server:** put both `.zip` files in `world/datapacks/` and **restart the server**.

Don't unzip the files.

**`/reload` is not enough for this add-on.** The game registers enchantments only when a world loads. If you add the pack with `/reload`, it tells you in chat to reopen the world, and until you do, felling works without the enchantment. Timber itself keeps working the whole time.

If Timber is missing or older than 1.1.0, the add-on says so in chat with a download link. Until you fix that, the enchantment does nothing.

**As a mod:** both packs also come as mods for Fabric, Quilt, NeoForge and Forge. Put `EnchantedTimber-1.0.0-fabric.jar` (Fabric or Quilt, needs Fabric API) or `EnchantedTimber-1.0.0-forge.jar` (Forge or NeoForge) in the `mods` folder, together with Timber as a mod or as a data pack. A mod loads before the world does, so the enchantment is always registered and the restart note above doesn't apply. Use either the mod or the zip, not both.

## Compatibility

- One zip supports Minecraft Java **26.2 and 26.3**.
- Needs **Timber 1.1.0 or newer**. With Timber 1.0.0 the enchantment does nothing, and every axe fells trees as before.
- Everything lives in the `enchanted_timber` namespace. It adds to the vanilla `#minecraft:non_treasure` and `#minecraft:tooltip_order` enchantment tags and the `#minecraft:load` function tag, and to Timber's add-on hooks. It doesn't replace any files.

## Good to know

- **Removing the add-on removes the enchantment from every item.** The next time the world loads without the pack, the game strips Timber from all axes and books, because the enchantment no longer exists. Axes keep their other enchantments, and Timber books become blank enchanted books. Adding the pack back later doesn't restore them.
- Adding or removing the pack always needs the world reopened or the server restarted.
- The enchantment's name isn't translated, so it shows as "Timber" in every language. Resource packs can translate the key `enchantment.enchanted_timber.timber`.

## Uninstall

1. Run `/function enchanted_timber:uninstall`.
2. Remove `EnchantedTimber-1.0.0.zip` from the `datapacks` folder.
3. Reopen the world or restart the server. Timber then works without the enchantment again, and the enchantment is removed from all items (see above).

Installed as a mod? Run step 1, then remove the jar from the `mods` folder and restart the game or server.

## Support

Enchanted Timber is free. If it saves you some chopping, a coffee helps fund the next update.

[![Support me on Ko-fi](https://raw.githubusercontent.com/ProfetGit/assets/main/kofi-banner.gif)](https://ko-fi.com/profetgit)

## License

© 2026 Profet. All rights reserved.

- **You can** use Enchanted Timber on any server, including monetized ones, and include the unmodified zip in any modpack that credits Profet and links here. You can also feature it in videos and modify it for your own world or server.
- **Please don't** re-upload Enchanted Timber or a modified version of it elsewhere, sell it, or present it as your own.

The full terms are in the `LICENSE` file inside the zip. For anything else, just ask.
