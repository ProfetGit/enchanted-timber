-- Enchanted Timber icon sprites. Run through the aseprite MCP: dofile("<abs>/EnchantedTimber/dev/icon/draw_sprites.lua")
-- Timber's tree, axe and FX and Enchanted Veinminer's book, runes and violet FX (same author), plus the axe's flash and glint.
dofile("/home/emppu/Projects/Minecraft Datapacks/.claude/skills/pack-icon-animation/assets/pixel_art.lua")
local OUT = "/home/emppu/Projects/Minecraft Datapacks/EnchantedTimber/dev/icon/sprites/"
local TB = "/home/emppu/Projects/Minecraft Datapacks/Timber/dev/icon/sprites/"
local EV = "/home/emppu/Projects/Minecraft Datapacks/EnchantedVeinminer/dev/icon/sprites/"
local pc = app.pixelColor
local GLINT_EDGE, GLINT_CORE = "#C866FF", "#F6C8FF"

local function hexof(v) return string.format("#%02X%02X%02X", pc.rgbaR(v), pc.rgbaG(v), pc.rgbaB(v)) end
local function lerp(hex, to, k)
  local a, b = PA.hex(hex), PA.hex(to)
  local f = function(x, y) return math.floor(x + (y - x) * k + 0.5) end
  return string.format("#%02X%02X%02X", f(pc.rgbaR(a), pc.rgbaR(b)), f(pc.rgbaG(a), pc.rgbaG(b)), f(pc.rgbaB(a), pc.rgbaB(b)))
end
-- read a png/aseprite into {key = hex}, w, h
local function load(path)
  local s = Sprite{ fromFile = path }
  local px = {}
  for it in s.cels[1].image:pixels() do
    local v = it()
    if pc.rgbaA(v) > 127 then px[PA.key(it.x + s.cels[1].position.x, it.y + s.cels[1].position.y)] = hexof(v) end
  end
  local w, h = s.width, s.height
  s:close()
  return px, w, h
end
local function transform(src, dst, fn)
  local px, w, h = load(src)
  local out = {}
  for k, v in pairs(px) do out[k] = fn(k % 4096, k // 4096, v) end
  PA.save_pixels(dst, w, h, out)
end
local function copy(src, dst) transform(src, dst, function(x, y, v) return v end) end
-- enchantment glint: a diagonal band (x - y in [c, c + 3]) lerped toward violet, brightest in the middle two
local function glint(src, dst, c)
  transform(src, dst, function(x, y, v)
    local d = (x - y) - c
    if d == 1 or d == 2 then return lerp(v, GLINT_CORE, 0.75) end
    if d == 0 or d == 3 then return lerp(v, GLINT_EDGE, 0.55) end
    return v
  end)
end

for _, n in ipairs({ "log_side", "log_side_left", "log_side_right", "leaves", "leaves_left", "leaves_right",
                     "mini_log_side", "mini_log_side_left", "mini_log_side_right", "mini_log_top",
                     "apple_item", "chunks", "hit_flash", "fx_star", "fx_puff", "fx_spark", "fx_ring" }) do
  copy(TB .. n .. ".png", OUT .. n)
end
for _, n in ipairs({ "book_open", "book_glint_0", "book_glint_1", "book_glint_2", "book_glint_3", "book_glint_4",
                     "fx_rune", "fx_spark_violet", "fx_ring_violet" }) do
  copy(EV .. n .. ".png", OUT .. n)
end

-- axe: Timber's iron axe, a white flash for the charge and five glint-sweep frames (the art spans x - y = -13..8)
copy(TB .. "axe_item.png", OUT .. "axe_item")
PA.whiten(OUT .. "axe_item.aseprite", OUT .. "axe_flash", 0.8)
for i, c in ipairs({ -14, -9, -4, 1, 6 }) do glint(OUT .. "axe_item.aseprite", OUT .. "axe_glint_" .. (i - 1), c) end
-- violet swing smear
PA.remap(TB .. "fx_smear.png", OUT .. "fx_smear", { ["#FFFFFF"] = "#FFFFFF", ["#D6E4F0"] = "#EDB8FF" })

-- backgrounds: flat night navy plus a ground-shadow ellipse, tilted along the ground line the tree falls onto.
-- Placed for the current crop (icon: 64px grid, x8 = 512px; banner: 192x64 grid with ART_OFFSET in make_banner.py),
-- so move them if the camera, crop or fall changes.
local NIGHT, SHADOW = "#23305E", "#1A2449"
local function shadow_bg(path, w, h, cx, cy, a, b, deg, extra)
  local px, t = {}, math.rad(deg)
  for y = 0, h - 1 do
    for x = 0, w - 1 do
      local dx, dy = x + 0.5 - cx, y + 0.5 - cy
      local u, v = (dx * math.cos(t) + dy * math.sin(t)) / a, (-dx * math.sin(t) + dy * math.cos(t)) / b
      px[PA.key(x, y)] = (u * u + v * v <= 1) and SHADOW or NIGHT
    end
  end
  for k, c in pairs(extra or {}) do px[k] = c end
  PA.save_pixels(path, w, h, px)
end
shadow_bg(OUT .. "bg_flat", 64, 64, 30.9, 52.8, 26.1, 6.3, -11)

-- banner: same shadow under the art, plus pixel stars away from the text and the art
local stars = {}
local function star(x, y, big)
  if big then
    stars[PA.key(x, y)] = "#FFFFFF"
    for _, d in ipairs({ { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) do stars[PA.key(x + d[1], y + d[2])] = "#8FA3E0" end
  else
    stars[PA.key(x, y)] = "#6F83C4"
  end
end
for _, s in ipairs({ { 5, 5, true }, { 38, 4 }, { 72, 7 }, { 101, 5, true }, { 117, 12 }, { 186, 6, true }, { 170, 3 },
                     { 187, 40 }, { 179, 55, true }, { 6, 57 }, { 50, 59, true }, { 96, 58 } }) do
  star(s[1], s[2], s[3])
end
shadow_bg(OUT .. "banner_bg", 192, 64, 142, 54.1, 26, 6.3, -11, stars)

-- banner lettering: ENCHANTED in glint violet over TIMBER in Timber's leaf green; tagline white + green accent
PA.title_sprite(OUT .. "banner_title_top", "ENCHANTED", {
  bands = { "#FCE4FF", "#F0B8FF", "#F0B8FF", "#F0B8FF", "#D77CFF", "#D77CFF", "#D77CFF", "#B04CE8", "#B04CE8", "#B04CE8" },
  extrude = { "#6A1FA8", "#3F0F6A" },
})
PA.title_sprite(OUT .. "banner_title", "TIMBER", {
  bands = { "#F2FFC0", "#C3EA6C", "#C3EA6C", "#C3EA6C", "#8ED14E", "#8ED14E", "#8ED14E", "#5BB23E", "#5BB23E", "#5BB23E" },
  extrude = { "#236B33", "#17492D" },
})
PA.label_sprite(OUT .. "banner_tagline", "ONE BOOK. WHOLE TREE.", function(i) return i > 10 and "#8ED14E" or "#FFFFFF" end)
