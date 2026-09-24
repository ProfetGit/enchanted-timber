import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class EnchantedTimberTest {
    static MinecraftServer server;
    static ServerLevel level;
    static ServerPlayer player;
    static EmbeddedChannel channel;
    static int passed, failed;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        server = boot();
        long deadline = System.currentTimeMillis() + 180_000;
        while (!server.isReady()) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never became ready");
            Thread.sleep(50);
        }
        ticks(20);
        level = server.overworld();
        try {
            if (args.length >= 2 && args[0].equals("explore")) {
                explore(Path.of(args[1]));
            } else {
                Scenarios.run();
                System.out.println("SUMMARY " + passed + " passed, " + failed + " failed");
                for (String f : failures) System.out.println("  - " + f);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            failed++;
        } finally {
            on(() -> { server.halt(false); return null; });
            Thread.sleep(3000);
            System.exit(failed == 0 ? 0 : 1);
        }
    }

    /** Vanilla by default; -Dharness.main=<class> boots a plugin platform in-process instead (PLATFORM= in run.sh). */
    static MinecraftServer boot() throws Exception {
        String main = System.getProperty("harness.main");
        if (main == null) {
            net.minecraft.server.Main.main(new String[] {"--nogui"});
            return findServer();
        }
        Class.forName(main).getMethod("main", String[].class).invoke(null, (Object) new String[] {"--nogui"});
        java.lang.reflect.Method get = MinecraftServer.class.getMethod("getServer");
        long deadline = System.currentTimeMillis() + 180_000;
        Object s;
        while ((s = get.invoke(null)) == null) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("server never started");
            Thread.sleep(50);
        }
        return (MinecraftServer) s;
    }

    @SuppressWarnings("unchecked")
    static MinecraftServer findServer() throws Exception {
        Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
        Field hooksField = hooksClass.getDeclaredField("hooks");
        hooksField.setAccessible(true);
        Map<Thread, Thread> hooks = (Map<Thread, Thread>) hooksField.get(null);
        for (Thread t : hooks.keySet()) {
            for (Field f : t.getClass().getDeclaredFields()) {
                if (MinecraftServer.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return (MinecraftServer) f.get(t);
                }
            }
        }
        throw new IllegalStateException("server instance not found in shutdown hooks");
    }

    static <T> T on(Callable<T> task) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        server.submit(() -> {
            try {
                out.set(task.call());
            } catch (Throwable t) {
                err.set(t);
            }
        }).join();
        if (err.get() != null) throw new RuntimeException(err.get());
        return out.get();
    }

    static void ticks(int n) throws InterruptedException {
        int target = server.getTickCount() + n;
        while (server.getTickCount() < target) Thread.sleep(2);
    }

    /** Runs a console command on the server thread and returns its chat output. */
    static List<String> cmd(String command) {
        return on(() -> {
            List<String> out = new ArrayList<>();
            // a proxy, not an anonymous class: plugin platforms add methods (getBukkitSender), answered by the server
            CommandSource capture = (CommandSource) java.lang.reflect.Proxy.newProxyInstance(CommandSource.class.getClassLoader(),
                new Class<?>[] {CommandSource.class}, (proxy, m, a) -> switch (m.getName()) {
                    case "sendSystemMessage" -> { out.add(((Component) a[0]).getString()); yield null; }
                    case "acceptsSuccess", "acceptsFailure" -> true;
                    case "shouldInformAdmins" -> false;
                    default -> m.invoke(server, a);
                });
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), command);
            return out;
        });
    }

    /** A /data get source as full SNBT ("null" when missing): Paper and Purpur cut /data get output at 128 characters. */
    static String full(String source) {
        cmd("data remove storage harness:full v");
        cmd("data modify storage harness:full v set from " + source);
        return on(() -> String.valueOf(server.getCommandStorage().get(net.minecraft.resources.Identifier.parse("harness:full")).get("v")));
    }

    static void explore(Path file) throws Exception {
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("//")) continue;
            if (line.startsWith("!tick ")) { ticks(Integer.parseInt(line.substring(6).trim())); continue; }
            if (line.equals("!player")) { spawnPlayer(); continue; }
            if (line.startsWith("!sneak ")) { sneak(Boolean.parseBoolean(line.substring(7).trim())); continue; }
            if (line.equals("!chat")) { for (String m : chat()) System.out.println("[EXPLORE] chat| " + m); continue; }
            if (line.startsWith("!destroy ")) {
                String[] p = line.substring(9).trim().split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                System.out.println("[EXPLORE] destroy " + pos + " -> " + destroy(pos) + " items " + itemsNear(pos, 3));
                continue;
            }
            List<String> out = cmd(line);
            System.out.println("[EXPLORE] > " + line);
            for (String o : out) System.out.println("[EXPLORE]     " + o.replace("\n", "\n[EXPLORE]     "));
        }
    }

    static void spawnPlayer() {
        if (player != null) return;
        on(() -> {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("TimberTester".getBytes()), "TimberTester");
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            ServerPlayer p = new ServerPlayer(server, level, profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(new ChannelHandler[] {connection});
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            player = p;
            return null;
        });
    }

    /** Drains chat/action-bar packets sent to the mock player: "text {clicks=n}". */
    static List<String> chat() {
        return on(() -> {
            List<String> out = new ArrayList<>();
            Object o;
            while ((o = channel.readOutbound()) != null) {
                if (o instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket p)
                    out.add((p.overlay() ? "[actionbar] " : "") + p.content().getString().replace("\n", "⏎") + " {clicks=" + clicks(p.content()) + "}");
                else if (o instanceof net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket p)
                    out.add("[actionbar] " + p.text().getString());
            }
            return out;
        });
    }

    static int clicks(Component c) {
        int n = c.getStyle().getClickEvent() != null ? 1 : 0;
        for (Component s : c.getSiblings()) n += clicks(s);
        return n;
    }

    static void sneak(boolean on) {
        on(() -> {
            player.setShiftKeyDown(on);
            player.setPose(on ? Pose.CROUCHING : Pose.STANDING);
            return null;
        });
    }

    static boolean destroy(BlockPos pos) {
        return on(() -> player.gameMode.destroyBlock(pos));
    }

    static String blockId(BlockPos pos) {
        return on(() -> {
            BlockState s = level.getBlockState(pos);
            return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        });
    }

    static Map<String, Integer> itemsNear(BlockPos pos, double r) {
        return on(() -> {
            Map<String, Integer> m = new java.util.TreeMap<>();
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(r))) {
                ItemStack s = e.getItem();
                m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
            }
            return m;
        });
    }

    /** True while Timber still has a tree in flight (displays, controller or job markers). */
    static boolean felling() {
        return on(() -> {
            for (net.minecraft.world.entity.Entity e : level.getAllEntities())
                if (e.entityTags().stream().anyMatch(g -> g.equals("timber.d") || g.equals("timber.ctl") || g.equals("timber.job") || g.equals("timber.m"))) return true;
            return false;
        });
    }

    static int displays() {
        return on(() -> {
            int n = 0;
            for (net.minecraft.world.entity.Entity e : level.getAllEntities())
                if (e instanceof net.minecraft.world.entity.Display.BlockDisplay && e.entityTags().contains("timber.d")) n++;
            return n;
        });
    }

    static int count(java.util.function.Predicate<BlockState> f, BlockPos c, int r) {
        return on(() -> {
            int n = 0;
            for (BlockPos q : BlockPos.betweenClosed(c.getX() - r, c.getY() - 1, c.getZ() - r, c.getX() + r, c.getY() + 12, c.getZ() + r))
                if (f.test(level.getBlockState(q))) n++;
            return n;
        });
    }

    static ItemStack mainhand() {
        return on(() -> player.getMainHandItem().copy());
    }

    static void setMainhand(ItemStack s) {
        on(() -> { player.setItemInHand(InteractionHand.MAIN_HAND, s.copy()); return null; });
    }

    /** Puts left and right into a fresh anvil and returns the result slot. */
    static ItemStack anvil(ItemStack left, ItemStack right) {
        return on(() -> {
            AnvilMenu m = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(level, player.blockPosition()));
            // CraftBukkit builds a Bukkit view of the menu, which needs the title that openMenu would set
            try {
                m.getClass().getMethod("setTitle", Component.class).invoke(m, Component.literal("Repair & Name"));
            } catch (NoSuchMethodException e) {
                // vanilla: no title needed
            }
            m.getSlot(0).set(left.copy());
            m.getSlot(1).set(right.copy());
            m.createResult();
            return m.getSlot(2).getItem().copy();
        });
    }

    static Holder.Reference<Enchantment> enchantment(String id) {
        return on(() -> server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow());
    }

    /** Rolls the enchanting table's selection at the given level and counts rolls that include the enchantment. */
    static int tableRolls(String itemId, String enchId, int cost, int rolls) {
        return on(() -> {
            Registry<Enchantment> reg = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder.Reference<Enchantment> target = reg.get(Identifier.parse(enchId)).orElseThrow();
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
            RandomSource rnd = RandomSource.create(42);
            int hits = 0;
            for (int i = 0; i < rolls; i++) {
                List<EnchantmentInstance> picked = EnchantmentHelper.selectEnchantment(rnd, stack, cost,
                    java.util.stream.StreamSupport.stream(reg.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE).spliterator(), false));
                for (EnchantmentInstance e : picked) if (e.enchantment().is(target.key())) hits++;
            }
            return hits;
        });
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("[PASS] " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            failures.add(name + ": " + detail);
            System.out.println("[FAIL] " + name + "  (" + detail + ")");
        }
    }

    static void info(String msg) {
        System.out.println("[INFO] " + msg);
    }
}

class Scenarios {
    static final int G = -60, Y = -59;
    static int plotIdx = 0, cx, cz;
    static final String ENCH = "enchanted_timber:timber";
    static final String HAS = "[minecraft:enchantments~[{enchantments:\"" + ENCH + "\"}]]";
    static final String AXE = "minecraft:iron_axe", EAXE = AXE + "[minecraft:enchantments={\"" + ENCH + "\":1}]";
    // on a plugin platform run.sh passes the base's and the add-on's pack ids in -Dharness.packs
    static final String[] PACKS = System.getProperty("harness.packs", "").split(" ");
    static final String BASE = PACKS.length == 2 ? PACKS[0] : baseZip(), OLD = "file/timber-1.0.0",
        ADDON = PACKS.length == 2 ? PACKS[1] : "file/EnchantedTimber-1.0.0.zip";

    static List<String> cmd(String c) { return EnchantedTimberTest.cmd(c); }
    static List<String> chat() { return EnchantedTimberTest.chat(); }
    static void ticks(int n) throws InterruptedException { EnchantedTimberTest.ticks(n); }
    static void check(String name, boolean ok, String detail) { EnchantedTimberTest.check(name, ok, detail); }
    static void info(String msg) { EnchantedTimberTest.info(msg); }

    static BlockPos p(int dx, int dy, int dz) { return new BlockPos(cx + dx, Y + dy, cz + dz); }
    static String at(int dx, int dy, int dz) { return (cx + dx) + " " + (Y + dy) + " " + (cz + dz); }

    static String baseZip() {
        try (var s = Files.list(Path.of("world/datapacks"))) {
            return "file/" + s.map(f -> f.getFileName().toString()).filter(n -> n.startsWith("Timber-") && n.endsWith(".zip")).findFirst().orElseThrow();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A fresh 32x32 grass plot (8 in rotation), Timber at defaults, player standing 2 west of the plot centre. */
    static void plot() throws Exception {
        int i = plotIdx++ % 8;
        cx = 16 + 32 * (i % 4);
        cz = 16 + 32 * (i / 4);
        cmd("kill @e[type=block_display,tag=timber.d]");
        cmd("fill " + (cx - 16) + " " + Y + " " + (cz - 16) + " " + (cx + 15) + " " + (Y + 14) + " " + (cz + 15) + " minecraft:air");
        cmd("fill " + (cx - 16) + " " + G + " " + (cz - 16) + " " + (cx + 15) + " " + G + " " + (cz + 15) + " minecraft:grass_block");
        cmd("kill @e[type=item]");
        cmd("scoreboard players reset * timber.config");
        cmd("function timber:config/defaults");
        cmd("gamemode survival TimberTester");
        cmd("scoreboard players set TimberTester timber.off 0");
        cmd("clear TimberTester");
        EnchantedTimberTest.sneak(false);
        cmd("tp TimberTester " + (cx - 2) + ".5 " + Y + " " + cz + ".5 -90 20");
        ticks(2);
    }

    /** Plot with a natural oak (trunk 6, canopy radius 2); returns its log count. */
    static int tree() throws Exception {
        plot();
        for (int ly = 3; ly <= 6; ly++) {
            int rr = ly >= 5 ? 1 : 2;
            cmd("fill " + at(-rr, ly, -rr) + " " + at(rr, ly, rr) + " minecraft:oak_leaves keep");
        }
        cmd("fill " + at(0, 0, 0) + " " + at(0, 5, 0) + " minecraft:oak_log");
        ticks(30);
        return logs();
    }

    static boolean isLog(BlockState s) { return s.is(net.minecraft.tags.BlockTags.LOGS); }
    static int logs() { return EnchantedTimberTest.count(Scenarios::isLog, p(0, 0, 0), 6); }
    static int dropped() { return EnchantedTimberTest.itemsNear(p(0, 0, 0), 14).getOrDefault("minecraft:oak_log", 0); }

    static void hold(String spec) { cmd("item replace entity TimberTester weapon.mainhand with " + spec); }

    /** Chops the bottom log; returns logs left standing one tick later. */
    static int chop() throws Exception {
        EnchantedTimberTest.destroy(p(0, 0, 0));
        ticks(1);
        return logs();
    }

    /** Waits for a felled tree to land and poof. */
    static void settle() throws Exception {
        for (int i = 0; i < 150 && EnchantedTimberTest.felling(); i++) ticks(1);
        ticks(2);
    }

    /** Chops a fresh oak with the given tool: 1 = felled, 0 = single log, -1 = something else. */
    static int fells(String toolSpec) throws Exception {
        int n = tree();
        hold(toolSpec);
        int left = chop();
        int d = EnchantedTimberTest.displays();
        settle();
        return left == 0 && d > 0 ? 1 : left == n - 1 && d == 0 ? 0 : -1;
    }

    static String held() {
        ItemStack s = EnchantedTimberTest.mainhand();
        return BuiltInRegistries.ITEM.getKey(s.getItem()) + " dmg=" + s.getDamageValue() + " " + s.getEnchantments();
    }

    static int damage() { return EnchantedTimberTest.mainhand().getDamageValue(); }

    static boolean mainhandIs(String itemPredicate) {
        return cmd("execute if items entity TimberTester weapon.mainhand " + itemPredicate).toString().contains("Test passed");
    }

    static int score(String holder) {
        String out = cmd("scoreboard players get " + holder + " enchanted_timber.data").toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(" has (-?\\d+) ").matcher(out);
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MIN_VALUE;
    }

    static int requiresCount() {
        String out = EnchantedTimberTest.full("storage timber:meta requires");
        return out.equals("null") ? -1 : out.split("Your axe needs the ", -1).length - 1;
    }

    static boolean isWarning(String m) { return m.startsWith("✦ Enchanted Timber "); }

    static void pack(String action, String id) throws Exception {
        List<String> out = cmd("datapack " + action + " \"" + id + "\"");
        info("datapack " + action + " " + id + " -> " + out);
        ticks(5);
    }

    static void run() throws Exception {
        EnchantedTimberTest.spawnPlayer();
        cmd("forceload add 0 0 127 63");
        cmd("gamerule random_tick_speed 0");
        cmd("gamerule block_drops true");
        cmd("gamemode survival TimberTester");
        ticks(80);
        pack("disable", OLD);
        List<String> packs = cmd("datapack list enabled");
        check("packs: Timber 1.1.0 + add-on enabled, 1.0.0 off", packs.toString().contains(BASE) && packs.toString().contains(ADDON)
            && !packs.toString().contains(OLD), packs.toString());
        chat();
        ticks(50);
        List<String> c = chat();
        check("no warning with Timber present", score("#problem") == 0 && c.stream().noneMatch(Scenarios::isWarning),
            "problem=" + score("#problem") + " chat=" + c);
        check("enchantment registered", score("#registered") == 1, "registered=" + score("#registered"));
        check("enchantment name is Timber", EnchantedTimberTest.enchantment(ENCH).value().description().getString().equals("Timber"),
            EnchantedTimberTest.enchantment(ENCH).value().description().getString());
        check("requirement registered with Timber once", requiresCount() == 1, "requires=" + requiresCount());

        // 1. the gate
        int n = tree();
        hold(AXE);
        chat();
        int left = chop();
        ticks(5);
        check("unenchanted axe chops a single log", left == n - 1 && EnchantedTimberTest.displays() == 0 && dropped() == 1 && damage() == 1,
            "logs " + n + " -> " + left + ", displays " + EnchantedTimberTest.displays() + ", dropped " + dropped() + ", " + held());
        List<String> bar = chat();
        check("no action bar without the enchantment", bar.stream().noneMatch(m -> m.startsWith("[actionbar]")), bar.toString());

        n = tree();
        hold(AXE);
        List<String> en = cmd("enchant TimberTester " + ENCH);
        info("enchant axe -> " + en);
        chat();
        left = chop();
        int d = EnchantedTimberTest.displays();
        check("/enchant works on an axe", en.toString().contains("Applied enchantment"), en.toString());
        check("enchanted axe fells the whole tree", left == 0 && d > 0, "logs left " + left + ", displays " + d);
        bar = chat();
        info("action bar: " + bar);
        check("action bar reports the fell", bar.stream().anyMatch(m -> m.startsWith("[actionbar]") && m.contains("Timber!")), bar.toString());
        settle();
        check("every log drops, axe pays 1 durability per log", dropped() == n && damage() == n && mainhandIs("*" + HAS),
            "dropped " + dropped() + " of " + n + ", " + held());

        for (String a : new String[] {"wooden", "stone", "copper", "golden", "iron", "diamond", "netherite"}) {
            hold("minecraft:" + a + "_axe");
            en = cmd("enchant TimberTester " + ENCH);
            if (!mainhandIs("minecraft:" + a + "_axe" + HAS)) check("/enchant on " + a + " axe", false, en.toString());
        }
        check("/enchant works on all 7 axes", true, "");

        int r = fells("minecraft:diamond_axe[minecraft:enchantments={\"minecraft:efficiency\":5,\"minecraft:unbreaking\":3,\"minecraft:mending\":1,\"" + ENCH + "\":1}]");
        check("Efficiency V + Unbreaking III + Mending + Timber: fells", r == 1, "result " + r + " " + held());

        for (String other : new String[] {"minecraft:sharpness\":5", "minecraft:silk_touch\":1", "minecraft:fortune\":3"}) {
            hold("minecraft:netherite_axe[minecraft:enchantments={\"" + other + "}]");
            en = cmd("enchant TimberTester " + ENCH);
            check("combines with " + other.substring(10, other.indexOf('"')), en.toString().contains("Applied enchantment") && mainhandIs("*" + HAS), en.toString());
        }

        n = tree();
        hold(EAXE);
        EnchantedTimberTest.sneak(true);
        left = chop();
        EnchantedTimberTest.sneak(false);
        check("Timber's own rules still apply (sneaking -> single log)", left == n - 1 && EnchantedTimberTest.displays() == 0, "logs " + n + " -> " + left);

        n = tree();
        hold("minecraft:golden_axe[minecraft:damage=30,minecraft:enchantments={\"" + ENCH + "\":1}]");
        left = chop();
        check("Timber's durability rule still applies (worn axe -> single log)", left == n - 1 && EnchantedTimberTest.displays() == 0,
            "logs " + n + " -> " + left + " " + held());

        // 2. not for other tools
        for (String t : new String[] {"minecraft:diamond_pickaxe", "minecraft:diamond_shovel", "minecraft:diamond_sword", "minecraft:diamond_hoe",
                "minecraft:shears", "minecraft:mace", "minecraft:trident", "minecraft:fishing_rod"}) {
            hold(t);
            en = cmd("enchant TimberTester " + ENCH);
            check("/enchant refused on " + t, !mainhandIs("*" + HAS) && !en.toString().contains("Applied enchantment"), en.toString());
        }

        // 3. enchanted book + anvil
        hold("minecraft:enchanted_book[minecraft:stored_enchantments={\"" + ENCH + "\":1}]");
        check("enchanted book with stored_enchantments", mainhandIs("minecraft:enchanted_book[minecraft:stored_enchantments~[{enchantments:\"" + ENCH + "\"}]]"), held());
        ItemStack book = EnchantedTimberTest.mainhand();
        n = tree();
        hold("minecraft:diamond_axe[minecraft:enchantments={\"minecraft:efficiency\":5}]");
        ItemStack out = EnchantedTimberTest.anvil(EnchantedTimberTest.mainhand(), book);
        EnchantedTimberTest.setMainhand(out);
        check("anvil: book onto axe", mainhandIs("minecraft:diamond_axe" + HAS) && mainhandIs("*[minecraft:enchantments~[{enchantments:\"minecraft:efficiency\",levels:5}]]"), held());
        left = chop();
        check("anvil-enchanted axe fells", left == 0 && EnchantedTimberTest.displays() > 0, "logs left " + left);
        settle();
        for (String t : new String[] {"minecraft:diamond_pickaxe", "minecraft:diamond_shovel", "minecraft:diamond_sword"}) {
            hold(t);
            out = EnchantedTimberTest.anvil(EnchantedTimberTest.mainhand(), book);
            EnchantedTimberTest.setMainhand(out);
            check("anvil: book refused on " + t, out.isEmpty() || !mainhandIs("*" + HAS), held());
        }
        out = EnchantedTimberTest.anvil(book, book);
        EnchantedTimberTest.setMainhand(out);
        check("anvil: two books don't make level II", !mainhandIs("*[minecraft:stored_enchantments~[{enchantments:\"" + ENCH + "\",levels:{min:2}}]]"), held());

        // 4. where it comes from
        int axeHits = EnchantedTimberTest.tableRolls("minecraft:iron_axe", ENCH, 30, 3000);
        int bookHits = EnchantedTimberTest.tableRolls("minecraft:book", ENCH, 30, 3000);
        int pickHits = EnchantedTimberTest.tableRolls("minecraft:iron_pickaxe", ENCH, 30, 3000);
        int swordHits = EnchantedTimberTest.tableRolls("minecraft:iron_sword", ENCH, 30, 3000);
        int lowHits = EnchantedTimberTest.tableRolls("minecraft:iron_axe", ENCH, 5, 3000);
        int effHits = EnchantedTimberTest.tableRolls("minecraft:iron_axe", "minecraft:efficiency", 30, 3000);
        int sharpHits = EnchantedTimberTest.tableRolls("minecraft:iron_axe", "minecraft:sharpness", 30, 3000);
        info("table rolls /3000 at level 30: timber axe=" + axeHits + " book=" + bookHits + " pickaxe=" + pickHits + " sword=" + swordHits
            + ", level 5 axe=" + lowHits + "; efficiency=" + effHits + " sharpness=" + sharpHits);
        check("enchanting table offers it for axes and books", axeHits > 0 && bookHits > 0, "axe=" + axeHits + " book=" + bookHits);
        check("enchanting table never offers it for pickaxes or swords", pickHits == 0 && swordHits == 0, "pickaxe=" + pickHits + " sword=" + swordHits);
        hold(EAXE);
        for (String tag : new String[] {"in_enchanting_table", "non_treasure", "tradeable", "on_random_loot", "on_traded_equipment", "tooltip_order"})
            check("in #minecraft:" + tag, mainhandIs("*[minecraft:enchantments~[{enchantments:\"#minecraft:" + tag + "\"}]]"), "");
        for (String tag : new String[] {"treasure", "curse", "double_trade_price", "exclusive_set/mining", "exclusive_set/damage"})
            check("not in #minecraft:" + tag, !mainhandIs("*[minecraft:enchantments~[{enchantments:\"#minecraft:" + tag + "\"}]]"), "");

        // 5. Timber's join hint and menu
        chat();
        cmd("execute as TimberTester run function timber:player/welcome");
        List<String> hello = chat();
        info("welcome: " + hello);
        check("join hint says the enchantment is needed",
            hello.contains("🪓 Timber: chop a tree with an axe and the whole tree comes down. Sneak to take a single log. Your axe needs the Timber enchantment. [Toggle] {clicks=1}"),
            hello.toString());
        cmd("execute as TimberTester run function timber:settings");
        List<String> menu = chat();
        for (String m : menu) info("menu| " + m);
        check("settings menu says the enchantment is needed", menu.size() == 13 && menu.get(1).contains("Your axe needs the Timber enchantment."),
            menu.size() + " lines");

        // 6. /reload keeps everything
        cmd("reload");
        ticks(5);
        check("after /reload: one requirement line, still gated", requiresCount() == 1 && fells(AXE) == 0 && fells(EAXE) == 1,
            "requires=" + requiresCount());
        chat();
        ticks(45);
        check("after /reload: no warning", chat().stream().noneMatch(Scenarios::isWarning) && score("#problem") == 0, "problem=" + score("#problem"));

        // 7. Timber missing
        pack("disable", BASE);
        c = chat();
        info("chat without Timber: " + c);
        check("warns when Timber is missing", score("#problem") == 1 && c.stream().anyMatch(m -> isWarning(m) && m.contains("version 1.1.0 or newer") && m.contains("{clicks=1}")),
            c.toString());
        ticks(50);
        c = chat();
        check("warning isn't repeated to the same player", c.stream().noneMatch(Scenarios::isWarning), c.toString());
        cmd("scoreboard players set TimberTester enchanted_timber.left 1");
        ticks(45);
        c = chat();
        check("warning repeats after a rejoin", c.stream().anyMatch(Scenarios::isWarning), c.toString());
        pack("enable", BASE);
        chat();
        ticks(45);
        c = chat();
        check("warning stops once Timber is back", score("#problem") == 0 && c.stream().noneMatch(Scenarios::isWarning), c.toString());
        check("gate works again", fells(AXE) == 0 && fells(EAXE) == 1, "");

        // 8. Timber 1.0.0 (no add-on support)
        pack("disable", BASE);
        pack("enable", OLD);
        List<String> ver = cmd("data get storage timber:meta version");
        c = chat();
        check("warns with Timber 1.0.0", ver.toString().contains("1.0.0") && score("#problem") == 1 && c.stream().anyMatch(Scenarios::isWarning), ver + " " + c);
        info("Timber 1.0.0 + add-on, unenchanted axe: " + (fells(AXE) == 1 ? "fells (no hooks)" : "single log"));
        pack("disable", OLD);
        pack("enable", BASE);
        chat();
        ticks(45);
        check("back on 1.1.0: no warning, gated", score("#problem") == 0 && chat().stream().noneMatch(Scenarios::isWarning) && fells(AXE) == 0,
            "problem=" + score("#problem"));

        // 9. add-on switched off with /datapack disable (no restart)
        pack("disable", ADDON);
        r = fells(AXE);
        hold(AXE);
        en = cmd("enchant TimberTester " + ENCH);
        check("add-on disabled: Timber fells without the enchantment again", r == 1 && requiresCount() == -1, "result " + r + " requires=" + requiresCount());
        info("add-on disabled, before restart: /enchant -> " + en);
        pack("enable", ADDON);
        check("add-on re-enabled: gated again", fells(AXE) == 0 && requiresCount() == 1, "requires=" + requiresCount());

        // 10. uninstall
        List<String> un = cmd("execute as TimberTester run function enchanted_timber:uninstall");
        List<String> objs = cmd("scoreboard objectives list");
        check("uninstall removes the add-on's objectives", objs.stream().noneMatch(o -> o.contains("enchanted_timber")), objs + " " + un);
    }
}
