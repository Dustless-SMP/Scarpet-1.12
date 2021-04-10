package carpet.script;

import carpet.CarpetServer;
import carpet.helpers.TickSpeed;
import carpet.script.exception.InternalExpressionException;
import carpet.script.utils.FixedCommandSource;
import carpet.script.value.BlockValue;
import carpet.script.value.EntityValue;
import carpet.script.value.FunctionValue;
import carpet.script.value.ListValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import carpet.utils.Messenger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CarpetEventServer
{
    public final List<ScheduledCall> scheduledCalls = new LinkedList<>();
    public final MinecraftServer server;
    private static final List<Value> NOARGS = Collections.emptyList();
    public final Map<String, Event> customEvents = new HashMap<>();

    public static class Callback
    {
        public final String host;
        public final String optionalTarget;
        public final FunctionValue function;
        public final List<Value> parametrizedArgs;


        public Callback(String host, String target, FunctionValue function, List<Value> parametrizedArgs)
        {
            this.host = host;
            this.function = function;
            this.optionalTarget = target;
            this.parametrizedArgs = parametrizedArgs==null?NOARGS:parametrizedArgs;
        }

        /**
         * Used also in entity events
         * @param sender - entity command source
         * @param runtimeArgs = options
         */
        public boolean execute(ICommandSender sender, List<Value> runtimeArgs)
        {
            if (!this.parametrizedArgs.isEmpty())
            {
                runtimeArgs = new ArrayList<>(runtimeArgs);
                runtimeArgs.addAll(this.parametrizedArgs);
            }
            return CarpetServer.scriptServer.runEventCall(
                    sender.withLevel(0),
                    host, optionalTarget, function, runtimeArgs
            );
        }

        /**
         * Used also in entity events
         * @param sender - sender of the signal
         * @param optionalRecipient - optional target player argument
         * @param runtimeArgs = options
         */
        public int signal(ICommandSender sender, EntityPlayerMP optionalRecipient, List<Value> runtimeArgs)
        {
            // recipent of the call doesn't match the handlingHost
            if (optionalRecipient != null && !optionalRecipient.getName().equals(optionalTarget))
                return 0;
            List<Value> args = runtimeArgs;
            if (!this.parametrizedArgs.isEmpty())
            {
                args = new ArrayList<>(runtimeArgs);
                args.addAll(this.parametrizedArgs);
            }
            return CarpetServer.scriptServer.signal(sender, optionalRecipient, host, function, args, false);
        }


        @Override
        public String toString()
        {
            return function.getString()+((host==null)?"":"(from "+host+(optionalTarget == null?"":"/"+optionalTarget)+")");
        }
        public static class Signature
        {
            String function;
            String host;
            String target;
            public Signature(String fun, String h, String t)
            {
                function = fun;
                host = h;
                target = t;
            }
        }
        public static Signature fromString(String str)
        {
            Pattern find = Pattern.compile("(\\w+)(?:\\(from (\\w+)(?:/(\\w+))?\\))?");
            Matcher matcher = find.matcher(str);
            if(matcher.matches())
            {
                return new Signature(matcher.group(1), matcher.group(2), matcher.group(3));
            }
            return new Signature(str, null, null);
        }
    }

    public static class ScheduledCall extends Callback
    {

        private final CarpetContext ctx;
        public long dueTime;

        public ScheduledCall(CarpetContext context, FunctionValue function, List<Value> args, long dueTime)
        {
            // ignoring target as we will be always calling self
            super(context.host.getName(), null, function, args);
            this.ctx = context;
            this.dueTime = dueTime;
        }

        /**
         * used in scheduled calls
         */
        public void execute()
        {
            CarpetServer.scriptServer.runScheduledCall(ctx.origin, ctx.s, host, (CarpetScriptHost) ctx.host, function, parametrizedArgs);
        }


    }

    public static class CallbackList
    {

        public final List<Callback> callList;
        public final int reqArgs;
        final boolean isSystem;
        final boolean isGlobalOnly;
        final boolean perPlayerDistribution;

        public CallbackList(int reqArgs, boolean isSystem, boolean isGlobalOnly)
        {
            this.callList = new ArrayList<>();
            this.reqArgs = reqArgs;
            this.isSystem = isSystem;
            this.isGlobalOnly = isGlobalOnly;
            perPlayerDistribution = isSystem && !isGlobalOnly;
        }

        /**
         * Handles only built-in events from the events system
         * @param argumentSupplier
         * @param cmdSourceSupplier
         */
        public void call(Supplier<List<Value>> argumentSupplier, Supplier<ICommandSender> cmdSourceSupplier)
        {
            if (callList.size() > 0)
            {
                List<Value> argv = argumentSupplier.get(); // empty for onTickDone
                ICommandSender source;
                try
                {
                     source = cmdSourceSupplier.get();
                }
                catch (NullPointerException noReference) // todo figure out what happens when closing.
                {
                    return;
                }
                String nameCheck = perPlayerDistribution?source.getName():null;
                assert argv.size() == reqArgs;
                List<Callback> fails = new ArrayList<>();
                for (Callback call: callList)
                {
                    // supressing calls where target player hosts simply don't match
                    // handling global hosts with player targets is left to when the host is resolved (few calls deeper).
                    if (nameCheck != null && call.optionalTarget != null && !nameCheck.equals(call.optionalTarget)) continue;
                    if (!call.execute(source, argv)) fails.add(call);
                }
                for (Callback call : fails) callList.remove(call);
            }
        }

        public int signal(ICommandSender sender, EntityPlayerMP optinoalReceipient, List<Value> callArg)
        {
            if (callList.isEmpty()) return 0;
            //List<Callback> fails = new ArrayList<>();
            // skipping fails on purpose - its a player induced call.
            int successes = 0;
            for (Callback call: callList)
            {
                successes +=  Math.max(0, call.signal(sender, optinoalReceipient, callArg));
            }
            //for (Callback call : fails) callList.remove(call);
            return successes;
        }

        public boolean addFromExternal(ICommandSender source, String hostName, String funName, Consumer<ScriptHost> hostOnEventHandler)
        {
            ScriptHost host = CarpetServer.scriptServer.getHostByName(hostName);
            if (host == null)
            {
                // impossible call to add
                Messenger.m(source, "r Unknown app "+hostName);
                return false;
            }
            hostOnEventHandler.accept(host);
            FunctionValue udf = host.getFunction(funName);
            if (udf == null || udf.getArguments().size() != reqArgs)
            {
                // call won't match arguments
                Messenger.m(source, "r Callback doesn't expect required number of arguments: "+reqArgs);
                return false;
            }
            String target = null;
            if (host.isPerUser())
            {
                try
                {
                    target = source.getName();
                }
                catch (Exception e)
                {
                    Messenger.m(source, "r Cannot add event to a player scoped app from a command without a player context");
                    return false;
                }
            }
            //all clear
            //remove duplicates

            removeEventCall(hostName, target, udf.getString());
            callList.add(new Callback(hostName, target, udf, null));
            return true;
        }
        public boolean addEventCallInternal(ScriptHost host, FunctionValue function, List<Value> args)
        {
            if (function == null || (function.getArguments().size() - args.size()) != reqArgs)
            {
                return false;
            }
            //all clear
            //remove duplicates
            removeEventCall(host.getName(), host.user, function.getString());
            callList.add(new Callback(host.getName(), host.user, function, args));
            return true;
        }

        public void removeEventCall(String hostName, String target, String funName)
        {
            callList.removeIf((c)->  c.function.getString().equals(funName)
                    && (Objects.equals(c.host, hostName))
                    && (Objects.equals(c.optionalTarget, target))
            );
        }

        public void removeAllCalls(CarpetScriptHost host)
        {
            callList.removeIf((c)-> (Objects.equals(c.host, host.getName()))
                    && (Objects.equals(c.optionalTarget, host.user)));
        }

        public void createChildEvents(CarpetScriptHost host)
        {
            List<Callback> copyCalls = new ArrayList<>();
            callList.forEach((c)->
            {
                if ((Objects.equals(c.host, host.getName()))
                    && c.optionalTarget == null)
                {
                    copyCalls.add(new Callback(c.host, host.user, c.function, c.parametrizedArgs));
                }
            });
            callList.addAll(copyCalls);
        }

        public void clearEverything()
        {
            callList.clear();
        }
    }

    public static class Event
    {
        public static final Map<String, Event> byName = new HashMap<>();
        public static List<Event> publicEvents(CarpetScriptServer server)
        {
            List<Event> events = byName.values().stream().filter(e -> e.isPublic).collect(Collectors.toList());
            if (server != null) events.addAll(server.events.customEvents.values());
            return events;
        }

        public static final Event START = new Event("server_starts", 0, true)
        {
            @Override
            public void onTick()
            {
                handler.call(Collections::emptyList, () -> new FixedCommandSource(null, this.name, new TextComponentString(this.name), null, null, new Vec3d(0, 0, 0), new Value[]{}, new ArrayList<>()));
            }
        };

        public static final Event SHUTDOWN = new Event("server_shuts_down", 0, true)
        {
            @Override
            public void onTick()
            {
                handler.call(Collections::emptyList, () -> new FixedCommandSource(null, this.name, new TextComponentString(this.name), null, null, new Vec3d(0, 0, 0), new Value[]{}, new ArrayList<>()));
            }
        };

        public static final Event TICK = new Event("tick", 0, true)
        {
            @Override
            public void onTick()
            {
                handler.call(Collections::emptyList, () -> new FixedCommandSource(null, this.name, new TextComponentString(this.name), null, null, new Vec3d(0, 0, 0), new Value[]{}, new ArrayList<>())
                );
            }
        };
        public static final Event NETHER_TICK = new Event("tick_nether", 0, true)
        {
            @Override
            public boolean deprecated()
            {
                return true;
            }

            @Override
            public void onTick()
            {
                handler.call(Collections::emptyList, () -> new FixedCommandSource(null, this.name, new TextComponentString(this.name), null, null, new Vec3d(0, 0, 0), new Value[]{}, new ArrayList<>())
                );
            }
        };
        public static final Event ENDER_TICK = new Event("tick_ender", 0, true)
        {
            @Override
            public boolean deprecated()
            {
                return true;
            }
            @Override
            public void onTick()
            {
                handler.call(Collections::emptyList, () -> new FixedCommandSource(null, this.name, new TextComponentString(this.name), null, null, new Vec3d(0, 0, 0), new Value[]{}, new ArrayList<>()));
            }
        };
        public static final Event CHUNK_GENERATED = new Event("chunk_generated", 2, true)
        {
            @Override
            public void onChunkGenerated(WorldServer world, Chunk chunk)
            {
                handler.call( () -> Arrays.asList(new NumericValue((long) chunk.x << 4), new NumericValue((long) chunk.z << 4)),
                        () ->  new FixedCommandSource(world, this.name, new TextComponentString(this.name), world.getMinecraftServer(),null, new Vec3d(chunk.x << 4, 0,  chunk.z << 4), new Value[]{}, new ArrayList<>()));
            }
        };

        public static final Event PLAYER_JUMPS = new Event("player_jumps", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), () -> player);
            }
        };
        public static final Event PLAYER_DEPLOYS_ELYTRA = new Event("player_deploys_elytra", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), () -> player);
            }
        };
        public static final Event PLAYER_WAKES_UP = new Event("player_wakes_up", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), () -> player);
            }
        };
        public static final Event PLAYER_ESCAPES_SLEEP = new Event("player_escapes_sleep", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), () -> player);
            }
        };
        public static final Event PLAYER_RIDES = new Event("player_rides", 5, false)
        {
            @Override
            public void onMountControls(EntityPlayerMP player, float strafeSpeed, float forwardSpeed, boolean jumping, boolean sneaking)
            {
                handler.call( () -> Arrays.asList(new EntityValue(player),
                        new NumericValue(forwardSpeed), new NumericValue(strafeSpeed), new NumericValue(jumping), new NumericValue(sneaking)
                ), () -> player);
            }
        };
        public static final Event PLAYER_USES_ITEM = new Event("player_uses_item", 3, false)
        {
            @Override
            public void onItemAction(EntityPlayerMP player, EnumHand enumhand, ItemStack itemstack)
            {
                handler.call( () ->
                {
                    //ItemStack itemstack = player.getStackInHand(enumhand);
                    return Arrays.asList(
                            new EntityValue(player),
                            ListValue.fromItemStack(itemstack),
                            StringValue.of(enumhand == EnumHand.MAIN_HAND ? "mainhand" : "offhand")
                    );
                }, () -> player);
            }
        };
        public static final Event PLAYER_CLICKS_BLOCK = new Event("player_clicks_block", 3, false)
        {
            @Override
            public void onBlockAction(EntityPlayerMP player, BlockPos blockpos, EnumFacing facing)
            {
                handler.call( () ->
                {
                    return Arrays.asList(
                            new EntityValue(player),
                            new BlockValue(null, player.getServerWorld(), blockpos),
                            StringValue.of(facing.getName())
                    );
                }, ()->player);
            }
        };
        /*todo block hit result
        public static final Event PLAYER_RIGHT_CLICKS_BLOCK = new Event("player_right_clicks_block", 6, false)
        {
            @Override
            public void onBlockHit(EntityPlayerMP player, EnumHand enumhand, BlockHitResult hitRes)//ItemStack itemstack, EnumHand enumhand, BlockPos blockpos, EnumFacing enumfacing, Vec3d vec3d)
            {
                handler.call( () ->
                {
                    ItemStack itemstack = player.getActiveItemStack(enumhand);
                    BlockPos blockpos = hitRes.getBlockPos();
                    EnumFacing enumfacing = hitRes.getSide();
                    Vec3d vec3d = hitRes.getPos().subtract(blockpos.getX(), blockpos.getY(), blockpos.getZ());
                    return Arrays.asList(
                            new EntityValue(player),
                            ListValue.fromItemStack(itemstack),
                            StringValue.of(enumhand == EnumHand.MAIN_HAND ? "mainhand" : "offhand"),
                            new BlockValue(null, player.getServerWorld(), blockpos),
                            StringValue.of(enumfacing.getName()),
                            ListValue.of(
                                    new NumericValue(vec3d.x),
                                    new NumericValue(vec3d.y),
                                    new NumericValue(vec3d.z)
                            )
                    );
                }, ()->player);
            }
        };
        
        public static final Event PLAYER_INTERACTS_WITH_BLOCK = new Event("player_interacts_with_block", 5, false)
        {
            @Override
            public void onBlockHit(EntityPlayerMP player, EnumHand enumhand, BlockHitResult hitRes)
            {
                handler.call( () ->
                {
                    BlockPos blockpos = hitRes.getBlockPos();
                    EnumFacing enumfacing = hitRes.getSide();
                    Vec3d vec3d = hitRes.getPos().subtract(blockpos.getX(), blockpos.getY(), blockpos.getZ());
                    return Arrays.asList(
                            new EntityValue(player),
                            StringValue.of(enumhand == EnumHand.MAIN_HAND ? "mainhand" : "offhand"),
                            new BlockValue(null, player.getServerWorld(), blockpos),
                            StringValue.of(enumfacing.getName()),
                            ListValue.of(
                                    new NumericValue(vec3d.x),
                                    new NumericValue(vec3d.y),
                                    new NumericValue(vec3d.z)
                            )
                    );
                }, ()->player);
            }
        };

         */
        public static final Event PLAYER_PLACES_BLOCK = new Event("player_places_block", 4, false)
        {
            @Override
            public void onBlockPlaced(EntityPlayerMP player, BlockPos pos, EnumHand enumhand, ItemStack itemstack)
            {
                handler.call( () -> Arrays.asList(
                        new EntityValue(player),
                        ListValue.fromItemStack(itemstack),
                        StringValue.of(enumhand == EnumHand.MAIN_HAND ? "mainhand" : "offhand"),
                        new BlockValue(null, player.getServerWorld(), pos)
                ), ()->player);
            }
        };
        public static final Event PLAYER_BREAK_BLOCK = new Event("player_breaks_block", 2, false)
        {
            @Override
            public void onBlockBroken(EntityPlayerMP player, BlockPos pos, IBlockState previousBS)
            {
                handler.call(
                        () -> Arrays.asList(new EntityValue(player), new BlockValue(previousBS, player.getServerWorld(), pos)),
                        ()->player
                );
            }
        };
        public static final Event PLAYER_INTERACTS_WITH_ENTITY = new Event("player_interacts_with_entity", 3, false)
        {
            @Override
            public void onEntityHandAction(EntityPlayerMP player, Entity entity, EnumHand enumhand)
            {
                handler.call( () -> Arrays.asList(
                        new EntityValue(player), new EntityValue(entity), StringValue.of(enumhand==EnumHand.MAIN_HAND?"mainhand":"offhand")
                ), ()->player);
            }
        };
        /*todo trade event
        public static final Event PLAYER_TRADES = new Event("player_trades", 5, false)
        {
            @Override
            public void onTrade(EntityPlayerMP player, Merchant merchant, TradeOffer tradeOffer)
            {
                handler.call( () -> Arrays.asList(
                        new EntityValue(player),
                        merchant instanceof MerchantEntity ? new EntityValue((MerchantEntity) merchant) : Value.NULL,
                        ValueConversions.of(tradeOffer.getOriginalFirstBuyItem()),
                        ValueConversions.of(tradeOffer.getSecondBuyItem()),
                        ValueConversions.of(tradeOffer.getSellItem())
                ), ()->player);
            }
        };
        */
        public static final Event PLAYER_PICKS_UP_ITEM = new Event("player_picks_up_item", 2, false)
        {
            @Override
            public void onItemAction(EntityPlayerMP player, EnumHand enumhand, ItemStack itemstack) {
                handler.call( () -> Arrays.asList(new EntityValue(player), ListValue.fromItemStack(itemstack)), ()->player);
            }
        };

        public static final Event PLAYER_ATTACKS_ENTITY = new Event("player_attacks_entity", 2, false)
        {
            @Override
            public void onEntityHandAction(EntityPlayerMP player, Entity entity, EnumHand enumhand)
            {
                handler.call( () -> Arrays.asList(new EntityValue(player), new EntityValue(entity)), ()->player);
            }
        };
        public static final Event PLAYER_STARTS_SNEAKING = new Event("player_starts_sneaking", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_STOPS_SNEAKING = new Event("player_stops_sneaking", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_STARTS_SPRINTING = new Event("player_starts_sprinting", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_STOPS_SPRINTING = new Event("player_stops_sprinting", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };

        public static final Event PLAYER_RELEASED_ITEM = new Event("player_releases_item", 3, false)
        {
            @Override
            public void onItemAction(EntityPlayerMP player, EnumHand enumhand, ItemStack itemstack)
            {
                // this.getStackInHand(this.getActiveHand()), this.activeItemStack)
                handler.call( () ->
                        Arrays.asList(
                                new EntityValue(player),
                                ListValue.fromItemStack(itemstack),
                                StringValue.of(enumhand == EnumHand.MAIN_HAND ? "mainhand" : "offhand")
                        ), ()->player);
            }
        };
        public static final Event PLAYER_FINISHED_USING_ITEM = new Event("player_finishes_using_item", 3, false)
        {
            @Override
            public void onItemAction(EntityPlayerMP player, EnumHand enumhand, ItemStack itemstack)
            {
                // this.getStackInHand(this.getActiveHand()), this.activeItemStack)
                handler.call( () ->
                        Arrays.asList(
                                new EntityValue(player),
                                ListValue.fromItemStack(itemstack),
                                new StringValue(enumhand == EnumHand.MAIN_HAND ? "mainhand" : "offhand")
                        ), ()->player);
            }
        };
        public static final Event PLAYER_DROPS_ITEM = new Event("player_drops_item", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_DROPS_STACK = new Event("player_drops_stack", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_CHOOSES_RECIPE = new Event("player_chooses_recipe", 3, false)
        {
            @Override
            public void onRecipeSelected(EntityPlayerMP player, IRecipe recipe, boolean fullStack)
            {
                handler.call( () ->
                        Arrays.asList(
                                new EntityValue(player),
                                StringValue.of(recipe.getIngredients().toString() + recipe.getRecipeOutput().getTranslationKey()),
                                new NumericValue(fullStack)
                        ), ()->player);
            }
        };
        public static final Event PLAYER_SWITCHES_SLOT = new Event("player_switches_slot", 3, false)
        {
            @Override
            public void onSlotSwitch(EntityPlayerMP player, int from, int to)
            {
                if (from == to) return; // initial slot update
                handler.call( () ->
                        Arrays.asList(
                                new EntityValue(player),
                                new NumericValue(from),
                                new NumericValue(to)
                        ), ()->player);
            }
        };
        public static final Event PLAYER_SWAPS_HANDS = new Event("player_swaps_hands", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_SWINGS_HAND = new Event("player_swings_hand", 2, false)
        {
            @Override
            public void onHandAction(EntityPlayerMP player, EnumHand hand)
            {
                handler.call( () -> Arrays.asList(
                            new EntityValue(player),
                            StringValue.of(hand == EnumHand.MAIN_HAND ? "mainhand" : "offhand")
                        )
                        , ()->player);
            }
        };
        public static final Event PLAYER_TAKES_DAMAGE = new Event("player_takes_damage", 4, false)
        {
            @Override
            public void onDamage(Entity target, float amount, DamageSource source)
            {
                handler.call( () ->
                        Arrays.asList(
                                 new EntityValue(target),
                                 new NumericValue(amount),
                                 StringValue.of(source.damageType),
                                 source.getTrueSource()==null?Value.NULL:new EntityValue(source.getTrueSource())
                        ), ()->target);
            }
        };
        public static final Event PLAYER_DEALS_DAMAGE = new Event("player_deals_damage", 3, false)
        {
            @Override
            public void onDamage(Entity target, float amount, DamageSource source)
            {
                handler.call( () ->
                        Arrays.asList(new EntityValue(source.getTrueSource()), new NumericValue(amount), new EntityValue(target)),
                        source::getTrueSource
                );
            }
        };
        public static final Event PLAYER_COLLIDES_WITH_ENTITY = new Event("player_collides_with_entity", 2, false)
        {
            @Override
            public void onEntityHandAction(EntityPlayerMP player, Entity entity, EnumHand enumhand) {
                handler.call( () -> Arrays.asList(new EntityValue(player), new EntityValue(entity)), ()->player);
            }
        };

        public static final Event PLAYER_DIES = new Event("player_dies", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_RESPAWNS = new Event("player_respawns", 1, false)
        {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_CHANGES_DIMENSION = new Event("player_changes_dimension", 5, false)
        {
            @Override
            public void onDimensionChange(EntityPlayerMP player, Vec3d from, Vec3d to, DimensionType fromDim, DimensionType dimTo)
            {
                // eligibility already checked in mixin
                Value fromValue = ListValue.fromTriple(from.x, from.y, from.z);
                Value toValue = (to == null)?Value.NULL:ListValue.fromTriple(to.x, to.y, to.z);
                Value fromDimStr = new StringValue(fromDim.getName());
                Value toDimStr = new StringValue(dimTo.getName());

                handler.call( () -> Arrays.asList(new EntityValue(player), fromValue, fromDimStr, toValue, toDimStr), ()->player);
            }
        };
        public static final Event PLAYER_CONNECTS = new Event("player_connects", 1, false) {
            @Override
            public void onPlayerEvent(EntityPlayerMP player)
            {
                handler.call( () -> Collections.singletonList(new EntityValue(player)), ()->player);
            }
        };
        public static final Event PLAYER_DISCONNECTS = new Event("player_disconnects", 2, false) {
            @Override
            public void onPlayerMessage(EntityPlayerMP player, String message)
            {
                handler.call( () -> Arrays.asList(new EntityValue(player), new StringValue(message)), ()->player);
            }
        };
        /*todo statistics
        public static final Event STATISTICS = new Event("statistic", 4, false)
        {
            private <T> Identifier getStatId(Stat<T> stat)
            {
                return stat.getType().getRegistry().getId(stat.getValue());
            }
            private final Set<Identifier> skippedStats = new HashSet<Identifier>(){{
                add(Stats.TIME_SINCE_DEATH);
                add(Stats.TIME_SINCE_REST);
                add(Stats.PLAY_ONE_MINUTE);
            }};
            @Override
            public void onPlayerStatistic(EntityPlayerMP player, Stat<?> stat, int amount)
            {
                Identifier id = getStatId(stat);
                if (skippedStats.contains(id)) return;
                handler.call( () -> Arrays.asList(
                        new EntityValue(player),
                        StringValue.of(NBTSerializableValue.nameFromRegistryId(Registry.STAT_TYPE.getId(stat.getType()))),
                        StringValue.of(NBTSerializableValue.nameFromRegistryId(id)),
                        new NumericValue(amount)
                ), ()->player);
            }
        };
        */
        public static final Event LIGHTNING = new Event("lightning", 2, true)
        {
            @Override
            public void onWorldEventFlag(WorldServer world, BlockPos pos, int flag)
            {
                handler.call(
                        () -> Arrays.asList(
                                new BlockValue(null, world, pos),
                                flag>0?Value.TRUE:Value.FALSE
                        ), () -> CarpetServer.minecraft_server
                );
            }
        };
        ///* todo carpet rule change
        public static final Event CARPET_RULE_CHANGES = new Event("carpet_rule_changes", 2, true)
        {
            @Override
            public void onCarpetRuleChanges(Field rule, ICommandSender source) {
                handler.call(() -> Collections.singletonList(new StringValue(rule.getName())), () -> source);
            }
        };
        //*/
        /*todo entity load event
        public static String getEntityLoadEventName(EntityType<? extends Entity> et)
        {
            return "entity_loaded_" + ValueConversions.of(Registry.ENTITY_TYPE.getId(et)).getString();
        }

        public static final Map<EntityType<? extends Entity>, Event> ENTITY_LOAD= new HashMap<EntityType<? extends Entity>, Event>() {{
            EntityType.get("zombie");
            Registry.ENTITY_TYPE.forEach(et -> {
                put(et, new Event(getEntityLoadEventName(et), 1, true, false)
                {
                    @Override
                    public void onEntityAction(Entity entity)
                    {
                        handler.call(
                                () -> Collections.singletonList(new EntityValue(entity)),
                                () -> CarpetServer.minecraft_server.getCommandSource().withWorld((WorldServer) entity.world).withLevel(CarpetSettings.runPermissionLevel)
                        );
                    }
                });
            });
        }};
        */
        // on projectile thrown (arrow from bows, crossbows, tridents, snoballs, e-pearls

        public final String name;

        public final CallbackList handler;
        public final boolean globalOnly;
        public final boolean isPublic; // public events can be targetted with __on_<event> defs
        public Event(String name, int reqArgs, boolean isGlobalOnly)
        {
            this(name, reqArgs, isGlobalOnly, true);
        }
        public Event(String name, int reqArgs, boolean isGlobalOnly, boolean isPublic)
        {
            this.name = name;
            this.handler = new CallbackList(reqArgs, true, isGlobalOnly);
            this.globalOnly = isGlobalOnly;
            this.isPublic = isPublic;
            byName.put(name, this);
        }

        public static List<Event> getAllEvents(CarpetScriptServer server, Predicate<Event> predicate)
        {
            List<CarpetEventServer.Event> eventList = new ArrayList<>(CarpetEventServer.Event.byName.values());
            eventList.addAll(server.events.customEvents.values());
            if (predicate == null) return eventList;
            return eventList.stream().filter(predicate).collect(Collectors.toList());
        }

        public static Event getEvent(String name, CarpetScriptServer server)
        {
            if (byName.containsKey(name)) return byName.get(name);
            return server.events.customEvents.get(name);
        }

        public static Event getOrCreateCustom(String name, CarpetScriptServer server)
        {
            Event event = getEvent(name, server);
            if (event != null) return event;
            return new Event(name, server);
        }

        public static void removeAllHostEvents(CarpetScriptHost host)
        {
            byName.values().forEach((e) -> e.handler.removeAllCalls(host));
            host.getScriptServer().events.customEvents.values().forEach((e) -> e.handler.removeAllCalls(host));
        }

        public static void transferAllHostEventsToChild(CarpetScriptHost host)
        {
            byName.values().forEach((e) -> e.handler.createChildEvents(host));
            host.getScriptServer().events.customEvents.values().forEach((e) -> e.handler.createChildEvents(host));
        }

        public static void clearAllBuiltinEvents()
        {
            byName.values().forEach(e -> e.handler.clearEverything());
        }

        // custom event constructor
        private Event(String name, CarpetScriptServer server)
        {
            this.name = name;
            this.handler = new CallbackList(1, false, false);
            this.globalOnly = false;
            this.isPublic = true;
            server.events.customEvents.put(name, this);
        }

        //handle_event('event', function...)
        //signal_event('event', player or null, args.... ) -> number of apps notified
        public boolean isNeeded() {
            return handler.callList.size() > 0;
        }
        public boolean deprecated() {return false;}
        //stubs for calls just to ease calls in vanilla code so they don't need to deal with scarpet value types
        public void onTick() { }
        //todo calling thee event
        public void onChunkGenerated(WorldServer world, Chunk chunk) { }
        public void onPlayerEvent(EntityPlayerMP player) { }
        public void onPlayerMessage(EntityPlayerMP player, String message) { }
        //public void onPlayerStatistic(EntityPlayerMP player, Stat<?> stat, int amount) { }
        public void onMountControls(EntityPlayerMP player, float strafeSpeed, float forwardSpeed, boolean jumping, boolean sneaking) { }
        public void onItemAction(EntityPlayerMP player, EnumHand enumhand, ItemStack itemstack) { }
        public void onBlockAction(EntityPlayerMP player, BlockPos blockpos, EnumFacing facing) { }
        //public void onBlockHit(EntityPlayerMP player, EnumHand enumhand, BlockHitResult hitRes) { }
        public void onBlockBroken(EntityPlayerMP player, BlockPos pos, IBlockState previousBS) { }
        public void onBlockPlaced(EntityPlayerMP player, BlockPos pos, EnumHand enumhand, ItemStack itemstack) { }
        public void onEntityHandAction(EntityPlayerMP player, Entity entity, EnumHand enumhand) { }
        public void onHandAction(EntityPlayerMP player, EnumHand enumhand) { }
        public void onEntityAction(Entity entity) { }
        public void onDimensionChange(EntityPlayerMP player, Vec3d from, Vec3d to, DimensionType fromDim, DimensionType dimTo) {}
        public void onDamage(Entity target, float amount, DamageSource source) { }
        public void onRecipeSelected(EntityPlayerMP player, IRecipe recipe, boolean fullStack) {}
        public void onSlotSwitch(EntityPlayerMP player, int from, int to) {}
        //public void onTrade(EntityPlayerMP player, Merchant merchant, TradeOffer tradeOffer) {}


        public void onWorldEvent(WorldServer world, BlockPos pos) { }
        public void onWorldEventFlag(WorldServer world, BlockPos pos, int flag) { }
        public void onCarpetRuleChanges(Field rule, ICommandSender source) { }
    }


    public CarpetEventServer(MinecraftServer server)
    {
        this.server = server;
        Event.clearAllBuiltinEvents();
    }

    public void tick()
    {
        if (!TickSpeed.process_entities)
            return;
        Iterator<ScheduledCall> eventIterator = scheduledCalls.iterator();
        List<ScheduledCall> currentCalls = new ArrayList<>();
        while(eventIterator.hasNext())
        {
            ScheduledCall call = eventIterator.next();
            call.dueTime--;
            if (call.dueTime <= 0)
            {
                currentCalls.add(call);
                eventIterator.remove();
            }
        }
        for (ScheduledCall call: currentCalls)
        {
            call.execute();
        }

    }
    public void scheduleCall(CarpetContext context, FunctionValue function, List<Value> args, long due)
    {
        scheduledCalls.add(new ScheduledCall(context, function, args, due));
    }

    public boolean addEventFromCommand(ICommandSender source, String event, String host, String funName)
    {
        Event ev = Event.getEvent(event, CarpetServer.scriptServer);
        if (ev == null)
        {
            return false;
        }
        boolean added = ev.handler.addFromExternal(source, host, funName, h -> onEventAddedToHost(ev, h));
        if (added) Messenger.m(source, "gi Added " + funName + " to " + event);
        return added;
    }

    public void addBuiltInEvent(String event, ScriptHost host, FunctionValue function, List<Value> args)
    {
        // this is globals only
        Event ev = Event.byName.get(event);
        onEventAddedToHost(ev, host);
        boolean success =  ev.handler.addEventCallInternal(host, function, args==null?NOARGS:args);
        if (!success) throw new InternalExpressionException("Global event "+event+" requires "+ev.handler.reqArgs+", not "+(function.getNumParams()-((args==null)?0:args.size())));
    }

    public boolean handleCustomEvent(String event, CarpetScriptHost host, FunctionValue function, List<Value> args)
    {
        Event ev = Event.getOrCreateCustom(event, host.getScriptServer());
        onEventAddedToHost(ev, host);
        return ev.handler.addEventCallInternal(host, function, args==null?NOARGS:args);
    }

    public int signalEvent(String event, CarpetContext cc, EntityPlayerMP optionalTarget, List<Value> callArgs)
    {
        Event ev = Event.getEvent(event, ((CarpetScriptHost)cc.host).getScriptServer());
        if (ev == null) return -1;
        return ev.handler.signal(cc.s, optionalTarget, callArgs);
    }

    private void onEventAddedToHost(Event event, ScriptHost host)
    {
        if (event.deprecated()) host.issueDeprecation(event.name+" event");
        //return !(event.globalOnly && (host.perUser || host.parent != null));
    }

    public boolean removeEventFromCommand(ICommandSender source, String event, String funName)
    {
        Event ev = Event.getEvent(event, CarpetServer.scriptServer);
        if (ev == null)
        {
            Messenger.m(source, "r Unknown event: " + event);
            return false;
        }
        Callback.Signature call = Callback.fromString(funName);
        ev.handler.removeEventCall(call.host, call.target, call.function);
        // could verified if actually removed
        Messenger.m(source, "gi Removed event: " + funName + " from "+event);
        return true;
    }
    public boolean removeBuiltInEvent(String event, CarpetScriptHost host)
    {
        Event ev = Event.getEvent(event, host.getScriptServer());
        if (ev == null) return false;
        ev.handler.removeAllCalls(host);
        return true;
    }

    public void removeBuiltInEvent(String event, CarpetScriptHost host, String funName)
    {
        Event ev = Event.getEvent(event, host.getScriptServer());
        if (ev != null) ev.handler.removeEventCall(host.getName(), host.user, funName);
    }

    public void removeAllHostEvents(CarpetScriptHost host)
    {
        // remove event handlers
        Event.removeAllHostEvents(host);
        if (host.isPerUser())
            for (ScriptHost child: host.userHosts.values()) Event.removeAllHostEvents((CarpetScriptHost) child);
        // remove scheduled calls
        scheduledCalls.removeIf(sc -> sc.host != null && sc.host.equals(host.getName()));
    }

    private Pair<String,String> decodeCallback(String funName)
    {
        Pattern find = Pattern.compile("(\\w+)\\(from (\\w+)\\)");
        Matcher matcher = find.matcher(funName);
        if(matcher.matches())
        {
            return Pair.of(matcher.group(2), matcher.group(1));
        }
        return Pair.of(null, funName);
    }
}
