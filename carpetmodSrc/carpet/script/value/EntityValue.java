package carpet.script.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.BooleanValue;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.MapValue;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;
import carpet.patches.EntityPlayerMPFake;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOpsEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class EntityValue extends Value {
    public static final Map<String, BiFunction<Entity, Value, Value>> featureAccessors = new HashMap<String, BiFunction<Entity, Value, Value>>() {{
        //put("test", (e, a) -> a == null ? Value.NULL : new StringValue(a.getString()));
        put("uuid", (e, a) -> new StringValue(e.getUniqueID().toString()));
        put("id", (e, a) -> new NumericValue(e.getEntityId()));
        put("pos", (e, a) -> ValueConversions.of(e.getPositionVector()));
        put("location", (e, a) -> ListValue.of(new NumericValue(e.posX), new NumericValue(e.posY), new NumericValue(e.posZ), new NumericValue(e.rotationYaw), new NumericValue(e.rotationPitch)));
        put("x", (e, a) -> new NumericValue(e.posX));
        put("y", (e, a) -> new NumericValue(e.posY));
        put("z", (e, a) -> new NumericValue(e.posZ));
        put("motion", (e, a) -> ValueConversions.of(e.motionX, e.motionY, e.motionZ));
        put("motion_x", (e, a) -> new NumericValue(e.motionX));
        put("motion_y", (e, a) -> new NumericValue(e.motionY));
        put("motion_z", (e, a) -> new NumericValue(e.motionZ));
        put("on_ground", (e, a) -> BooleanValue.of(e.onGround));
        put("name", (e, a) -> new StringValue(e.getName()));
        //put("display_name", (e, a) -> new FormattedTextValue(e.getDisplayName()));todo FormattedTextValue
        //todo test entity type vs command name
        put("command_name", (e, a) -> new StringValue(EntityList.getEntityString(e)));
        put("type", (e, a) -> new StringValue(EntityList.getEntityString(e)));
        put("custom_name", (e, a) -> e.hasCustomName() ? new StringValue(e.getCustomNameTag()) : Value.NULL);
        put("is_riding", (e, a) -> BooleanValue.of(e.isRiding()));
        put("is_ridden", (e, a) -> BooleanValue.of(e.isBeingRidden()));
        put("passengers", (e, a) -> ListValue.wrap(e.getPassengers().stream().map(EntityValue::new).collect(Collectors.toList())));
        put("mount", (e, a) -> e.isRiding() ? new EntityValue(e.getRidingEntity()) : Value.NULL);
        put("unmountable", (e, a) -> BooleanValue.of(e.canBeRidden()));
        //todo scoreboards
        //put("scoreboard_tags", (e, a) -> ListValue.wrap(e.getScoreboardTags().stream().map(StringValue::new).collect(Collectors.toList())));
        //put("has_scoreboard_tag", (e, a) -> BooleanValue.of(e.getScoreboardTags().contains(a.getString())));
        //put("team", (e, a) -> e.getScoreboardTeam()==null?Value.NULL:new StringValue(e.getScoreboardTeam().getName()));


        //todo entity tags
        //put("entity_tags", (e, a) -> ListValue.wrap(e.getServer().getTagManager().getOrCreateTagGroup(Registry.ENTITY_TYPE_KEY).getTags().entrySet().stream().filter(entry -> entry.getValue().contains(e.getType())).map(entry -> ValueConversions.of(entry.getKey())).collect(Collectors.toList())));

        //put("has_entity_tag", (e, a) -> {
        //    Tag<EntityType<?>> tag = e.getServer().getTagManager().getOrCreateTagGroup(Registry.ENTITY_TYPE_KEY).getTag(InputValidator.identifierOf(a.getString()));
        //    if (tag == null) return Value.NULL;
        //    return BooleanValue.of(e.getType().isIn(tag));
        //});

        put("yaw", (e, a) -> new NumericValue(e.rotationYaw));
        put("head_yaw", (e, a) -> {
            if (e instanceof EntityLivingBase) {
                return new NumericValue(e.getRotationYawHead());
            }
            return Value.NULL;
        });
        //todo body yaw
        //put("body_yaw", (e, a)-> {
        //    if (e instanceof EntityLivingBase)
        //    {
        //        return  new NumericValue(((EntityLivingBase) e).bodyYaw);
        //    }
        //    return Value.NULL;
        //});
        put("pitch", (e, a) -> new NumericValue(e.rotationPitch));
        put("look", (e, a) -> ValueConversions.of(e.getLookVec()));
        put("is_burning", (e, a) -> BooleanValue.of(e.isBurning()));
        put("fire", (e, a) -> new NumericValue(e.getFire()));
        put("silent", (e, a) -> BooleanValue.of(e.isSilent()));
        put("gravity", (e, a) -> BooleanValue.of(!e.hasNoGravity()));
        put("immune_to_fire", (e, a) -> BooleanValue.of(e.isImmuneToFire()));
        put("invulnerable", (e, a) -> BooleanValue.of(e.getIsInvulnerable()));
        put("dimension", (e, a) -> new NumericValue(e.dimension));
        put("height", (e, a) -> new NumericValue(e.height));
        put("width", (e, a) -> new NumericValue(e.width));
        put("eye_height", (e, a) -> new NumericValue(e.getEyeHeight()));
        put("age", (e, a) -> new NumericValue(e.ticksExisted));
        put("breeding_age", (e, a) -> e instanceof EntityAgeable ? new NumericValue(((EntityAgeable) e).getGrowingAge()) : Value.NULL);
        put("despawn_timer", (e, a) -> e instanceof EntityLivingBase ?
                new NumericValue(((EntityLivingBase) e).getIdleTime()) :
                e instanceof EntityItem ? new NumericValue(((EntityItem) e).getAge()) : Value.NULL);
        put("item", (e, a) -> (e instanceof EntityItem) ? ValueConversions.of(((EntityItem) e).getItem()) : Value.NULL);
        put("holds", (e, a) -> {
            if (!(e instanceof EntityLivingBase)) return Value.NULL;
            EntityLivingBase ep = (EntityLivingBase) e;
            Iterable<ItemStack> inventory = ep.getEquipmentAndArmor();
            int where = (e instanceof EntityPlayer) ? ((EntityPlayer) e).inventory.currentItem : 0;
            if (a != null)
                where = a.readInt();
            ItemStack item = null;
            int i = where;
            for (ItemStack itemStack : inventory) {
                if (i == 0) {
                    item = itemStack;
                    break;
                }
                i--;
            }
            if (i != 0 && a != null)
                throw new InternalExpressionException("Unknown inventory slot: " + a.getString());

            if (item == null)
                return Value.NULL;
            else
                return ValueConversions.of(item);
        });

        put("selected_slot", (e, a) -> {
            if (e instanceof EntityPlayer)
                return new NumericValue(((EntityPlayer) e).inventory.currentItem); //getInventory
            return Value.NULL;
        });

        put("count", (e, a) -> (e instanceof EntityItem) ? new NumericValue(((EntityItem) e).getItem().getCount()) : Value.NULL);
        put("pickup_delay", (e, a) -> (e instanceof EntityItem) ? new NumericValue(((EntityItem) e).getPickupDelay()) : Value.NULL);
        put("portal_cooldown", (e, a) -> new NumericValue(e.timeUntilPortal));
        put("portal_timer", (e, a) -> new NumericValue(e.getMaxInPortalTime()));
        put("is_baby", (e, a) -> (e instanceof EntityLivingBase) ? BooleanValue.of(((EntityLivingBase) e).isChild()) : Value.NULL);
        //todo targetting
        //put("target", (e, a) -> {
        //    if (e instanceof MobEntity)
        //    {
        //        EntityLivingBase target = ((MobEntity) e).getTarget(); // there is also getAttacking in living....
        //        if (target != null)
        //        {
        //            return new EntityValue(target);
        //        }
        //    }
        //    return Value.NULL;
        //});
        //put("home", (e, a) -> {
        //    if (e instanceof MobEntity)
        //    {
        //        return (((MobEntity) e).getPositionTargetRange () > 0)?new BlockValue(null, (ServerWorld) e.getEntityWorld(), ((PathAwareEntity) e).getPositionTarget()):Value.FALSE;
        //    }
        //    return Value.NULL;
        //});
        put("spawn_point", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                EntityPlayerMP spe = (EntityPlayerMP) e;
                return ListValue.of(
                        ValueConversions.of(spe.getBedLocation()),
                        BooleanValue.of(spe.isSpawnForced())
                );
            }
            return Value.NULL;
        });
        put("sneaking", (e, a) -> BooleanValue.of(e.isSneaking()));
        put("sprinting", (e, a) -> BooleanValue.of(e.isSprinting()));
        put("swimming", (e, a) -> BooleanValue.of(e.isInWater()));

        put("air", (e, a) -> new NumericValue(e.getAir()));
        put("language", (e, a) -> e instanceof EntityPlayerMP ? StringValue.of(((EntityPlayerMP) e).getLanguage()) : Value.NULL);
        put("persistence", (e, a) -> (e instanceof EntityLiving) ? BooleanValue.of(((EntityLiving) e).isPersistenceRequired()) : Value.NULL);
        put("hunger", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).getFoodStats().getFoodLevel()) : Value.NULL);
        put("saturation", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).getFoodStats().getSaturationLevel()) : Value.NULL);
        put("exhaustion", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).getFoodStats().getFoodExhaustionLevel()) : Value.NULL);
        put("absorption", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).getAbsorptionAmount()) : Value.NULL);
        put("xp", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).experienceTotal) : Value.NULL);
        put("xp_level", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).experienceLevel) : Value.NULL);
        put("xp_progress", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).experience) : Value.NULL);
        put("score", (e, a) -> (e instanceof EntityPlayer) ? new NumericValue(((EntityPlayer) e).getScore()) : Value.NULL);
        put("jumping", (e, a) -> (e instanceof EntityLivingBase) ? new NumericValue(((EntityLivingBase) e).getJumping()) : Value.NULL);
        put("gamemode", (e, a) -> (e instanceof EntityPlayerMP) ? StringValue.of(((EntityPlayerMP) e).interactionManager.getGameType().getName()) : Value.NULL);
        put("gamemode_id", (e, a) -> (e instanceof EntityPlayerMP) ? new NumericValue(((EntityPlayerMP) e).interactionManager.getGameType().getID()) : Value.NULL);
        //todo pathing/AI stuff
        //put("path", (e, a) -> {
        //    if (e instanceof MobEntity)
        //    {
        //        Path path = ((MobEntity)e).getNavigation().getCurrentPath();
        //        if (path == null) return Value.NULL;
        //        return ValueConversions.fromPath((ServerWorld)e.getEntityWorld(), path);
        //    }
        //    return Value.NULL;
        //});
        //put("brain", (e, a) -> {
        //    String module = a.getString();
        //    MemoryModuleType<?> moduleType = Registry.MEMORY_MODULE_TYPE.get(InputValidator.identifierOf(module));
        //    if (moduleType == MemoryModuleType.DUMMY) return Value.NULL;
        //    if (e instanceof EntityLivingBase EntityLivingBase)
        //    {
        //        Brain<?> brain = EntityLivingBase.getBrain();
        //        Map<MemoryModuleType<?>, Optional<? extends Memory<?>>> memories = ((BrainInterface)brain).getMobMemories();
        //        Optional<? extends Memory<?>> optmemory = memories.get(moduleType);
        //        if (optmemory==null || !optmemory.isPresent()) return Value.NULL;
        //        Memory<?> memory = optmemory.get();
        //        return ValueConversions.fromTimedMemory(e, ((MemoryInterface)memory).getScarpetExpiry(), memory.getValue());
        //    }
        //    return Value.NULL;
        //});
//
        put("permission_level", (e, a) -> {
            if (!(e instanceof EntityPlayerMP)) return Value.NULL;
            EntityPlayerMP emp = (EntityPlayerMP) e;
            if (emp.server.getPlayerList().canSendCommands(emp.getGameProfile())) {
                UserListOpsEntry userlistopsentry = emp.server.getPlayerList().getOppedPlayers().getEntry(emp.getGameProfile());

                return new NumericValue(userlistopsentry.getPermissionLevel());
            } else {
                return Value.NULL;
            }
        });


        put("player_type", (e, a) -> {
            if (e instanceof EntityPlayer) {
                if (e instanceof EntityPlayerMPFake) return new StringValue("fake");
                return new StringValue("multiplayer");
            }
            return Value.NULL;
        });

        //todo client brand
        //put("client_brand", (e, a) -> {
        //    if (e instanceof EntityPlayerMP)
        //    {
        //        return StringValue.of(ServerNetworkHandler.getPlayerStatus((EntityPlayerMP) e));
        //    }
        //    return Value.NULL;
        //});

        put("ping", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                EntityPlayerMP spe = (EntityPlayerMP) e;
                return new NumericValue(spe.ping);
            }
            return Value.NULL;
        });

        //todo effects
        //put("effect", (e, a) ->
        //{
        //    if (!(e instanceof EntityLivingBase))
        //    {
        //        return Value.NULL;
        //    }
        //    if (a == null)
        //    {
        //        List<Value> effects = new ArrayList<>();
        //        for (StatusEffectInstance p : ((EntityLivingBase) e).getStatusEffects())
        //        {
        //            effects.add(ListValue.of(
        //                    new StringValue(p.getTranslationKey().replaceFirst("^effect\\.minecraft\\.", "")),
        //                    new NumericValue(p.getAmplifier()),
        //                    new NumericValue(p.getDuration())
        //            ));
        //        }
        //        return ListValue.wrap(effects);
        //    }
        //    String effectName = a.getString();
        //    StatusEffect potion = Registry.STATUS_EFFECT.get(InputValidator.identifierOf(effectName));
        //    if (potion == null)
        //        throw new InternalExpressionException("No such an effect: "+effectName);
        //    if (!((EntityLivingBase) e).hasStatusEffect(potion))
        //        return Value.NULL;
        //    StatusEffectInstance pe = ((EntityLivingBase) e).getStatusEffect(potion);
        //    return ListValue.of( new NumericValue(pe.getAmplifier()), new NumericValue(pe.getDuration()) );
        //});
//
        put("health", (e, a) ->
        {
            if (e instanceof EntityLivingBase) {
                return new NumericValue(((EntityLivingBase) e).getHealth());
            }
            if (e instanceof EntityItem) {
                new NumericValue(((EntityItem) e).getHealth());
            }
            return Value.NULL;
        });

        put("may_fly", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                return BooleanValue.of(((EntityPlayerMP) e).capabilities.allowFlying);
            }
            return Value.NULL;
        });

        put("flying", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                return BooleanValue.of(((EntityPlayerMP) e).capabilities.isFlying);
            }
            return Value.NULL;
        });

        put("may_build", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                return BooleanValue.of(((EntityPlayerMP) e).capabilities.allowEdit);
            }
            return Value.NULL;
        });

        put("insta_build", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                return BooleanValue.of(((EntityPlayerMP) e).capabilities.isCreativeMode);
            }
            return Value.NULL;
        });

        put("fly_speed", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                return NumericValue.of(((EntityPlayerMP) e).capabilities.getFlySpeed());
            }
            return Value.NULL;
        });

        put("walk_speed", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                return NumericValue.of(((EntityPlayerMP) e).capabilities.getWalkSpeed());
            }
            return Value.NULL;
        });


        put("active_block", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                return ValueConversions.of(((EntityPlayerMP) e).interactionManager.getDestroyPos());
            }
            return Value.NULL;
        });

        put("breaking_progress", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                return new NumericValue(((EntityPlayerMP) e).interactionManager.getDurabilityRemainingOnBlock());
            }
            return Value.NULL;
        });

        //todo facing/tracing
        //put("facing", (e, a) -> {
        //    int index = 0;
        //    if (a != null)
        //        index = (6+(int)NumericValue.asNumber(a).getLong())%6;
        //    if (index < 0 || index > 5)
        //        throw new InternalExpressionException("Facing order should be between -6 and 5");
        //    return new StringValue(Direction.getEntityFacingOrder(e)[index].asString());
        //});
        //put("trace", (e, a) ->
        //{
        //    float reach = 4.5f;
        //    boolean entities = true;
        //    boolean liquids = false;
        //    boolean blocks = true;
        //    boolean exact = false;
//
        //    if (a!=null)
        //    {
        //        if (!(a instanceof ListValue))
        //        {
        //            reach = (float) NumericValue.asNumber(a).getDouble();
        //        }
        //        else
        //        {
        //            List<Value> args = ((ListValue) a).getItems();
        //            if (args.size()==0)
        //                throw new InternalExpressionException("'trace' needs more arguments");
        //            reach = (float) NumericValue.asNumber(args.get(0)).getDouble();
        //            if (args.size() > 1)
        //            {
        //                entities = false;
        //                blocks = false;
        //                for (int i = 1; i < args.size(); i++)
        //                {
        //                    String what = args.get(i).getString();
        //                    if (what.equalsIgnoreCase("entities"))
        //                        entities = true;
        //                    else if (what.equalsIgnoreCase("blocks"))
        //                        blocks = true;
        //                    else if (what.equalsIgnoreCase("liquids"))
        //                        liquids = true;
        //                    else if (what.equalsIgnoreCase("exact"))
        //                        exact = true;
//
        //                    else throw new InternalExpressionException("Incorrect tracing: "+what);
        //                }
        //            }
        //        }
        //    }
        //    else if (e instanceof EntityPlayerMP && ((EntityPlayerMP) e).interactionManager.isCreative())
        //    {
        //        reach = 5.0f;
        //    }
//
        //    HitResult hitres;
        //    if (entities && !blocks)
        //        hitres = Tracer.rayTraceEntities(e, 1, reach, reach*reach);
        //    else if (entities)
        //        hitres = Tracer.rayTrace(e, 1, reach, liquids);
        //    else
        //        hitres = Tracer.rayTraceBlocks(e, 1, reach, liquids);
//
        //    if (hitres == null) return Value.NULL;
        //    if (exact && hitres.getType() != HitResult.Type.MISS) return ValueConversions.of(hitres.getPos());
        //    switch (hitres.getType())
        //    {
        //        case MISS: return Value.NULL;
        //        case BLOCK: return new BlockValue(null, (ServerWorld) e.getEntityWorld(), ((BlockHitResult)hitres).getBlockPos() );
        //        case ENTITY: return new EntityValue(((EntityHitResult)hitres).getEntity());
        //    }
        //    return Value.NULL;
        //});

        put("attribute", (e, a) -> {
            if (!(e instanceof EntityLivingBase)) return Value.NULL;
            EntityLivingBase el = (EntityLivingBase) e;
            if (a == null) {
                AbstractAttributeMap container = el.getAttributeMap();
                Map<String, IAttributeInstance> attributeInstanceMap = container.getAttributesByNameMap();
                Map<Value, Value> valueAttributeInstanceMap = new HashMap<>();
                for (Entry<String, IAttributeInstance> entry : attributeInstanceMap.entrySet()) {
                    valueAttributeInstanceMap.put(StringValue.of(entry.getKey()), new NumericValue(entry.getValue().getAttributeValue()));
                }
                return MapValue.wrap(valueAttributeInstanceMap);
            }
            String attribute = a.getString();
            return NumericValue.of(el.getAttributeMap().getAttributeInstanceByName(attribute).getAttributeValue());
        });

        //todo nbt
        //put("nbt",(e, a) -> {
        //    NbtCompound nbttagcompound = e.writeNbt((new NbtCompound()));
        //    if (a==null)
        //        return new NBTSerializableValue(nbttagcompound);
        //    return new NBTSerializableValue(nbttagcompound).get(a);
        //});

        put("category", (e, a) -> {
            String category;
            if (e instanceof IMob)
                category = "monster";
            else if (e instanceof EntityAnimal)
                category = "creature";
            else if (e instanceof EntityAmbientCreature)
                category = "ambient";
            else if (e instanceof EntityWaterMob)
                category = "water";
            else
                category = "misc";
            return StringValue.of(category);
        });
    }};
    public static final Map<String, BiConsumer<Entity, Value>> featureModifiers = new HashMap<String, BiConsumer<Entity, Value>>() {{
        //put("test", (e, a) -> a == null ? Value.NULL : new StringValue(a.getString()));
        put("pos", (e, a) -> {
            Vec3d newPos = ValueConversions.vec3d(a);
            e.setPositionAndUpdate(newPos.x, newPos.y, newPos.z);
        });
        put("location", (e, a) -> {
            List<Value> lv = ((ListValue) a).getItems();
            Vec3d newPos = ValueConversions.vec3d(ListValue.wrap(lv.subList(0, 2)));
            float yaw = (float) lv.get(0).readNumber();
            float pitch = (float) lv.get(1).readNumber();
            e.setLocationAndAngles(newPos.x, newPos.y, newPos.z, yaw, pitch);
        });
        put("x", (e, a) -> e.setPositionAndUpdate(a.readNumber(), e.posY, e.posZ));
        put("y", (e, a) -> e.setPositionAndUpdate(e.posX, a.readNumber(), e.posZ));
        put("z", (e, a) -> e.setPositionAndUpdate(e.posX, e.posY, a.readNumber()));
        put("motion", (e, a) -> e.setVelocity(ValueConversions.vec3d(a)));
        put("motion_x", (e, a) -> e.setVelocity(a.readNumber(), e.motionY, e.motionZ));
        put("motion_y", (e, a) -> e.setVelocity(e.motionX, a.readNumber(), e.motionZ));
        put("motion_z", (e, a) -> e.setVelocity(e.motionX, e.motionY, a.readNumber()));
        put("name", (e, a) -> e.setCustomNameTag(a.getString()));
        ////put("display_name", (e, a) -> new FormattedTextValue(e.getDisplayName()));todo FormattedTextValue
        ////todo test entity type vs command name
        //put("custom_name", (e, a) -> e.hasCustomName() ? new StringValue(e.getCustomNameTag()) : Value.NULL);
        //todo riding
        put("riding", (e, a) -> {
            if (a == NULL) {
                e.dismountRidingEntity();
            } else if (a instanceof EntityValue) {
                e.startRiding(((EntityValue) a).getEntity(), true);
            } else {
                throw new InternalExpressionException("Second argument to modify(e, 'riding') should be an entity to ride");
            }
        });
        //put("passengers", (e, a) -> ListValue.wrap(e.getPassengers().stream().map(EntityValue::new).collect(Collectors.toList())));
        //put("mount", (e, a) -> e.isRiding() ? new EntityValue(e.getRidingEntity()) : Value.NULL);
        ////todo scoreboards
        ////put("scoreboard_tags", (e, a) -> ListValue.wrap(e.getScoreboardTags().stream().map(StringValue::new).collect(Collectors.toList())));
        ////put("has_scoreboard_tag", (e, a) -> BooleanValue.of(e.getScoreboardTags().contains(a.getString())));
        ////put("team", (e, a) -> e.getScoreboardTeam()==null?Value.NULL:new StringValue(e.getScoreboardTeam().getName()));
//
//
        ////todo entity tags
        ////put("entity_tags", (e, a) -> ListValue.wrap(e.getServer().getTagManager().getOrCreateTagGroup(Registry.ENTITY_TYPE_KEY).getTags().entrySet().stream().filter(entry -> entry.getValue().contains(e.getType())).map(entry -> ValueConversions.of(entry.getKey())).collect(Collectors.toList())));
//
        ////put("has_entity_tag", (e, a) -> {
        ////    Tag<EntityType<?>> tag = e.getServer().getTagManager().getOrCreateTagGroup(Registry.ENTITY_TYPE_KEY).getTag(InputValidator.identifierOf(a.getString()));
        ////    if (tag == null) return Value.NULL;
        ////    return BooleanValue.of(e.getType().isIn(tag));
        ////});
//
        put("yaw", (e, a) -> e.setRotation((float) a.readNumber(), e.rotationPitch));
        put("head_yaw", (e, a) -> e.setRotationYawHead((float) a.readNumber()));
        ////todo body yaw
        ////put("body_yaw", (e, a)-> {
        ////    if (e instanceof EntityLivingBase)
        ////    {
        ////        return  new NumericValue(((EntityLivingBase) e).bodyYaw);
        ////    }
        ////    return Value.NULL;
        ////});
        put("pitch", (e, a) -> e.setRotation(e.rotationYaw, (float) a.readNumber()));
        put("look", (e, a) -> {
            Vec3d vec = ValueConversions.vec3d(a).normalize();

            float pitch = (float) -(Math.asin(vec.y) * 57.2957795131);
            float f2 = -MathHelper.cos(-pitch * 0.017453292F);
            float yawDirty = (float) -Math.asin(vec.x / f2);
            float yawClean;
            if (vec.z >= 0) {
                yawClean = -yawDirty;
            } else {
                yawClean = (float) (yawDirty - 180 * Math.signum(vec.x));
            }

            e.setRotation(yawClean, pitch);
        });
        put("fire", (e, a) -> e.setFire(a.readInt()));
        put("silent", (e, a) -> e.setSilent(a.getBoolean()));
        put("gravity", (e, a) -> e.setNoGravity(!a.getBoolean()));
        put("immune_to_fire", (e, a) -> e.setImmuneToFire(a.getBoolean()));
        put("invulnerable", (e, a) -> e.setEntityInvulnerable(a.getBoolean()));
        put("dimension", (e, a) -> e.changeDimension(a.readInt()));
        put("age", (e, a) -> e.ticksExisted = a.readInt());
        put("breeding_age", (e, a) -> {
            if (e instanceof EntityAgeable) {
                EntityAgeable ea = (EntityAgeable) e;
                ea.setGrowingAge(a.readInt());
            }
        });
        put("despawn_timer", (e, a) -> {
            if (e instanceof EntityLiving) {
                EntityLiving el = (EntityLiving) e;
                el.setIdleTime(a.readInt());
            }
        });
        put("item", (e, a) -> {
            if (e instanceof EntityItem) {
                EntityItem item = ((EntityItem) e);
                int count = item.getItem().getCount();
                //noinspection ConstantConditions
                item.setItem(new ItemStack(Item.getByNameOrId(a.getString()), count));
            }
        });

        put("selected_slot", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).inventory.currentItem = a.readInt();
        });

        put("count", (e, a) -> {
            if (e instanceof EntityItem) ((EntityItem) e).getItem().setCount(a.readInt());
        });
        put("pickup_delay", (e, a) -> {
            if (e instanceof EntityItem) ((EntityItem) e).setPickupDelay(a.readInt());
        });
        put("portal_cooldown", (e, a) -> e.timeUntilPortal = a.readInt());
        put("is_baby", (e, a) -> {
            if (e instanceof EntityAgeable) {
                EntityAgeable ea = (EntityAgeable) e;
                ea.setGrowingAge(a.readInt());
            } else if (e instanceof EntityArmorStand) {
                EntityArmorStand eas = (EntityArmorStand) e;
                eas.setSmall(a.getBoolean());
            } else if (e instanceof EntityZombie) {
                EntityZombie ez = (EntityZombie) e;
                ez.setChild(a.getBoolean());
            }
        });
        ////todo targetting
        ////put("target", (e, a) -> {
        ////    if (e instanceof MobEntity)
        ////    {
        ////        EntityLivingBase target = ((MobEntity) e).getTarget(); // there is also getAttacking in living....
        ////        if (target != null)
        ////        {
        ////            return new EntityValue(target);
        ////        }
        ////    }
        ////    return Value.NULL;
        ////});
        ////put("home", (e, a) -> {
        ////    if (e instanceof MobEntity)
        ////    {
        ////        return (((MobEntity) e).getPositionTargetRange () > 0)?new BlockValue(null, (ServerWorld) e.getEntityWorld(), ((PathAwareEntity) e).getPositionTarget()):Value.FALSE;
        ////    }
        ////    return Value.NULL;
        ////});
        //put("spawn_point", (e, a) -> {
        //    if (e instanceof EntityPlayerMP) {
        //        EntityPlayerMP spe = (EntityPlayerMP) e;
        //        return ListValue.of(
        //                ValueConversions.of(spe.getBedLocation()),
        //                BooleanValue.of(spe.isSpawnForced())
        //        );
        //    }
        //    return Value.NULL;
        //});

        put("air", (e, a) -> e.setAir(a.readInt()));
        put("persistence", (e, a) -> {
            if (e instanceof EntityLiving) ((EntityLiving) e).setPersistence(a.getBoolean());
        });
        put("hunger", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).getFoodStats().setFoodLevel(a.readInt());
        });
        put("saturation", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).getFoodStats().setSaturationLevel((float) a.readNumber());
        });
        put("exhaustion", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).getFoodStats().setExhaustionLevel((float) a.readNumber());
        });
        put("absorption", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setAbsorptionAmount((float) a.readNumber());
        });
        put("xp", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).experienceTotal = a.readInt();
        });
        put("xp_progress", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).experience = a.readInt();
        });
        put("score", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setScore(a.readInt());
        });
        put("jump", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).jump();
        });
        put("jumping", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setJumping(a.getBoolean());
        });
        put("gamemode", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setGameType(GameType.valueOf(a.getString()));
        });
        put("gamemode_id", (e, a) -> {
            if (e instanceof EntityPlayer) ((EntityPlayer) e).setGameType(GameType.getByID(a.readInt()));
        });

        ////todo pathing/AI stuff
        ////put("path", (e, a) -> {
        ////    if (e instanceof MobEntity)
        ////    {
        ////        Path path = ((MobEntity)e).getNavigation().getCurrentPath();
        ////        if (path == null) return Value.NULL;
        ////        return ValueConversions.fromPath((ServerWorld)e.getEntityWorld(), path);
        ////    }
        ////    return Value.NULL;
        ////});
        ////put("brain", (e, a) -> {
        ////    String module = a.getString();
        ////    MemoryModuleType<?> moduleType = Registry.MEMORY_MODULE_TYPE.get(InputValidator.identifierOf(module));
        ////    if (moduleType == MemoryModuleType.DUMMY) return Value.NULL;
        ////    if (e instanceof EntityLivingBase EntityLivingBase)
        ////    {
        ////        Brain<?> brain = EntityLivingBase.getBrain();
        ////        Map<MemoryModuleType<?>, Optional<? extends Memory<?>>> memories = ((BrainInterface)brain).getMobMemories();
        ////        Optional<? extends Memory<?>> optmemory = memories.get(moduleType);
        ////        if (optmemory==null || !optmemory.isPresent()) return Value.NULL;
        ////        Memory<?> memory = optmemory.get();
        ////        return ValueConversions.fromTimedMemory(e, ((MemoryInterface)memory).getScarpetExpiry(), memory.getValue());
        ////    }
        ////    return Value.NULL;
        ////});
////
        put("permission_level", (e, a) -> {
            if (!(e instanceof EntityPlayerMP)) return;
            EntityPlayerMP emp = (EntityPlayerMP) e;
            if (emp.server.getPlayerList().canSendCommands(emp.getGameProfile())) {
                UserListOpsEntry userlistopsentry = emp.server.getPlayerList().getOppedPlayers().getEntry(emp.getGameProfile());

                userlistopsentry.setPermissionLevel(a.readInt());
            }
        });
//
//
        ////todo client brand
        ////put("client_brand", (e, a) -> {
        ////    if (e instanceof EntityPlayerMP)
        ////    {
        ////        return StringValue.of(ServerNetworkHandler.getPlayerStatus((EntityPlayerMP) e));
        ////    }
        ////    return Value.NULL;
        ////});
//

        ////todo effects
        ////put("effect", (e, a) ->
        ////{
        ////    if (!(e instanceof EntityLivingBase))
        ////    {
        ////        return Value.NULL;
        ////    }
        ////    if (a == null)
        ////    {
        ////        List<Value> effects = new ArrayList<>();
        ////        for (StatusEffectInstance p : ((EntityLivingBase) e).getStatusEffects())
        ////        {
        ////            effects.add(ListValue.of(
        ////                    new StringValue(p.getTranslationKey().replaceFirst("^effect\\.minecraft\\.", "")),
        ////                    new NumericValue(p.getAmplifier()),
        ////                    new NumericValue(p.getDuration())
        ////            ));
        ////        }
        ////        return ListValue.wrap(effects);
        ////    }
        ////    String effectName = a.getString();
        ////    StatusEffect potion = Registry.STATUS_EFFECT.get(InputValidator.identifierOf(effectName));
        ////    if (potion == null)
        ////        throw new InternalExpressionException("No such an effect: "+effectName);
        ////    if (!((EntityLivingBase) e).hasStatusEffect(potion))
        ////        return Value.NULL;
        ////    StatusEffectInstance pe = ((EntityLivingBase) e).getStatusEffect(potion);
        ////    return ListValue.of( new NumericValue(pe.getAmplifier()), new NumericValue(pe.getDuration()) );
        ////});
////
        put("health", (e, a) -> {
            if (e instanceof EntityLivingBase) {
                ((EntityLivingBase) e).setHealth((float) a.readNumber());
            }
            if (e instanceof EntityItem) {
                ((EntityItem) e).setHealth(a.readInt());
            }
        });

        put("may_fly", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).capabilities.allowFlying = v.getBoolean();
            }
        });

        put("flying", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).capabilities.isFlying = v.getBoolean();
            }
        });

        put("may_build", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).capabilities.allowEdit = v.getBoolean();
            }
        });

        put("insta_build", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).capabilities.isCreativeMode = v.getBoolean();
            }
        });

        put("fly_speed", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).capabilities.setFlySpeed((float) v.readNumber());
            }
        });

        put("walk_speed", (e, v) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).capabilities.setWalkSpeed((float) v.readNumber());
            }
        });

        put("active_block", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).interactionManager.setDestroyPos(((BlockValue) a).getPos());
            }
        });

        put("breaking_progress", (e, a) -> {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).interactionManager.setDurabilityRemainingOnBlock(a.readInt());
            }
        });
//
        ////todo facing/tracing
        ////put("facing", (e, a) -> {
        ////    int index = 0;
        ////    if (a != null)
        ////        index = (6+(int)NumericValue.asNumber(a).getLong())%6;
        ////    if (index < 0 || index > 5)
        ////        throw new InternalExpressionException("Facing order should be between -6 and 5");
        ////    return new StringValue(Direction.getEntityFacingOrder(e)[index].asString());
        ////});
        ////put("trace", (e, a) ->
        ////{
        ////    float reach = 4.5f;
        ////    boolean entities = true;
        ////    boolean liquids = false;
        ////    boolean blocks = true;
        ////    boolean exact = false;
////
        ////    if (a!=null)
        ////    {
        ////        if (!(a instanceof ListValue))
        ////        {
        ////            reach = (float) NumericValue.asNumber(a).getDouble();
        ////        }
        ////        else
        ////        {
        ////            List<Value> args = ((ListValue) a).getItems();
        ////            if (args.size()==0)
        ////                throw new InternalExpressionException("'trace' needs more arguments");
        ////            reach = (float) NumericValue.asNumber(args.get(0)).getDouble();
        ////            if (args.size() > 1)
        ////            {
        ////                entities = false;
        ////                blocks = false;
        ////                for (int i = 1; i < args.size(); i++)
        ////                {
        ////                    String what = args.get(i).getString();
        ////                    if (what.equalsIgnoreCase("entities"))
        ////                        entities = true;
        ////                    else if (what.equalsIgnoreCase("blocks"))
        ////                        blocks = true;
        ////                    else if (what.equalsIgnoreCase("liquids"))
        ////                        liquids = true;
        ////                    else if (what.equalsIgnoreCase("exact"))
        ////                        exact = true;
////
        ////                    else throw new InternalExpressionException("Incorrect tracing: "+what);
        ////                }
        ////            }
        ////        }
        ////    }
        ////    else if (e instanceof EntityPlayerMP && ((EntityPlayerMP) e).interactionManager.isCreative())
        ////    {
        ////        reach = 5.0f;
        ////    }
////
        ////    HitResult hitres;
        ////    if (entities && !blocks)
        ////        hitres = Tracer.rayTraceEntities(e, 1, reach, reach*reach);
        ////    else if (entities)
        ////        hitres = Tracer.rayTrace(e, 1, reach, liquids);
        ////    else
        ////        hitres = Tracer.rayTraceBlocks(e, 1, reach, liquids);
////
        ////    if (hitres == null) return Value.NULL;
        ////    if (exact && hitres.getType() != HitResult.Type.MISS) return ValueConversions.of(hitres.getPos());
        ////    switch (hitres.getType())
        ////    {
        ////        case MISS: return Value.NULL;
        ////        case BLOCK: return new BlockValue(null, (ServerWorld) e.getEntityWorld(), ((BlockHitResult)hitres).getBlockPos() );
        ////        case ENTITY: return new EntityValue(((EntityHitResult)hitres).getEntity());
        ////    }
        ////    return Value.NULL;
        ////});
//
        //put("attribute", (e, a) -> {
        //    if (!(e instanceof EntityLivingBase)) return Value.NULL;
        //    EntityLivingBase el = (EntityLivingBase) e;
        //    if (a == null) {
        //        AbstractAttributeMap container = el.getAttributeMap();
        //        Map<String, IAttributeInstance> attributeInstanceMap = container.getAttributesByNameMap();
        //        Map<Value, Value> valueAttributeInstanceMap = new HashMap<>();
        //        for (Entry<String, IAttributeInstance> entry : attributeInstanceMap.entrySet()) {
        //            valueAttributeInstanceMap.put(StringValue.of(entry.getKey()), new NumericValue(entry.getValue().getAttributeValue()));
        //        }
        //        return MapValue.wrap(valueAttributeInstanceMap);
        //    }
        //    String attribute = a.getString();
        //    return NumericValue.of(el.getAttributeMap().getAttributeInstanceByName(attribute).getAttributeValue());
        //});
//
        ////todo nbt
        ////put("nbt",(e, a) -> {
        ////    NbtCompound nbttagcompound = e.writeNbt((new NbtCompound()));
        ////    if (a==null)
        ////        return new NBTSerializableValue(nbttagcompound);
        ////    return new NBTSerializableValue(nbttagcompound).get(a);
        ////});
//
        //put("category", (e, a) -> {
        //    String category;
        //    if (e instanceof IMob)
        //        category = "monster";
        //    else if (e instanceof EntityAnimal)
        //        category = "creature";
        //    else if (e instanceof EntityAmbientCreature)
        //        category = "ambient";
        //    else if (e instanceof EntityWaterMob)
        //        category = "water";
        //    else
        //        category = "misc";
        //    return StringValue.of(category);
        //});
    }};
    private final Entity entity;

    public EntityValue(Entity e) {
        entity = e;
    }

    public static Value of(Entity e) {
        if (e == null) return Value.NULL;
        return new EntityValue(e);
    }

    public static Collection<? extends Entity> getEntitiesFromSelector(ICommandSender source, String selector) {
        try {
            return EntitySelector.matchEntities(source, selector, Entity.class);
        } catch (Exception e) {
            throw new InternalExpressionException("Cannot select entities from " + selector);
        }
    }

    public static EntityPlayerMP getPlayerByValue(MinecraftServer server, Value value) {
        EntityPlayerMP player = null;
        if (value instanceof EntityValue) {
            Entity e = ((EntityValue) value).getEntity();
            if (e instanceof EntityPlayerMP) {
                player = (EntityPlayerMP) e;
            }
        } else if (value == Value.NULL) {
            return null;
        } else {
            String playerName = value.getString();
            player = server.getPlayerList().getPlayerByUsername(playerName);
        }
        return player;
    }

    public Entity getEntity() {
        return entity;
    }

    @Override
    public String getString() {
        return entity.getName();
    }

    @Override
    public String getTypeString() {
        return "entity";
    }

    @Override
    public boolean getBoolean() {
        return true;
    }

    @Override
    public boolean equals(Object v) {
        if (v instanceof EntityValue) {
            return getEntity().getEntityId() == ((EntityValue) v).getEntity().getEntityId();
        }
        return super.equals((Value) v);
    }

    @Override
    public Value in(Value v) {
        if (v instanceof ListValue) {
            List<Value> values = ((ListValue) v).getItems();
            String what = values.get(0).getString();
            Value arg = null;
            if (values.size() == 2) {
                arg = values.get(1);
            } else if (values.size() > 2) {
                arg = ListValue.wrap(values.subList(1, values.size()));
            }
            return this.get(what, arg);
        }
        String what = v.getString();
        return this.get(what, null);
    }

    public Value get(String what, Value arg) {
        if (!(featureAccessors.containsKey(what)))
            throw new InternalExpressionException("Unknown entity feature: " + what);
        try {
            return featureAccessors.get(what).apply(getEntity(), arg);
        } catch (NullPointerException npe) {
            throw new InternalExpressionException("Cannot fetch '" + what + "' with these arguments");
        }
    }

    public void set(String what, Value toWhat) {
        if (!(featureModifiers.containsKey(what)))
            throw new InternalExpressionException("Unknown entity action: " + what);
        try {
            featureModifiers.get(what).accept(getEntity(), toWhat);
        } catch (NullPointerException npe) {
            throw new InternalExpressionException("'modify' for '" + what + "' expects a value");
        } catch (IndexOutOfBoundsException ind) {
            throw new InternalExpressionException("Wrong number of arguments for `modify` option: " + what);
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getString());
    }

    @Override
    public NBTBase toNbt() {
        return entity.writeToNBT();
    }

    @Override
    public int hashCode() {
        return getEntity().hashCode();
    }
}
