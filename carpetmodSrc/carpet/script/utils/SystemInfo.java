package carpet.script.utils;

import carpet.CarpetSettings;
import carpet.script.CarpetContext;
import carpet.script.CarpetScriptHost;
import carpet.script.value.ListValue;
import carpet.script.value.MapValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import com.sun.management.OperatingSystemMXBean;
import net.minecraft.world.GameRules;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SystemInfo {
    private static final Map<String, Function<CarpetContext,Value>> options = new HashMap<String, Function<CarpetContext,Value>>(){{
        put("app_name", c ->
        {
            String name = c.host.getName();
            return name == null?Value.NULL:new StringValue(name);
        });
        put("app_list", c -> ListValue.wrap(((CarpetScriptHost)c.host).getScriptServer().modules.keySet().stream().filter(Objects::nonNull).map(StringValue::new).collect(Collectors.toList())));
        put("app_scope", c -> StringValue.of((c.host).isPerUser()?"player":"global"));
        put("app_players", c -> ListValue.wrap(c.host.getUserList().stream().map(StringValue::new).collect(Collectors.toList())));

        put("world_name", c -> new StringValue(c.s.getServer().getName()));//todo redo this cos it doesnt work
        put("world_seed", c -> new NumericValue(c.s.getEntityWorld().getSeed()));
        put("server_motd", c -> StringValue.of(c.s.getServer().getMOTD()));
        put("world_path", c -> StringValue.of(c.s.getServer().getDataDirectory().getPath()));
        put("world_folder", c -> {
            Path serverPath = c.s.getServer().getDataDirectory().toPath();
            int nodeCount = serverPath.getNameCount();
            if (nodeCount < 2) return Value.NULL;
            String tlf = serverPath.getName(nodeCount-2).toString();
            return StringValue.of(tlf);
        });
        put("world_dimensions", c -> ListValue.wrap(Arrays.stream(c.s.getServer().worlds).map(k->StringValue.of(k.toString())).collect(Collectors.toList())));//todo dimensions properly
        put("game_difficulty", c -> StringValue.of(c.s.getServer().getDifficulty().getTranslationKey()));
        put("game_hardcore", c -> new NumericValue(c.s.getServer().isHardcore()));
        //todo figure out
        //put("game_storage_format", c -> StringValue.of(c.s.getServer().getSaveProperties().getFormatName(c.s.getServer().getSaveProperties().getVersion())));
        put("game_mode", c -> StringValue.of(c.s.getServer().getGameType().getName()));
        put("game_max_players", c -> new NumericValue(c.s.getServer().getMaxPlayers()));
        put("game_view_distance", c -> new NumericValue(c.s.getServer().getPlayerList().getViewDistance()));
        put("game_mod_name", c -> StringValue.of(c.s.getServer().getServerModName()));
        put("game_version", c -> StringValue.of(c.s.getServer().getMinecraftVersion()));
        //todo game stability
        //put("game_stable", c -> BooleanValue.of(SharedConstants.getGameVersion().isStable()));
        //put("game_data_version", c->NumericValue.of(SharedConstants.getGameVersion().getWorldVersion()));
        //put("game_pack_version", c->NumericValue.of(SharedConstants.getGameVersion().getPackVersion()));

        //todo ip
        //put("server_ip", c -> StringValue.of(c.s.getServer().ip?()));
        put("server_whitelisted", c -> new NumericValue(c.s.getServer().getPlayerList().isWhiteListEnabled()));
        put("server_whitelist", c -> {
            MapValue whitelist = new MapValue(Collections.emptyList());
            for (String s: c.s.getServer().getPlayerList().getWhitelistedPlayerNames())
            {
                whitelist.append(StringValue.of(s));
            }
            return whitelist;
        });
        put("server_banned_players", c -> {
            MapValue whitelist = new MapValue(Collections.emptyList());
            for (String s: c.s.getServer().getPlayerList().getBannedPlayers().getKeys())
            {
                whitelist.append(StringValue.of(s));
            }
            return whitelist;
        });
        put("server_banned_ips", c -> {
            MapValue whitelist = new MapValue(Collections.emptyList());
            for (String s: c.s.getServer().getPlayerList().getBannedIPs().getKeys())
            {
                whitelist.append(StringValue.of(s));
            }
            return whitelist;
        });
        //todo dev env (can it be done?)
        //put("server_dev_environment", c-> new NumericValue(FabricLoader.getInstance().isDevelopmentEnvironment()));

        put("java_max_memory", c -> new NumericValue(Runtime.getRuntime().maxMemory()));
        put("java_allocated_memory", c -> new NumericValue(Runtime.getRuntime().totalMemory()));
        put("java_used_memory", c -> new NumericValue(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
        put("java_cpu_count", c -> new NumericValue(Runtime.getRuntime().availableProcessors()));
        put("java_version", c -> StringValue.of(System.getProperty("java.version")));
        put("java_bits", c -> {
            for (String property : new String[]{"sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch"})
            {
                String value = System.getProperty(property);
                if (value != null && value.contains("64"))
                    return new NumericValue(64);

            }
            return new NumericValue(32);
        });
        put("java_system_cpu_load", c -> {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
                    OperatingSystemMXBean.class);
            return new NumericValue(osBean.getSystemCpuLoad());
        });
        put("java_process_cpu_load", c -> {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
                    OperatingSystemMXBean.class);
            return new NumericValue(osBean.getProcessCpuLoad());
        });
        put("world_carpet_rules", c -> {
            Map<Value, Value> rules = new HashMap<>();
            Map<String, Field> carpetRules = CarpetSettings.getRules();
            carpetRules.forEach((k,v)->{
                rules.put(StringValue.of(k), StringValue.of(v.toString()));//todo check if this works...
            });
            return MapValue.wrap(rules);
        });
        put("world_gamerules", c->{
            Map<Value, Value> rules = new HashMap<>();
            final GameRules gameRules = c.s.getEntityWorld().getGameRules();
            Arrays.stream(gameRules.getRules()).collect(Collectors.toList()).forEach(gr->rules.put(StringValue.of(gr), StringValue.of(gameRules.getString(gr))));

            return MapValue.wrap(rules);
        });
        put("scarpet_version", c -> StringValue.of(CarpetSettings.carpetVersion));

    }};
    public static Value get(String what, CarpetContext cc)
    {
        return options.getOrDefault(what, c -> null).apply(cc);
    }
    public static Value getAll(CarpetContext cc)
    {
        return MapValue.wrap(options.entrySet().stream().collect(Collectors.toMap(e -> new StringValue(e.getKey()), e -> e.getValue().apply(cc))));
    }

}
