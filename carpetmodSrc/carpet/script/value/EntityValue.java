package carpet.script.value;

import carpet.patches.EntityPlayerMPFake;
import carpet.script.argument.Vector3Argument;
import carpet.script.exception.InternalExpressionException;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.GameType;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


// TODO: decide whether copy(entity) should duplicate entity in the world.
public class EntityValue extends Value
{
    private Entity entity;

    public EntityValue(Entity e)
    {
        entity = e;
    }

    private static final Map<String, EntitySelector> selectorCache = new HashMap<>();
    /*todo entity selectors
    public static Collection<? extends Entity > getEntitiesFromSelector(ICommandSender source, String selector)
    {
        try
        {
            EntitySelector entitySelector = selectorCache.get(selector);
            if (entitySelector != null)
            {
                return entitySelector.(source.withMaxLevel(4));
            }
            entitySelector = new EntitySelectorReader(new StringReader(selector), true).read();
            selectorCache.put(selector, entitySelector);
            return entitySelector.getEntities(source.withMaxLevel(4));
        }
        catch (CommandSyntaxException e)
        {
            throw new InternalExpressionException("Cannot select entities from "+selector);
        }
    }
    */
    public Entity getEntity()
    {
        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP newPlayer = entity.getServer().getPlayerList().getPlayerByUUID(entity.getUniqueID());
            entity = newPlayer;
        }
        return entity;
    }

    public static EntityPlayerMP getPlayerByValue(MinecraftServer server, Value value)
    {
        EntityPlayerMP player = null;
        if (value instanceof EntityValue)
        {
            Entity e = ((EntityValue) value).getEntity();
            if (e instanceof EntityPlayerMP)
            {
                player = (EntityPlayerMP) e;
            }
        }
        else if (value.isNull())
        {
            return null;
        }
        else
        {
            String playerName = value.getString();
            player = server.getPlayerList().getPlayerByUsername(playerName);
        }
        return player;
    }

    public static String getPlayerNameByValue(Value value)
    {
        String playerName = null;
        if (value instanceof EntityValue)
        {
            Entity e = ((EntityValue) value).getEntity();
            if (e instanceof EntityPlayerMP)
            {
                playerName = e.getName();
            }
        }
        else if (value.isNull())
        {
            return null;
        }
        else
        {
            playerName = value.getString();
        }
        return playerName;
    }

    @Override
    public String getString()
    {
        return getEntity().getName();
    }

    @Override
    public boolean getBoolean()
    {
        return true;
    }

    @Override
    public boolean equals(Object v)
    {
        if (v instanceof EntityValue)
        {
            return getEntity().getEntityId()==((EntityValue) v).getEntity().getEntityId();
        }
        return super.equals((Value)v);
    }

    @Override
    public Value in(Value v)
    {
        if (v instanceof ListValue)
        {
            List<Value> values = ((ListValue) v).getItems();
            String what = values.get(0).getString();
            Value arg = null;
            if (values.size() == 2)
            {
                arg = values.get(1);
            }
            else if (values.size() > 2)
            {
                arg = ListValue.wrap(values.subList(1,values.size()));
            }
            return this.get(what, arg);
        }
        String what = v.getString();
        return this.get(what, null);
    }

    @Override
    public String getTypeString()
    {
        return "entity";
    }

    @Override
    public int hashCode()
    {
        return getEntity().hashCode();
    }

    /*todo entity class descriptors
    public static EntityClassDescriptor getEntityDescriptor(String who, MinecraftServer server)
    {
        EntityClassDescriptor eDesc = EntityClassDescriptor.byName.get(who);
        if (eDesc == null)
        {
            boolean positive = true;
            if (who.startsWith("!"))
            {
                positive = false;
                who = who.substring(1);
            }
            net.minecraft.tag.NBTBase<EntityType<?>> eTag = server.getTagManager().getEntityTypes().getTag(new Identifier(who));
            if (eTag == null) throw new InternalExpressionException(who+" is not a valid entity descriptor");
            if (positive)
            {
                return new EntityClassDescriptor(null, e -> eTag.contains(e.getType()) && e.isEntityAlive(), eTag.values().stream());
            }
            else
            {
                return new EntityClassDescriptor(null, e -> !eTag.contains(e.getType()) && e.isEntityAlive(), Registry.ENTITY_TYPE.stream().filter(et -> !eTag.contains(et)));
            }
        }
        return eDesc;
        //TODO add more here like search by tags, or type
        //if (who.startsWith('tag:'))
    }

    public static class EntityClassDescriptor
    {
        public EntityType<? extends Entity> directType;
        public Predicate<? super Entity> filteringPredicate;
        public List<EntityType<? extends  Entity>> typeList;
        public Value listValue;
        EntityClassDescriptor(EntityType<?> type, Predicate<? super Entity> predicate, List<EntityType<?>> types)
        {
            directType = type;
            filteringPredicate = predicate;
            typeList = types;
            listValue = (types==null)?Value.NULL:ListValue.wrap(types.stream().map(et -> StringValue.of(nameFromRegistryId(Registry.ENTITY_TYPE.getId(et)))).collect(Collectors.toList()));
        }
        EntityClassDescriptor(EntityType<?> type, Predicate<? super Entity> predicate, Stream<EntityType<?>> types)
        {
            this(type, predicate, types.collect(Collectors.toList()));
        }

        public final static Map<String, EntityClassDescriptor> byName = new HashMap<String, EntityClassDescriptor>() {{
            List<EntityType<?>> allTypes = Registry.ENTITY_TYPE.stream().collect(Collectors.toList());
            // nonliving types
            Set<EntityType<?>> projectiles = Sets.newHashSet(
                    EntityType.ARROW, EntityType.DRAGON_FIREBALL, EntityType.FIREWORK_ROCKET,
                    EntityType.FIREBALL, EntityType.LLAMA_SPIT, EntityType.SMALL_FIREBALL,
                    EntityType.SNOWBALL, EntityType.SPECTRAL_ARROW, EntityType.EGG,
                    EntityType.ENDER_PEARL, EntityType.EXPERIENCE_BOTTLE, EntityType.POTION,
                    EntityType.TRIDENT, EntityType.WITHER_SKULL, EntityType.FISHING_BOBBER
            );
            Set<EntityType<?>> deads = Sets.newHashSet(
                    EntityType.AREA_EFFECT_CLOUD, EntityType.BOAT, EntityType.END_CRYSTAL,
                    EntityType.EVOKER_FANGS, EntityType.EXPERIENCE_ORB, EntityType.EYE_OF_ENDER,
                    EntityType.FALLING_BLOCK, EntityType.ITEM, EntityType.ITEM_FRAME,
                    EntityType.LEASH_KNOT, EntityType.LIGHTNING_BOLT, EntityType.PAINTING,
                    EntityType.TNT

            );
            Set<EntityType<?>> minecarts = Sets.newHashSet(
                   EntityType.MINECART,  EntityType.CHEST_MINECART, EntityType.COMMAND_BLOCK_MINECART,
                    EntityType.FURNACE_MINECART, EntityType.HOPPER_MINECART,
                    EntityType.SPAWNER_MINECART, EntityType.TNT_MINECART
            );
            // living mob groups - non-defeault
            Set<EntityType<?>> undeads = Sets.newHashSet(
                    EntityType.STRAY, EntityType.SKELETON, EntityType.WITHER_SKELETON,
                    EntityType.ZOMBIE, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
                    EntityType.ZOMBIE_HORSE, EntityType.SKELETON_HORSE, EntityType.PHANTOM,
                    EntityType.WITHER, EntityType.ZOGLIN, EntityType.HUSK, EntityType.ZOMBIFIED_PIGLIN

            );
            Set<EntityType<?>> arthropods = Sets.newHashSet(
                    EntityType.BEE, EntityType.ENDERMITE, EntityType.SILVERFISH, EntityType.SPIDER,
                    EntityType.CAVE_SPIDER
            );
            Set<EntityType<?>> aquatique = Sets.newHashSet(
                    EntityType.GUARDIAN, EntityType.TURTLE, EntityType.COD, EntityType.DOLPHIN, EntityType.PUFFERFISH,
                    EntityType.SALMON, EntityType.SQUID, EntityType.TROPICAL_FISH
            );
            Set<EntityType<?>> illagers = Sets.newHashSet(
                    EntityType.PILLAGER, EntityType.ILLUSIONER, EntityType.VINDICATOR, EntityType.EVOKER,
                    EntityType.RAVAGER, EntityType.WITCH
            );

            Set<EntityType<?>> living = allTypes.stream().filter(et ->
                    !deads.contains(et) && !projectiles.contains(et) && !minecarts.contains(et)
            ).collect(Collectors.toSet());

            Set<EntityType<?>> regular = allTypes.stream().filter(et ->
                    living.contains(et) && !undeads.contains(et) && !arthropods.contains(et) && !aquatique.contains(et) && !illagers.contains(et)
            ).collect(Collectors.toSet());


            put("*", new EntityClassDescriptor(null, e -> true, allTypes) );
            put("valid", new EntityClassDescriptor(null, EntityPredicates.VALID_ENTITY, allTypes));
            put("!valid", new EntityClassDescriptor(null, e -> !e.isEntityAlive(), allTypes));

            put("living",  new EntityClassDescriptor(null, (e) -> (e instanceof EntityLiving && e.isEntityAlive()), allTypes.stream().filter(living::contains)));
            put("!living",  new EntityClassDescriptor(null, (e) -> (!(e instanceof EntityLiving) && e.isEntityAlive()), allTypes.stream().filter(et -> !living.contains(et))));

            put("projectile", new EntityClassDescriptor(null, (e) -> (e instanceof IProjectile && e.isEntityAlive()), allTypes.stream().filter(projectiles::contains)));
            put("!projectile", new EntityClassDescriptor(null, (e) -> (!(e instanceof IProjectile) && e.isEntityAlive()), allTypes.stream().filter(et -> !projectiles.contains(et) && !living.contains(et))));

            put("minecarts", new EntityClassDescriptor(null, (e) -> (e instanceof EntityMinecart && e.isEntityAlive()), allTypes.stream().filter(minecarts::contains)));
            put("!minecarts", new EntityClassDescriptor(null, (e) -> (!(e instanceof EntityMinecart) && e.isEntityAlive()), allTypes.stream().filter(et -> !minecarts.contains(et) && !living.contains(et))));


            // combat groups

            put("arthropod", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() == EntityGroup.ARTHROPOD && e.isEntityAlive()), allTypes.stream().filter(arthropods::contains)));
            put("!arthropod", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() != EntityGroup.ARTHROPOD && e.isEntityAlive()), allTypes.stream().filter(et -> !arthropods.contains(et) && living.contains(et))));

            put("undead", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() == EntityGroup.UNDEAD && e.isEntityAlive()), allTypes.stream().filter(undeads::contains)));
            put("!undead", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() != EntityGroup.UNDEAD && e.isEntityAlive()), allTypes.stream().filter(et -> !undeads.contains(et) && living.contains(et))));

            put("aquatic", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() == EntityGroup.AQUATIC && e.isEntityAlive()), allTypes.stream().filter(aquatique::contains)));
            put("!aquatic", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() != EntityGroup.AQUATIC && e.isEntityAlive()), allTypes.stream().filter(et -> !aquatique.contains(et) && living.contains(et))));

            put("illager", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() == EntityGroup.ILLAGER && e.isEntityAlive()), allTypes.stream().filter(illagers::contains)));
            put("!illager", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() != EntityGroup.ILLAGER && e.isEntityAlive()), allTypes.stream().filter(et -> !illagers.contains(et) && living.contains(et))));

            put("regular", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() == EntityGroup.DEFAULT && e.isEntityAlive()), allTypes.stream().filter(regular::contains)));
            put("!regular", new EntityClassDescriptor(null, e -> ((e instanceof EntityLiving) && ((EntityLiving) e).getGroup() != EntityGroup.DEFAULT && e.isEntityAlive()), allTypes.stream().filter(et -> !regular.contains(et) && living.contains(et))));

            for (Identifier typeId : Registry.ENTITY_TYPE.getIds())
            {
                EntityType<?> type  = Registry.ENTITY_TYPE.get(typeId);
                String mobType = ValueConversions.simplify(typeId);
                put(    mobType, new EntityClassDescriptor(type, EntityPredicates.VALID_ENTITY, Stream.of(type)));
                put("!"+mobType, new EntityClassDescriptor(null, (e) -> e.getType() != type  && e.isEntityAlive(), allTypes.stream().filter(et -> et != type)));
            }
            for (SpawnGroup catId : SpawnGroup.values())
            {
                String catStr = catId.getName();
                put(    catStr, new EntityClassDescriptor(null, e -> ((e.getType().getSpawnGroup() == catId) && e.isEntityAlive()), allTypes.stream().filter(et -> et.getSpawnGroup() == catId)));
                put("!"+catStr, new EntityClassDescriptor(null, e -> ((e.getType().getSpawnGroup() != catId) && e.isEntityAlive()), allTypes.stream().filter(et -> et.getSpawnGroup() != catId)));
            }
        }};

    }
    */

    public Value get(String what, Value arg)
    {
        if (!(featureAccessors.containsKey(what)))
            throw new InternalExpressionException("Unknown entity feature: "+what);
        try
        {
            return featureAccessors.get(what).apply(getEntity(), arg);
        }
        catch (NullPointerException npe)
        {
            throw new InternalExpressionException("Cannot fetch '"+what+"' with these arguments");
        }
    }
    private static final Map<String, EntityEquipmentSlot> inventorySlots = new HashMap<String, EntityEquipmentSlot>(){{
        put("mainhand", EntityEquipmentSlot.MAINHAND);
        put("offhand", EntityEquipmentSlot.OFFHAND);
        put("head", EntityEquipmentSlot.HEAD);
        put("chest", EntityEquipmentSlot.CHEST);
        put("legs", EntityEquipmentSlot.LEGS);
        put("feet", EntityEquipmentSlot.FEET);
    }};

    private static final Map<String, BiFunction<Entity, Value, Value>> featureAccessors = new HashMap<String, BiFunction<Entity, Value, Value>>() {{
        put("test", (e, a) -> a == null ? Value.NULL : new StringValue(a.getString()));//todo test
        put("uuid",(e, a) -> new StringValue(e.getUniqueID().toString()));
        put("id",(e, a) -> new NumericValue(e.getEntityId()));
        put("pos", (e, a) -> ListValue.of(new NumericValue(e.posX), new NumericValue(e.posY), new NumericValue(e.posZ)));
        put("location", (e, a) -> ListValue.of(new NumericValue(e.posX), new NumericValue(e.posY), new NumericValue(e.posZ), new NumericValue(e.rotationYaw), new NumericValue(e.rotationPitch)));
        put("x", (e, a) -> new NumericValue(e.posX));
        put("y", (e, a) -> new NumericValue(e.posY));
        put("z", (e, a) -> new NumericValue(e.posZ));
        put("motion", (e, a) ->
        {
            Vec3d velocity = e.getVelocity();
            return ListValue.of(new NumericValue(velocity.x), new NumericValue(velocity.y), new NumericValue(velocity.z));
        });
        put("motion_x", (e, a) -> new NumericValue(e.getVelocity().x));
        put("motion_y", (e, a) -> new NumericValue(e.getVelocity().y));
        put("motion_z", (e, a) -> new NumericValue(e.getVelocity().z));
        put("on_ground", (e, a) -> new NumericValue(e.onGround));
        put("name", (e, a) -> new StringValue(e.getName()));
        put("display_name", (e, a) -> new FormattedTextValue(e.getDisplayName()));
        put("command_name", (e, a) -> new NumericValue(e.cm_name()));//todo get other name
        put("custom_name", (e, a) -> e.hasCustomName()?new StringValue(e.getCustomNameTag()):Value.NULL);
        //todo check if cm_name is only way to get entity name
        put("type", (e, a) -> new StringValue(e.cm_name()));
        put("is_riding", (e, a) -> new NumericValue(e.isRiding()));
        put("is_ridden", (e, a) -> new NumericValue(e.isBeingRidden()));
        put("passengers", (e, a) -> ListValue.wrap(e.getPassengers().stream().map(EntityValue::new).collect(Collectors.toList())));
        put("mount", (e, a) -> (e.getRidingEntity()!=null)?new EntityValue(e.getRidingEntity()):Value.NULL);
        //todo check if it exists
        //put("unmountable", (e, a) -> new NumericValue(e.dismountRidingEntity();));

        put("nbt_tags", (e, a) -> ListValue.wrap(e.getTags().stream().map(StringValue::new).collect(Collectors.toList())));
        /*todo figure out
        // deprecated
        put("has_tag", (e, a) -> new NumericValue(e.getScoreboardTags().contains(a.getString())));

        put("has_scoreboard_tag", (e, a) -> new NumericValue(e.has().contains(a.getString())));
        */
        //todo figure this out. May have to do with datapacks, if so bye bye
        //put("has_entity_tag", (e, a) -> {
        //    NBTBase tag = e.getServer().getTagManager().getEntityTypes().getTag(new Identifier(a.getString()));
        //    if (tag == null) return Value.NULL;
        //    return new NumericValue(e.getType().isIn(tag));
        //});

        put("yaw", (e, a)-> new NumericValue(e.rotationYaw));
        put("head_yaw", (e, a)-> {
            if (e instanceof EntityLiving)
            {
                return  new NumericValue(e.getRotationYawHead());
            }
            return Value.NULL;
        });
        put("body_yaw", (e, a)-> {
            if (e instanceof EntityLiving)
            {
                return  new NumericValue(((EntityLiving) e).rotationYaw);
            }
            return Value.NULL;
        });

        put("pitch", (e, a)-> new NumericValue(e.rotationPitch));
        put("look", (e, a) -> {
            Vec3d look = e.getLook(1.0F);
            return ListValue.of(new NumericValue(look.x),new NumericValue(look.y),new NumericValue(look.z));
        });
        put("is_burning", (e, a) -> new NumericValue(e.isBurning()));
        put("fire", (e, a) -> new NumericValue(e.getFire()));
        put("silent", (e, a)-> new NumericValue(e.isSilent()));
        put("gravity", (e, a) -> new NumericValue(!e.hasNoGravity()));
        put("immune_to_fire", (e, a) -> new NumericValue(e.isImmuneToFire()));

        put("invulnerable", (e, a) -> new NumericValue(e.getIsInvulnerable()));
        put("dimension", (e, a) -> new StringValue(DimensionType.getById(e.dimension).getName())); // getDimId
        put("height", (e, a) -> new NumericValue(e.height)); //todo handling for player crouching
        put("width", (e, a) -> new NumericValue(e.width));
        put("eye_height", (e, a) -> new NumericValue(e.getEyeHeight()));
        put("age", (e, a) -> new NumericValue(e.ticksExisted));
        put("breeding_age", (e, a) -> e instanceof EntityAgeable?new NumericValue(((EntityAgeable) e).getGrowingAge()):Value.NULL);
        put("despawn_timer", (e, a) -> e instanceof EntityLiving?new NumericValue(((EntityItem) e).getDespawnTimer()):Value.NULL);
        put("item", (e, a) -> (e instanceof EntityItem)?ValueConversions.of(((EntityItem) e).getItem()):Value.NULL);
        put("count", (e, a) -> (e instanceof EntityItem)?new NumericValue(((EntityItem) e).getItem().getCount()):Value.NULL);
        put("pickup_delay", (e, a) -> (e instanceof EntityItem)?new NumericValue(((EntityItem) e).getPickupDelay()):Value.NULL);
        put("portal_cooldown", (e , a) ->new NumericValue(e.getMaxInPortalTime()));//todo check these with docs
        put("portal_timer", (e , a) ->new NumericValue(e.getPortalCooldown()));
        // EntityItem -> despawn timer via ssGetAge
        put("is_baby", (e, a) -> (e instanceof EntityLiving)?new NumericValue(((EntityLiving) e).isChild()):Value.NULL);
        put("target", (e, a) -> {
            if (e instanceof EntityMob) {
                EntityLivingBase target = ((EntityMob) e).getAttackTarget(); // there is also getAttacking in living....
                if (target != null) {
                    return new EntityValue(target);
                }
            }
            return Value.NULL;
        });

        put("spawn_point", (e, a) -> {
            if (e instanceof EntityPlayerMP)
            {
                EntityPlayerMP spe = (EntityPlayerMP)e;
                if (spe.getBedLocation() == null) return Value.FALSE;
                return ListValue.of(
                        ValueConversions.of(spe.getBedLocation()),
                        new NumericValue(spe.isSpawnForced()) // true if forced spawn point
                        );
            }
            return Value.NULL;
        });
        put("sneaking", (e, a) -> e.isSneaking()?Value.TRUE:Value.FALSE);
        put("sprinting", (e, a) -> e.isSprinting()?Value.TRUE:Value.FALSE);
        put("wet", (e, a) -> e.isWet()?Value.TRUE:Value.FALSE);//todo change docs from swimming to wet
        put("swinging", (e, a) -> {
            if (e instanceof EntityLiving) return new NumericValue(((EntityLiving) e).isSwingInProgress);
            return Value.NULL;
        });

        put("air", (e, a) -> new NumericValue(e.getAir()));

        put("persistence", (e, a) -> {
            if (e instanceof EntityLiving) return new NumericValue(((EntityLiving) e).getPersistence());
            return Value.NULL;
        });
        put("hunger", (e, a) -> {
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).getFoodStats().getFoodLevel());
            return Value.NULL;
        });
        put("saturation", (e, a) -> {
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).getFoodStats().getSaturationLevel());
            return Value.NULL;
        });

        put("exhaustion",(e, a)->{
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).getFoodStats().getExhaustion());
            return Value.NULL;
        });

        put("absorption",(e, a)->{
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).getAbsorptionAmount());
            return Value.NULL;
        });

        put("xp",(e, a)->{
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).experienceTotal);
            return Value.NULL;
        });

        put("xp_level", (e, a)->{
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).experienceLevel);
            return Value.NULL;
        });

        //todo xp progress
        //put("xp_progress", (e, a)->{
        //    if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).experienceProgress);
        //    return Value.NULL;
        //});

        put("score", (e, a)->{
            if(e instanceof EntityPlayer) return new NumericValue(((EntityPlayer) e).getScore());
            return Value.NULL;
        });

        put("jumping", (e, a) -> {
            if (e instanceof EntityLiving)
            {
                return  new NumericValue(((EntityLiving) e).getJumping());
            }
            return Value.NULL;
        });
        put("gamemode", (e, a) -> {
            if (e instanceof  EntityPlayerMP) {
                return new StringValue(((EntityPlayerMP) e).interactionManager.getGameType().getName());
            }
            return Value.NULL;
        });

        //todo pathing
        //put("path", (e, a) -> {
        //    if (e instanceof EntityMob)
        //    {
        //        Path path = ((EntityMob)e).getNavigation().getCurrentPath();
        //        if (path == null) return Value.NULL;
        //        return ValueConversions.fromPath((World)e.getEntityWorld(), path);
        //    }
        //    return Value.NULL;
        //});

        put("gamemode_id", (e, a) -> {
            if (e instanceof  EntityPlayerMP)
            {
                return new NumericValue(((EntityPlayerMP) e).interactionManager.getGameType().getID());
            }
            return Value.NULL;
        });

        put("permission_level", (e, a) -> {
            if (e instanceof  EntityPlayerMP)
            {
                EntityPlayerMP spe = (EntityPlayerMP) e;
                for (int i=4; i>=0; i--)
                {
                    if (spe.server.getOpPermissionLevel() == i)//todo check if that person has OP
                        return new NumericValue(i);

                }
                return new NumericValue(0);
            }
            return Value.NULL;
        });

        put("player_type", (e, a) -> {
            if (e instanceof EntityPlayer)
            {
                if (e instanceof EntityPlayerMPFake) return new StringValue("fake");//todo getting shadows
                EntityPlayer p = (EntityPlayer)e;
                MinecraftServer server = p.getEntityWorld().getMinecraftServer();
                if (server.isDedicatedServer()) return new StringValue("multiplayer");
                boolean singlePlayer = server.isSinglePlayer();
                if (singlePlayer) return new StringValue("singleplayer");
                boolean isowner = server.getServerHostname() == p.getGameProfile().getName();
                if (isowner) return new StringValue("lan_host");
                return new StringValue("lan player");
                // realms?
            }
            return Value.NULL;
        });

        //todoclient brand
        //put("client_brand", (e, a) -> {
        //    if (e instanceof EntityPlayerMP)
        //    {
        //        return StringValue.of(ServerNetworkHandler.getPlayerStatus((EntityPlayerMP) e));
        //    }
        //    return Value.NULL;
        //});

        put("team", (e, a) -> e.getTeam()==null?Value.NULL:new StringValue(e.getTeam().getName()));

        put("ping", (e, a) -> {
            if (e instanceof  EntityPlayerMP)
            {
                EntityPlayerMP spe = (EntityPlayerMP) e;
                return new NumericValue(spe.ping);
            }
            return Value.NULL;
        });

        //spectating_entity
        // isGlowing
        put("effect", (e, a) ->
        {
            if (!(e instanceof EntityLiving))
            {
                return Value.NULL;
            }
            if (a == null)
            {
                List<Value> effects = new ArrayList<>();
                for (PotionEffect p : ((EntityLiving) e).getActivePotionEffects())
                {
                    effects.add(ListValue.of(
                        new StringValue(p.getEffectName().replaceFirst("^effect\\.minecraft\\.", "")),
                        new NumericValue(p.getAmplifier()),
                        new NumericValue(p.getDuration())
                    ));
                }
                return ListValue.wrap(effects);
            }
            String effectName = a.getString();
            Potion potion = Potion.REGISTRY.getObject(new ResourceLocation(effectName));
            if (potion == null)
                throw new InternalExpressionException("No such an effect: "+effectName);
            if (!((EntityLiving) e).isPotionActive(potion))
                return Value.NULL;
            PotionEffect pe = ((EntityLiving) e).getActivePotionEffect(potion);
            return ListValue.of( new NumericValue(pe.getAmplifier()), new NumericValue(pe.getDuration()) );
        });

        put("health", (e, a) ->
        {
            if (e instanceof EntityLiving) {
                return new NumericValue(((EntityLiving) e).getHealth());
            }
            //if (e instanceof EntityItem)
            //{
            //    e.h consider making item health public
            //}
            return Value.NULL;
        });
        put("holds", (e, a) -> {
            EntityEquipmentSlot where = EntityEquipmentSlot.MAINHAND;
            if (a != null)
                where = inventorySlots.get(a.getString());
            if (where == null)
                throw new InternalExpressionException("Unknown inventory slot: "+a.getString());
            if (e instanceof EntityLiving)
                return ListValue.fromItemStack(((EntityLiving)e).getItemStackFromSlot(where));
            return Value.NULL;
        });

        put("selected_slot", (e, a) -> {
           if (e instanceof EntityPlayer)
               return new NumericValue(((EntityPlayer) e).inventory.currentItem);
           return Value.NULL;
        });
        /*todo breaking progress
        put("active_block", (e, a) -> {
            if (e instanceof EntityPlayerMP)
            {
                ServerPlayerInteractionManagerInterface manager = (ServerPlayerInteractionManagerInterface) (((EntityPlayerMP) e).interactionManager);
                BlockPos pos = manager.getCurrentBreakingBlock();
                if (pos == null) return Value.NULL;
                return new BlockValue(null, ((EntityPlayerMP) e).getWorld(), pos);
            }
            return Value.NULL;
        });


        put("breaking_progress", (e, a) -> {
            if (e instanceof EntityPlayerMP)
            {
                ServerPlayerInteractionManagerInterface manager = (ServerPlayerInteractionManagerInterface) (((EntityPlayerMP) e).interactionManager);
                int progress = manager.getCurrentBlockBreakingProgress();
                if (progress < 0) return Value.NULL;
                return new NumericValue(progress);
            }
            return Value.NULL;
        });
        */

        put("facing", (e, a) -> {
            int index = 0;
            if (a != null)
                index = (6+(int)NumericValue.asNumber(a).getLong())%6;
            if (index < 0 || index > 5)
                throw new InternalExpressionException("Facing order should be between -6 and 5");

            return new StringValue(e.getHorizontalFacing().getName2());
        });

        /*todo trace
        put("trace", (e, a) -> {
            float reach = 4.5f;
            boolean entities = true;
            boolean liquids = false;
            boolean blocks = true;
            boolean exact = false;

            if (a!=null)
            {
                if (!(a instanceof ListValue))
                {
                    reach = (float) NumericValue.asNumber(a).getDouble();
                }
                else
                {
                    List<Value> args = ((ListValue) a).getItems();
                    if (args.size()==0)
                        throw new InternalExpressionException("'trace' needs more arguments");
                    reach = (float) NumericValue.asNumber(args.get(0)).getDouble();
                    if (args.size() > 1)
                    {
                        entities = false;
                        blocks = false;
                        for (int i = 1; i < args.size(); i++)
                        {
                            String what = args.get(i).getString();
                            if (what.equalsIgnoreCase("entities"))
                                entities = true;
                            else if (what.equalsIgnoreCase("blocks"))
                                blocks = true;
                            else if (what.equalsIgnoreCase("liquids"))
                                liquids = true;
                            else if (what.equalsIgnoreCase("exact"))
                                exact = true;

                            else throw new InternalExpressionException("Incorrect tracing: "+what);
                        }
                    }
                }
            }
            else if (e instanceof EntityPlayerMP && ((EntityPlayerMP) e).interactionManager.isCreative())
            {
                reach = 5.0f;
            }

            HitResult hitres;
            if (entities && !blocks)
                hitres = Tracer.rayTraceEntities(e, 1, reach, reach*reach);
            else if (entities)
                hitres = Tracer.rayTrace(e, 1, reach, liquids);
            else
                hitres = Tracer.rayTraceBlocks(e, 1, reach, liquids);

            if (hitres == null) return Value.NULL;
            if (exact && hitres.getType() != HitResult.Type.MISS) return ValueConversions.of(hitres.getPos());
            switch (hitres.getType()) {
                case MISS: return Value.NULL;
                case BLOCK: return new BlockValue(null, (World) e.getEntityWorld(), ((BlockHitResult)hitres).getBlockPos() );
                case ENTITY: return new EntityValue(((EntityHitResult)hitres).getEntity());
            }
            return Value.NULL;
        });
        */
        put("nbt",(e, a) -> {
            NBTTagCompound nbttagcompound = e.writeToNBT((new NBTTagCompound()));
            if (a==null)
                return new NBTSerializableValue(nbttagcompound);
            return new NBTSerializableValue(nbttagcompound).get(a);
        });

        //todo mob categories cos 1.12 code is a mess
        //put("category",(e,a)->{
        //    String category;
        //    if(e instanceof EntityMob)
        //        category = "monster";
        //
        //
        //
        //    return Value.NULL;
        //});
    }};

    public void set(String what, Value toWhat)
    {
        if (!(featureModifiers.containsKey(what)))
            throw new InternalExpressionException("Unknown entity action: " + what);
        try
        {
            featureModifiers.get(what).accept(getEntity(), toWhat);
        }
        catch (NullPointerException npe)
        {
            throw new InternalExpressionException("'modify' for '"+what+"' expects a value");
        }
        catch (IndexOutOfBoundsException ind)
        {
            throw new InternalExpressionException("Wrong number of arguments for `modify` option: "+what);
        }
    }

    private static void updatePosition(Entity e, double x, double y, double z, float yaw, float pitch) {
        if (
                !Double.isFinite(x) || Double.isNaN(x) ||
                !Double.isFinite(y) || Double.isNaN(y) ||
                !Double.isFinite(z) || Double.isNaN(z) ||
                !Float.isFinite(yaw) || Float.isNaN(yaw) ||
                !Float.isFinite(pitch) || Float.isNaN(pitch)
        )
            return;
       e.setPositionAndRotation(x, y, z, yaw, pitch);
    }

    private static final Map<String, BiConsumer<Entity, Value>> featureModifiers = new HashMap<String, BiConsumer<Entity, Value>>() {{

        put("age", (e, v) -> e.ticksExisted = Math.abs((int)NumericValue.asNumber(v).getLong()) );
        put("health", (e, v) -> {
            if (e instanceof EntityLiving) ((EntityLiving) e).setHealth(NumericValue.asNumber(v, "health").getFloat());
        });
        // todo add handling of the source for extra effects
        /*put("damage", (e, v) -> {
            float dmgPoints;
            DamageSource source;
            if (v instanceof ListValue && ((ListValue) v).getItems().size() > 1)
            {
                   List<Value> vals = ((ListValue) v).getItems();
                   dmgPoints = (float) NumericValue.asNumber(v).getDouble();
                   source = DamageSource ... yeah...
            }
            else
            {

            }
        });*/
        put("kill", (e, v) -> e.onKillCommand());
        put("location", (e, v) ->
        {
            if (!(v instanceof ListValue))
            {
                throw new InternalExpressionException("Expected a list of 5 parameters as a second argument");
            }
            List<Value> coords = ((ListValue) v).getItems();
            updatePosition(e,
                    NumericValue.asNumber(coords.get(0)).getDouble(),
                    NumericValue.asNumber(coords.get(1)).getDouble(),
                    NumericValue.asNumber(coords.get(2)).getDouble(),
                    (float) NumericValue.asNumber(coords.get(3)).getDouble(),
                    (float) NumericValue.asNumber(coords.get(4)).getDouble()
            );
        });
        put("pos", (e, v) ->
        {
            if (!(v instanceof ListValue))
            {
                throw new InternalExpressionException("Expected a list of 3 parameters as a second argument");
            }
            List<Value> coords = ((ListValue) v).getItems();
            updatePosition(e,
                    NumericValue.asNumber(coords.get(0)).getDouble(),
                    NumericValue.asNumber(coords.get(1)).getDouble(),
                    NumericValue.asNumber(coords.get(2)).getDouble(),
                    e.rotationYaw,
                    e.rotationPitch
            );
        });
        put("x", (e, v) ->
        {
            updatePosition(e, NumericValue.asNumber(v).getDouble(), e.posY, e.posZ, e.rotationYaw, e.rotationPitch);
        });
        put("y", (e, v) ->
        {
            updatePosition(e, e.posX, NumericValue.asNumber(v).getDouble(), e.posZ, e.rotationYaw, e.rotationPitch);
        });
        put("z", (e, v) ->
        {
            updatePosition(e, e.posX, e.posY, NumericValue.asNumber(v).getDouble(), e.rotationYaw, e.rotationPitch);
        });
        put("yaw", (e, v) ->
        {
            updatePosition(e, e.posX, e.posY, e.posY, ((float)NumericValue.asNumber(v).getDouble()) % 360, e.rotationPitch);
        });
        put("head_yaw", (e, v) -> {
            if (e instanceof EntityLiving)
                e.setRotationYawHead(NumericValue.asNumber(v).getFloat());

        });
        put("body_yaw", (e, v) -> {
            if (e instanceof EntityLiving)
                e.setRotation(NumericValue.asNumber(v).getFloat(), e.rotationPitch);

        });

        put("pitch", (e, v) ->
        {
            updatePosition(e, e.posX, e.posY, e.posZ, e.rotationYaw, MathHelper.clamp((float)NumericValue.asNumber(v).getDouble(), -90, 90));
        });

        //"look"
        //"turn"
        //"nod"

        put("move", (e, v) -> {
            if (!(v instanceof ListValue))
                throw new InternalExpressionException("Expected a list of 3 parameters as a second argument");

            List<Value> coords = ((ListValue) v).getItems();
            updatePosition(e,
                    e.posX + NumericValue.asNumber(coords.get(0)).getDouble(),
                    e.posY + NumericValue.asNumber(coords.get(1)).getDouble(),
                    e.posZ + NumericValue.asNumber(coords.get(2)).getDouble(),
                    e.rotationYaw,
                    e.rotationPitch
            );
        });

        put("motion", (e, v) ->
        {
            if (!(v instanceof ListValue))
            {
                throw new InternalExpressionException("Expected a list of 3 parameters as a second argument");
            }
            List<Value> coords = ((ListValue) v).getItems();
            e.setVelocity(
                    NumericValue.asNumber(coords.get(0)).getDouble(),
                    NumericValue.asNumber(coords.get(1)).getDouble(),
                    NumericValue.asNumber(coords.get(2)).getDouble()
            );
        });
        put("motion_x", (e, v) ->
        {
            Vec3d velocity = e.getVelocity();
            e.setVelocity(NumericValue.asNumber(v).getDouble(), velocity.y, velocity.z);
        });
        put("motion_y", (e, v) ->
        {
            Vec3d velocity = e.getVelocity();
            e.setVelocity(velocity.x, NumericValue.asNumber(v).getDouble(), velocity.z);
        });
        put("motion_z", (e, v) ->
        {
            Vec3d velocity = e.getVelocity();
            e.setVelocity(velocity.x, velocity.y, NumericValue.asNumber(v).getDouble());
        });

        put("accelerate", (e, v) ->
        {
            if (!(v instanceof ListValue))
            {
                throw new InternalExpressionException("Expected a list of 3 parameters as a second argument");
            }
            List<Value> coords = ((ListValue) v).getItems();
            e.addVelocity(
                    NumericValue.asNumber(coords.get(0)).getDouble(),
                    NumericValue.asNumber(coords.get(1)).getDouble(),
                    NumericValue.asNumber(coords.get(2)).getDouble()
            );

        });
        put("custom_name", (e, v) -> {
            if (v instanceof NullValue)
            {
                e.setAlwaysRenderNameTag(false);
                e.setCustomNameTag(null);
                return;
            }
            boolean showName = false;
            if (v instanceof ListValue)
            {
                showName = ((ListValue) v).getItems().get(1).getBoolean();
                v = ((ListValue) v).getItems().get(0);
            }
            e.setAlwaysRenderNameTag(showName);
            e.setCustomNameTag(v.getString());
        });

        put("persistence", (e, v) -> {
            if (!(e instanceof EntityLiving)) return;
            if (v == null) v = Value.TRUE;
            ((EntityLiving)e).setPersistence(v.getBoolean());
        });

        put("dismount", (e, v) -> e.dismountRidingEntity());
        put("mount", (e, v) -> {
            if (v instanceof EntityValue) {
                e.startRiding(((EntityValue) v).getEntity(),true);
            }
            //todo packet sending for mounting
            //if (e instanceof EntityPlayerMP)
            //{
            //    ((EntityPlayerMP)e).networkHandler.sendPacket(new EntityPassengersSetS2CPacket(e));
            //    //...
            //}
        });
        //todo unmountable
        //put("unmountable", (e, v) ->{
        //    if (v == null)
        //        v = Value.TRUE;
        //    (e.pass).setPermanentVehicle(v.getBoolean());
        //});
        put("drop_passengers", (e, v) -> e.removePassengers());
        put("mount_passengers", (e, v) -> {
            if (v==null)
                throw new InternalExpressionException("'mount_passengers' needs entities to ride");
            if (v instanceof EntityValue)
                ((EntityValue) v).getEntity().startRiding(e);
            else if (v instanceof ListValue)
                for (Value element : ((ListValue) v).getItems())
                    if (element instanceof EntityValue)
                        ((EntityValue) element).getEntity().startRiding(e);
        });
        /*todo scoreboard stuff
        put("tag", (e, v) -> {
            if (v==null)
                throw new InternalExpressionException("'tag' requires parameters");
            if (v instanceof ListValue)
                for (Value element : ((ListValue) v).getItems()) e.addScoreboardTag(element.getString());
            else
                e.(v.getString());
        });

        put("clear_tag", (e, v) -> {
            if (v==null)
                throw new InternalExpressionException("'clear_tag' requires parameters");
            if (v instanceof ListValue)
                for (Value element : ((ListValue) v).getItems()) e.removeScoreboardTag(element.getString());
            else
                e.removeScoreboardTag(v.getString());
        });
        */

        //todo breeding
        //put("breeding_age", (e, v) ->
        //{
        //    if (e instanceof EntityCreature)
        //    {
        //        ((EntityCreature) e).canB ((int)NumericValue.asNumber(v).getLong());
        //    }
        //});

        put("talk", (e, v) -> {
            // attacks indefinitely
            if (e instanceof EntityMob)
            {
                ((EntityMob) e).playLivingSound();
            }
        });

        put("spawn_point", (e, a) -> {
            if (!(e instanceof EntityPlayerMP)) return;
            EntityPlayerMP spe = (EntityPlayerMP)e;
            if (a == null) {
                spe.setSpawnPoint(null,false);
            }
            else if (a instanceof ListValue) {
                List<Value> params= ((ListValue) a).getItems();
                Vector3Argument blockLocator = Vector3Argument.findIn(params, 0, false);
                BlockPos pos = new BlockPos(blockLocator.vec);
                World world = spe.getEntityWorld();
                float angle = spe.getRotationYawHead();
                boolean forced = false;
                if (params.size() > blockLocator.offset)
                {
                    Value worldValue = params.get(blockLocator.offset);
                    world = ValueConversions.dimFromValue(worldValue, spe.getServer());
                    if (params.size() > blockLocator.offset+1)
                    {
                        angle = NumericValue.asNumber(params.get(blockLocator.offset+1), "angle").getFloat();
                        if (params.size() > blockLocator.offset+2)
                        {
                            forced = params.get(blockLocator.offset+2).getBoolean();
                        }
                    }
                }
                spe.setSpawnPoint(pos, forced);
            }
            else if (a instanceof BlockValue)
            {
                BlockValue bv= (BlockValue)a;
                if (bv.getPos()==null || bv.getWorld() == null)
                    throw new InternalExpressionException("block for spawn modification should be localised in the world");
                spe.setSpawnPoint(bv.getPos(), true);
            }
            else if (a.isNull())
                spe.setSpawnPoint(null, false);

            else
                throw new InternalExpressionException("modifying player respawn point requires a block position, optional world, optional angle, and optional force");

        });

        put("pickup_delay", (e, v) -> {
            if (e instanceof EntityItem) {
                ((EntityItem) e).setPickupDelay((int)NumericValue.asNumber(v).getLong());
            }
        });

        put("despawn_timer", (e, v) -> {
            if (e instanceof EntityItem) {
                ((EntityItem) e).setAge(NumericValue.asNumber(v).getInt());
            }
        });

        put("portal_cooldown", (e , v) -> e.timeUntilPortal = NumericValue.asNumber(v, "portal_cooldown").getInt());

        //todo read docs to figure this out
        //put("portal_timer", (e , v) -> e.getMaxInPortalTime().setPortalTimer(NumericValue.asNumber(v,"portal_timer").getInt()));

        put("ai", (e, v) -> {
            if (e instanceof EntityMob)
                ((EntityMob) e).setNoAI(!v.getBoolean());
        });

        put("no_clip", (e, v) ->
        {
            if (v == null)
                e.noClip = true;
            else
                e.noClip = v.getBoolean();
        });
        put("effect", (e, v) ->
        {
            if (!(e instanceof EntityLiving)) return;
            EntityLiving le = (EntityLiving)e;
            if (v == null)
            {
                le.getActivePotionEffects().forEach(pe->le.removePotionEffect(pe.getPotion()));
                return;
            }
            else if (v instanceof ListValue) {
                List<Value> lv = ((ListValue) v).getItems();
                if (lv.size() >= 1 && lv.size() <= 5) {
                    String effectName = lv.get(0).getString();
                    Potion effect = Potion.REGISTRY.getObject(new ResourceLocation(effectName));
                    if (effect == null)
                        throw new InternalExpressionException("Wrong effect name: "+effectName);//todo test
                    if (lv.size() == 1) {
                        le.removePotionEffect(effect);
                        return;
                    }
                    int duration = (int)NumericValue.asNumber(lv.get(1)).getLong();
                    if (duration <= 0) {
                        le.removePotionEffect(effect);
                        return;
                    }
                    int amplifier = 0;
                    if (lv.size() > 2)
                        amplifier = (int)NumericValue.asNumber(lv.get(2)).getLong();
                    boolean showParticles = true;
                    if (lv.size() > 3)
                        showParticles = lv.get(3).getBoolean();
                    boolean ambient = false;
                    if (lv.size() > 4)
                        ambient = lv.get(4).getBoolean();
                    le.addPotionEffect(new PotionEffect(effect, duration, amplifier, ambient, showParticles));
                    return;
                }
            }
            else
            {
                String effectName = v.getString();
                Potion effect = Potion.getPotionFromResourceLocation("");
                if (effect == null)
                    throw new InternalExpressionException("Wrong effect name: "+effectName);
                le.removePotionEffect(effect);
                return;
            }
            throw new InternalExpressionException("'effect' needs either no arguments (clear) or effect name, duration, and optional amplifier, show particles, show icon and ambient");
        });

        put("gamemode", (e,v)->{
            if(!(e instanceof EntityPlayerMP)) return;
            GameType toSet = v instanceof NumericValue ?
                    GameType.getByID(((NumericValue) v).getInt()) :
                    GameType.parseGameTypeWithDefault(v.getString().toLowerCase(Locale.ROOT), GameType.NOT_SET);
            ((EntityPlayerMP) e).setGameType(toSet);
        });

        put("jumping",(e,v)->{
            if(!(e instanceof EntityLiving)) return;
            ((EntityLiving) e).setJumping(v.getBoolean());
        });

        put("jump",(e,v)->{
            if (e instanceof EntityLiving) {
                ((EntityLiving) e).getJumpHelper().doJump();
            }
        });

        put("silent",(e,v)-> e.setSilent(v.getBoolean()));

        put("gravity",(e,v)-> e.setNoGravity(!v.getBoolean()));

        put("invulnerable",(e,v)-> e.setEntityInvulnerable(v.getBoolean()));

        put("fire",(e,v)-> e.setFireTicks((int)NumericValue.asNumber(v).getLong()));

        put("hunger", (e, v)-> {
            if(e instanceof EntityPlayer) ((EntityPlayer) e).getFoodStats().setFoodLevel((int) NumericValue.asNumber(v).getLong());
        });

        put("exhaustion", (e, v) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).getFoodStats().setFoodExhaustionLevel(NumericValue.asNumber(v, "exhaustion").getFloat());
        });

        put("absorption", (e, v) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setAbsorptionAmount(NumericValue.asNumber(v, "absorbtion").getFloat());
        });

        put("add_xp", (e, v) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).addExperience(NumericValue.asNumber(v, "add_xp").getInt());
        });

        put("xp_level", (e, v) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).addExperienceLevel(NumericValue.asNumber(v, "xp_level").getInt()-((EntityPlayer) e).experienceLevel);
        });

        put("xp_progress", (e, v) -> {
            /*todo xp progress
            if (e instanceof EntityPlayer)
            {
                EntityPlayer p = (EntityPlayer) e;
                p.experience = NumericValue.asNumber(v, "xp_progress").getFloat();
            }

             */
        });

        put("xp_score", (e, v) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setScore(NumericValue.asNumber(v, "xp_score").getInt());
        });

        put("saturation", (e, v)-> {
            if(e instanceof EntityPlayer) ((EntityPlayer) e).getFoodStats().setFoodSaturationLevel(NumericValue.asNumber(v, "saturation").getFloat());
        });

        put("air", (e, v) -> e.setAir(NumericValue.asNumber(v, "air").getInt()));

        put("breaking_progress", (e, a) -> {
            throw new UnsupportedOperationException("breaking_progress");
            /*todo breaking progress
            if (e instanceof EntityPlayerMP)
            {
                int progress = (a == null || a.isNull())?-1:NumericValue.asNumber(a).getInt();
                ServerPlayerInteractionManagerInterface manager = (ServerPlayerInteractionManagerInterface) (((EntityPlayerMP) e).interactionManager);
                manager.setBlockBreakingProgress(progress);
            }
            */
        });

        put("nbt", (e, v) -> {
            if (!(e instanceof EntityPlayer))
            {
                UUID uUID = e.getUniqueID();
                Value tagValue = NBTSerializableValue.fromValue(v);
                if (tagValue instanceof NBTSerializableValue)
                {
                    e.readFromNBT(((NBTSerializableValue) tagValue).getCompoundTag());
                    e.setUniqueId(uUID);
                }
            }
        });
        put("nbt_merge", (e, v) -> {
            if (!(e instanceof EntityPlayer))
            {
                UUID uUID = e.getUniqueID();
                Value tagValue = NBTSerializableValue.fromValue(v);
                if (tagValue instanceof NBTSerializableValue)
                {
                    NBTTagCompound nbttagcompound = e.writeToNBT((new NBTTagCompound()));
                    //todo figure out nbttagcompound.copyFrom(((NBTSerializableValue) tagValue).getCompoundTag());
                    e.readFromNBT(nbttagcompound);
                    e.setUniqueId(uUID);
                }
            }
        });

        // "dimension"      []
        // "item"           []
        // "count",         []
        // "effect_"name    []
    }};
    /* todo entity events
    public void setEvent(CarpetContext cc, String eventName, FunctionValue fun, List<Value> args) {
        EntityEventsGroup.Event event = EntityEventsGroup.Event.byName.get(eventName);
        if (event == null)
            throw new InternalExpressionException("Unknown entity event: " + eventName);
        ((EntityInterface)getEntity()).getEventContainer().addEvent(event, cc.host, fun, args);
    }
    */
    @Override
    public NBTBase toTag(boolean force)
    {
        if (!force) throw new NBTSerializableValue.IncompatibleTypeException(this);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("Data", getEntity().writeToNBT( new NBTTagCompound()));
        tag.setString("Name", getEntity().getName());
        return tag;
    }
}
