package com.solacemod.solace.entity;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.task.SolaceTask;
import com.solacemod.solace.story.SolaceBrain;
import com.solacemod.solace.story.SolaceManager;
import com.solacemod.solace.story.StoryDirector;
import com.solacemod.solace.util.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Solace's body in the world. The "thinking" happens in {@link SolaceBrain}; this class handles moving, fighting,
 * carrying items, and the boss form ("the Hollow") used in one of the endings.
 */
public class SolaceEntity extends PathfinderMob {
    public static final int FORM_NORMAL = 0;
    public static final int FORM_UNEASY = 1;
    public static final int FORM_HOLLOW = 2;
    /** The true form, shown only for a few seconds at a time (big powers, strong feelings, story moments). */
    public static final int FORM_LANTERN = 3;
    /** The Lantern, but enormous: it fills the sky (the chapter 6 confession). */
    public static final int FORM_VAST = 4;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PRESENCE = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CORRUPTION = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AGITATED = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NEGATIVE = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.BOOLEAN);

    public enum Mode { FOLLOW, STAY, WANDER }

    public final SimpleContainer inventory = new SimpleContainer(27);
    /** How Solace behaves in your fights: it watches, cheers, and now and then one-taps something. */
    public final SolaceCombat combat = new SolaceCombat(this);
    /** How Solace moves right now: mostly like a player, but sometimes wrong (see Presence). */
    public final Presence presence = new Presence(this);
    private Mode mode = Mode.FOLLOW;
    private boolean guard;
    private BlockPos stayPos;
    @Nullable
    private SolaceTask task;
    private int stareTicks;
    @Nullable
    private Entity stareTarget;
    /** A spot it stares at instead of someone (a build it noticed), or null. */
    @Nullable
    private Vec3 stareSpot;
    private int lanternTicks;
    private int formBeforeLantern = FORM_NORMAL;
    private int agitatedTicks;
    private int negativeTicks;

    // Boss ("Hollow") state
    @Nullable
    private ServerBossEvent bossEvent;
    private int abilityCooldown = 100;
    private boolean phaseTwo;
    private int ownerMissingTicks;
    /** Friends of the owner who have hit the Hollow. Only they (and the owner) feel its attacks. */
    private final java.util.Set<UUID> fighters = new java.util.LinkedHashSet<>();
    /** Above 0 for a test stand-in (another "player's" Solace for /solace admin together): ticks until it leaves. Not saved. */
    public int standInTicks;

    public SolaceEntity(EntityType<? extends SolaceEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_FORM, FORM_NORMAL);
        builder.define(DATA_PRESENCE, Presence.WALK);
        builder.define(DATA_CORRUPTION, 0);
        builder.define(DATA_AGITATED, false);
        builder.define(DATA_NEGATIVE, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SolaceGoals.StareGoal(this));
        this.goalSelector.addGoal(1, new SolaceGoals.PresenceGoal(this));
        this.goalSelector.addGoal(2, new SolaceGoals.TaskGoal(this));
        // Only the Hollow (the boss) fights like a monster. Normally Solace watches and one-taps (see SolaceCombat).
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() {
                return isHollow() && super.canUse();
            }
        });
        this.goalSelector.addGoal(4, new SolaceGoals.FollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new SolaceGoals.StayGoal(this));
        this.goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(7, new SolaceGoals.WanderGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                p -> isHollow() && p.getUUID().equals(getOwnerUUID())));
    }

    // ---------- Owner ----------

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwner(UUID owner) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    @Nullable
    public Player getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Nullable
    public ServerPlayer getServerOwner() {
        return getOwner() instanceof ServerPlayer sp ? sp : null;
    }

    public boolean isOwner(Player player) {
        return player.getUUID().equals(getOwnerUUID());
    }

    // ---------- Mode / tasks ----------

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        if (mode == Mode.STAY) this.stayPos = blockPosition();
    }

    @Nullable
    public BlockPos getStayPos() {
        return stayPos;
    }

    public boolean isGuarding() {
        return guard;
    }

    public void setGuard(boolean guard) {
        this.guard = guard;
        if (!guard && getTarget() instanceof Monster) setTarget(null);
    }

    @Nullable
    public SolaceTask getTask() {
        return task;
    }

    public void setTask(@Nullable SolaceTask newTask) {
        if (this.task != null) this.task.stop(this);
        this.task = newTask;
    }

    public int getStareTicks() {
        return stareTicks;
    }

    public void setStareTicks(int ticks) {
        this.stareTicks = ticks;
        this.stareTarget = null;
        this.stareSpot = null;
        if (ticks > 0) getNavigation().stop();
    }

    /** Stands still and looks at a spot (a build it just noticed). */
    public void stareAtSpot(Vec3 spot, int ticks) {
        setStareTicks(ticks);
        this.stareSpot = spot;
    }

    /** The spot it's staring at, or null. */
    @Nullable
    public Vec3 stareSpot() {
        return stareSpot;
    }

    /** Stands still and stares at someone else (another player, another Solace, the Hollow) instead of its owner. */
    public void stareAt(Entity target, int ticks) {
        setStareTicks(ticks);
        this.stareTarget = target;
    }

    /** Who it's staring at: someone set with {@link #stareAt}, or its owner. */
    @Nullable
    public Entity stareTarget() {
        if (stareTarget != null && stareTarget.isAlive() && stareTarget.level() == level()) return stareTarget;
        return getOwner();
    }

    public void tickStare() {
        if (stareTicks > 0) stareTicks--;
        if (stareTicks == 0) {
            stareTarget = null;
            stareSpot = null;
        }
    }

    // ---------- Form ----------

    public int getForm() {
        return this.entityData.get(DATA_FORM);
    }

    public void setForm(int form) {
        if (getForm() != form) this.entityData.set(DATA_FORM, form);
    }

    public int getPresence() {
        return entityData.get(DATA_PRESENCE);
    }

    void setPresence(int behaviour) {
        entityData.set(DATA_PRESENCE, behaviour);
    }

    /**
     * How far the wrongness has spread, 0 to 8 (the server sets it from the story chapter).
     * The client uses it for the creeping veins and how often he flashes into his negative side.
     */
    public int getCorruption() {
        return entityData.get(DATA_CORRUPTION);
    }

    public void setCorruption(int level) {
        if (level != getCorruption()) entityData.set(DATA_CORRUPTION, level);
    }

    /** Upset: the negative flashes come more often and the Lantern's tendrils thrash. */
    public boolean isAgitated() {
        return entityData.get(DATA_AGITATED);
    }

    public void agitate(int ticks) {
        agitatedTicks = Math.max(agitatedTicks, ticks);
        if (!isAgitated()) entityData.set(DATA_AGITATED, true);
    }

    /** Fully turned into his negative: glowing veins, burning eyes, flowing energy, sparks. */
    public boolean isNegative() {
        return entityData.get(DATA_NEGATIVE);
    }

    /** Turns fully negative for a moment (when he uses a power, kills something, or loses his temper). */
    public void goNegative(int ticks) {
        if (isHollow() || isLanternForm()) return;
        if (!isNegative()) {
            entityData.set(DATA_NEGATIVE, true);
            Sounds.play(level(), position(), "solace:negative_surge", SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        negativeTicks = Math.max(negativeTicks, ticks);
    }

    /** The Lantern shape is drawn for the true form and for the Hollow. */
    public boolean isLanternForm() {
        return getForm() == FORM_LANTERN || getForm() == FORM_VAST || getForm() == FORM_HOLLOW;
    }

    public boolean isShowingLantern() {
        return lanternTicks > 0;
    }

    /** Shows the true form for a moment, then goes back to looking like a person. */
    public void showLantern(int ticks) {
        showLantern(ticks, false);
    }

    public void showLantern(int ticks, boolean vast) {
        if (isHollow()) return;
        if (lanternTicks <= 0) formBeforeLantern = getForm();
        lanternTicks = Math.max(lanternTicks, ticks);
        setForm(vast ? FORM_VAST : FORM_LANTERN);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2, getZ(), 30, 0.8, 1.5, 0.8, 0.02);
            Sounds.playAt(this, "block.beacon.activate", 0.8F, 0.5F);
        }
    }

    /** Back to looking like a person, as if nothing happened. */
    public void endLantern() {
        lanternTicks = 0;
        if (getForm() != FORM_LANTERN && getForm() != FORM_VAST) return;
        setForm(formBeforeLantern);
        ((ServerLevel) level()).sendParticles(ParticleTypes.ASH, getX(), getY() + 1.5, getZ(), 40, 0.6, 1.2, 0.6, 0.02);
    }

    public boolean isHollow() {
        return getForm() == FORM_HOLLOW;
    }

    /** Turns Solace into the boss for the "Free Maren" ending. */
    public void becomeHollow() {
        presence.end();
        setTask(null);
        setGuard(false);
        this.mode = Mode.FOLLOW;
        setForm(FORM_HOLLOW);
        fighters.clear();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HOLLOW_HEALTH);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(9.0);
        getAttribute(Attributes.ARMOR).setBaseValue(10.0);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.34);
        setHealth(getMaxHealth());
        phaseTwo = false;
        abilityCooldown = 80;
        ownerMissingTicks = 0;
        if (bossEvent == null) {
            bossEvent = new ServerBossEvent(Component.literal("The Hollow"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            bossEvent.setDarkenScreen(true);
            bossEvent.setCreateWorldFog(true);
        }
        bossEvent.setProgress(1.0F);
        ServerPlayer owner = getServerOwner();
        if (owner != null) bossEvent.addPlayer(owner);
    }

    /** Back to normal after the player loses the boss fight. */
    public void revertFromHollow() {
        setTarget(null);
        fighters.clear();
        setForm(FORM_UNEASY);
        resetAttributes();
        setHealth(getMaxHealth());
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
            bossEvent = null;
        }
    }

    /** Normal (non-boss) stats. */
    private void resetAttributes() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);
        getAttribute(Attributes.ARMOR).setBaseValue(2.0);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.32);
    }

    // ---------- Ticking ----------

    @Override
    public void aiStep() {
        this.updateSwingTime();
        setAirSupply(getMaxAirSupply()); // Solace never drowns
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Ash drifting off Solace. Thicker as the story darkens.
            int chance = getForm() == FORM_NORMAL ? 6 : 2;
            if (random.nextInt(chance) == 0) {
                level().addParticle(getForm() == FORM_NORMAL ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                        getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.02, 0);
            }
            return;
        }
        if (hurtReactCooldown > 0) hurtReactCooldown--;
        if (agitatedTicks > 0 && --agitatedTicks == 0) entityData.set(DATA_AGITATED, false);
        if (negativeTicks > 0 && --negativeTicks == 0) {
            entityData.set(DATA_NEGATIVE, false);
            Sounds.play(level(), position(), "solace:negative_fade", SoundSource.HOSTILE, 0.8F, 1.0F);
        }
        if (lanternTicks > 0 && --lanternTicks == 0) endLantern();
        // A test stand-in (/solace admin together) belongs to nobody and leaves on its own.
        if (standInTicks > 0) {
            if (--standInTicks == 0) discard();
            return;
        }
        // A stale copy (e.g. one left in an unloaded chunk after Solace was re-summoned) removes itself.
        if ((tickCount == 1 || tickCount % 100 == 0) && !SolaceManager.isCurrent(this)) {
            if (bossEvent != null) bossEvent.removeAllPlayers();
            discard();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (task != null && task.isCombat() && task.tick(this)) {
            task = null;
        }
        if (isHollow()) {
            tickBoss();
        } else {
            combat.tick();
            presence.tick();
            if (tickCount % 10 == 0) pickUpThrownItems();
            if (tickCount % 40 == 0) equipBestWeapon();
        }
    }

    private static final double HOLLOW_HEALTH = 300.0;

    /** A friend of the owner hit the Hollow: now it fights them too, and it gets stronger (up to a few friends). */
    private void onFriendHit(ServerPlayer friend) {
        if (!com.solacemod.solace.config.SolaceConfig.HOLLOW_WITH_FRIENDS.get() || !fighters.add(friend.getUUID())) return;
        if (fighters.size() <= com.solacemod.solace.story.Together.HOLLOW_MAX_FRIENDS) {
            float fraction = getHealth() / getMaxHealth();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(HOLLOW_HEALTH + com.solacemod.solace.story.Together.HOLLOW_HEALTH_PER_FRIEND * fighters.size());
            setHealth(fraction * getMaxHealth());
        }
        com.solacemod.solace.story.Together.onHollowFriend(this, getServerOwner(), friend, fighters.size());
    }

    /** Testing: the boss's health as if this many friends had joined (/solace admin together). */
    public void pretendFriends(int friends) {
        int n = Math.max(0, Math.min(com.solacemod.solace.story.Together.HOLLOW_MAX_FRIENDS, friends));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HOLLOW_HEALTH + com.solacemod.solace.story.Together.HOLLOW_HEALTH_PER_FRIEND * n);
        setHealth(getMaxHealth());
    }

    /** The friends who have fought the Hollow and are still close by. */
    public java.util.List<ServerPlayer> fightersNearby() {
        java.util.List<ServerPlayer> list = new java.util.ArrayList<>();
        if (getServer() == null) return list;
        for (UUID id : fighters) {
            ServerPlayer p = getServer().getPlayerList().getPlayer(id);
            if (p != null && p.isAlive() && !p.isSpectator() && p.level() == level() && p.distanceTo(this) <= 48) list.add(p);
        }
        return list;
    }

    /** Everyone who fought the Hollow (online or not), for the ending. */
    public java.util.Set<UUID> fighters() {
        return java.util.Set.copyOf(fighters);
    }

    private void tickBoss() {
        if (bossEvent != null) bossEvent.setProgress(getHealth() / getMaxHealth());
        ServerPlayer owner = getServerOwner();
        if (owner == null || owner.level() != level() || owner.distanceTo(this) > 64 || owner.isSpectator()) {
            if (++ownerMissingTicks > 600) StoryDirector.onBossAbandoned(this);
            return;
        }
        ownerMissingTicks = 0;
        if (getTarget() != owner) setTarget(owner);
        ServerLevel level = (ServerLevel) level();

        if (!phaseTwo && getHealth() < getMaxHealth() * 0.5F) {
            phaseTwo = true;
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.4);
            SolaceBrain.event(owner, com.solacemod.solace.story.StoryText.BOSS_PHASE_TWO, true);
            spawnVisualLightning(level, owner.blockPosition());
        }
        if (--abilityCooldown > 0) return;
        abilityCooldown = phaseTwo ? 70 : 120;

        java.util.List<ServerPlayer> friends = fightersNearby();
        // With friends fighting, phase two has one more move: it turns on them, because it wants you alone.
        switch (random.nextInt(phaseTwo ? (friends.isEmpty() ? 5 : 6) : 4)) {
            case 0 -> { // teleport behind the player
                Vec3 look = owner.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 behind = owner.position().subtract(look.scale(3));
                if (level.noCollision(this, getBoundingBox().move(behind.subtract(position())))) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.01);
                    teleportTo(behind.x, owner.getY(), behind.z);
                    Sounds.playAt(this, "entity.enderman.teleport", 1.0F, 0.5F);
                }
            }
            case 1 -> { // darkness
                owner.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
                Sounds.play(level, owner.position(), "entity.warden.heartbeat", SoundSource.HOSTILE, 1.5F, 0.6F);
            }
            case 2 -> summonEchoes(level, owner, friends);
            case 3 -> { // shockwave: only the owner and friends who chose to fight feel it
                level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
                Sounds.playAt(this, "entity.warden.sonic_boom", 1.0F, 1.4F);
                java.util.List<ServerPlayer> hit = new java.util.ArrayList<>(friends);
                hit.add(owner);
                for (ServerPlayer p : hit) {
                    if (p.distanceTo(this) < 6) {
                        p.hurt(damageSources().mobAttack(this), 5.0F);
                        p.knockback(1.5, getX() - p.getX(), getZ() - p.getZ());
                    }
                }
            }
            case 5 -> { // "Not them. You.": blinds and slows only the friends
                for (ServerPlayer f : friends) {
                    f.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                    f.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false));
                }
                com.solacemod.solace.story.Together.hollowNotThem(this, owner, friends);
            }
            default -> spawnVisualLightning(level, owner.blockPosition().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
        }
    }

    private void summonEchoes(ServerLevel level, ServerPlayer owner, java.util.List<ServerPlayer> friends) {
        int alive = level.getEntitiesOfClass(Vex.class, getBoundingBox().inflate(32), v -> v.getOwner() == this).size();
        if (alive >= 4) return;
        for (int i = 0; i < 2; i++) {
            Vex vex = EntityType.VEX.create(level);
            if (vex == null) continue;
            vex.moveTo(getX() + random.nextGaussian(), getY() + 1.5, getZ() + random.nextGaussian(), 0, 0);
            vex.setOwner(this);
            vex.setBoundOrigin(blockPosition());
            vex.setLimitedLife(20 * 25);
            vex.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            vex.setCustomName(Component.literal("Echo"));
            vex.setTarget(friends.isEmpty() || random.nextBoolean() ? owner : friends.get(random.nextInt(friends.size())));
            level.addFreshEntity(vex);
        }
        Sounds.playAt(this, "entity.evoker.prepare_summon", 1.0F, 0.6F);
    }

    private static void spawnVisualLightning(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    // ---------- Damage / death ----------

    private int hurtReactCooldown;

    /**
     * Solace can't be hurt. Hitting it does nothing except make it notice (and blink behind you).
     * Only its boss form, the Hollow, can be damaged, so the "Free Maren" ending can be won.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isHollow()) {
            if (!level().isClientSide && source.getEntity() instanceof ServerPlayer p && !isOwner(p)) onFriendHit(p);
            return super.hurt(source, amount);
        }
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Not even /kill or the void: it scatters into ash and is back beside you, as if nothing happened.
            escapeDeath();
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player && isOwner(player) && hurtReactCooldown <= 0) {
            hurtReactCooldown = 60;
            presence.end();
            ServerLevel level = (ServerLevel) level();
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 15, 0.3, 0.6, 0.3, 0.01);
            Sounds.playAt(this, "entity.enderman.teleport", 0.6F, 0.4F);
            blinkBehind(player);
            SolaceBrain.onOwnerHurtSolace(player, this);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (isHollow()) return super.isInvulnerableTo(source);
        return true;
    }

    /** Called instead of dying (to /kill or the void). Only the Hollow can really be beaten. */
    private void escapeDeath() {
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.ASH, getX(), getY() + 1, getZ(), 60, 0.4, 0.9, 0.4, 0.02);
        Sounds.playAt(this, "entity.enderman.teleport", 0.7F, 0.3F);
        setHealth(getMaxHealth());
        fallDistance = 0;
        setDeltaMovement(Vec3.ZERO);
        ServerPlayer owner = getServerOwner();
        if (owner != null && owner.level() == level && owner.getY() > level.getMinBuildHeight()) {
            blinkBehind(owner);
        } else if (getY() < level.getMinBuildHeight() + 1) {
            BlockPos spawn = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
            moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, getYRot(), 0);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (isHollow()) super.knockback(strength, x, z);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    /** The Lantern is drawn much bigger than its hitbox: don't let the game skip drawing it when its feet are off-screen. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        int form = getForm();
        return form == FORM_VAST ? getBoundingBox().inflate(9, 12, 9) : isLanternForm() ? getBoundingBox().inflate(1.5, 2.5, 1.5) : super.getBoundingBoxForCulling();
    }

    /**
     * The hitbox follows the shape: in the Lantern forms (and the Hollow) it's the floating ball, about 1.4 blocks
     * across. In its body it's set in ModEntities: as wide as it can be and still fit through an open door.
     */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        return isLanternForm() ? net.minecraft.world.entity.EntityDimensions.scalable(1.4F, 1.65F) : super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_FORM.equals(key)) refreshDimensions();
    }

    /** It's mostly smoke and light: ceilings don't suffocate it. */
    @Override
    public boolean isInWall() {
        return !isHollow() && super.isInWall();
    }

    /** Solace makes no footstep sounds. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    /** Vanishes and reappears just behind the target, where they aren't looking. */
    public void blinkBehind(LivingEntity target) {
        ServerLevel level = (ServerLevel) level();
        UUID owner = getOwnerUUID();
        if (owner != null && target.getUUID().equals(owner)) com.solacemod.solace.story.Together.showWitnesses(this, owner, "blink", 40);
        Vec3 back = target.getLookAngle().multiply(-1, 0, -1);
        if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
        back = back.normalize();
        for (double dist : new double[]{2.5, 3.5, 1.8}) {
            Vec3 dest = target.position().add(back.scale(dist));
            BlockPos bp = BlockPos.containing(dest);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos p = bp.above(dy);
                if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) continue;
                Vec3 to = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                if (!level.noCollision(this, getBoundingBox().move(to.subtract(position())))) continue;
                level.sendParticles(ParticleTypes.ASH, getX(), getY() + 1, getZ(), 25, 0.3, 0.8, 0.3, 0.01);
                getNavigation().stop();
                moveTo(to.x, to.y, to.z, target.getYRot(), 0);
                level.sendParticles(ParticleTypes.ASH, to.x, to.y + 1, to.z, 25, 0.3, 0.8, 0.3, 0.01);
                return;
            }
        }
        SolaceGoals.teleportNear(this, target);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && isHollow()) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.removeItemNoUpdate(i);
                if (!stack.isEmpty()) spawnAtLocation(stack);
            }
            if (bossEvent != null) bossEvent.removeAllPlayers();
            StoryDirector.onHollowDefeated(this);
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ---------- Interaction ----------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isOwner(player) || isHollow()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || held.isEmpty()) {
            openInventory(player);
            return InteractionResult.CONSUME;
        }
        // Give Solace whatever you're holding.
        ItemStack copy = held.copy();
        ItemStack leftover = inventory.addItem(copy);
        int given = copy.getCount() - leftover.getCount();
        if (given <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("Solace's pockets are full."), true);
            return InteractionResult.CONSUME;
        }
        player.setItemInHand(hand, leftover);
        Sounds.playAt(this, "entity.item.pickup", 0.6F, 1.2F);
        ItemStack gift = copy.copy();
        gift.setCount(given);
        SolaceBrain.onGift((ServerPlayer) player, this, gift);
        return InteractionResult.CONSUME;
    }

    public void openInventory(Player player) {
        player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> ChestMenu.threeRows(id, playerInv, inventory),
                Component.literal("Solace's pockets")));
    }

    // ---------- Items ----------

    /** Things the owner throws to Solace (Q key, near it) go into its pockets, as a gift. Journal pages are left alone. */
    private void pickUpThrownItems() {
        ServerPlayer owner = getServerOwner();
        if (owner == null || owner.level() != level()) return;
        for (net.minecraft.world.entity.item.ItemEntity item : level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                getBoundingBox().inflate(2.5, 1.0, 2.5), e -> e.isAlive() && e.tickCount > 10)) {
            if (!(item.getOwner() instanceof Player thrower) || !thrower.getUUID().equals(owner.getUUID())) continue;
            ItemStack stack = item.getItem();
            if (com.solacemod.solace.story.Fragments.indexOf(stack) >= 0) continue;
            ItemStack copy = stack.copy();
            ItemStack leftover = inventory.addItem(copy);
            int taken = stack.getCount() - leftover.getCount();
            if (taken <= 0) continue;
            take(item, taken);
            ItemStack gift = stack.copyWithCount(taken);
            if (leftover.isEmpty()) item.discard();
            else item.setItem(leftover);
            Sounds.playAt(this, "entity.item.pickup", 0.6F, 1.2F);
            SolaceBrain.onGift(owner, this, gift);
        }
    }

    /** Puts an item in Solace's pockets; whatever doesn't fit is dropped on the ground. */
    public void storeOrDrop(ItemStack stack) {
        ItemStack leftover = inventory.addItem(stack);
        if (!leftover.isEmpty()) spawnAtLocation(leftover);
    }

    /**
     * Breaks a block and keeps the drops. With the right tool in hand it uses (and wears) that tool. Without one,
     * the block crumbles to ash under its power, and drops what a proper tool would have given.
     */
    public void harvest(BlockPos pos) {
        ServerLevel level = (ServerLevel) level();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        ItemStack tool = getMainHandItem();
        boolean usingTool = tool.isCorrectToolForDrops(state) || (!state.requiresCorrectToolForDrops() && tool.getDestroySpeed(state) > 1.0F);
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, this, usingTool ? tool : powerTool(state));
        level.destroyBlock(pos, false, this);
        drops.forEach(this::storeOrDrop);
        if (usingTool && !tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
        } else if (!usingTool) {
            level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** The tool Solace's power acts like for this block (only used to decide what drops). */
    private static ItemStack powerTool(BlockState state) {
        for (net.minecraft.world.item.Item i : new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.DIAMOND_PICKAXE,
                net.minecraft.world.item.Items.DIAMOND_AXE, net.minecraft.world.item.Items.DIAMOND_SHOVEL, net.minecraft.world.item.Items.DIAMOND_HOE,
                net.minecraft.world.item.Items.SHEARS}) {
            ItemStack s = new ItemStack(i);
            if (s.isCorrectToolForDrops(state)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Holds the best tool from the pockets for this block. Returns false if no tool can harvest it. */
    public boolean equipBestToolFor(BlockState state) {
        ItemStack hand = getMainHandItem();
        int bestSlot = -1;
        float bestSpeed = hand.isCorrectToolForDrops(state) ? hand.getDestroySpeed(state) : -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty() || !s.isCorrectToolForDrops(state)) continue;
            float speed = s.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) swapIntoHand(bestSlot);
        return !state.requiresCorrectToolForDrops() || getMainHandItem().isCorrectToolForDrops(state);
    }

    /** Holds the strongest weapon from the pockets. */
    public void equipBestWeapon() {
        int bestSlot = -1;
        float best = weaponScore(getMainHandItem());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            float score = weaponScore(inventory.getItem(i));
            if (score > best) {
                best = score;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) swapIntoHand(bestSlot);
    }

    private static float weaponScore(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        float bonus = stack.getItem() instanceof TieredItem tiered ? tiered.getTier().getAttackDamageBonus() : 0;
        if (stack.getItem() instanceof SwordItem) return 10 + bonus;
        if (stack.getItem() instanceof AxeItem) return 5 + bonus;
        return 0;
    }

    private void swapIntoHand(int slot) {
        ItemStack fromPocket = inventory.getItem(slot);
        ItemStack inHand = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, fromPocket);
        inventory.setItem(slot, inHand);
    }

    // ---------- Boss bar visibility ----------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (bossEvent != null) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (bossEvent != null) bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bossEvent != null) bossEvent.removeAllPlayers();
        if (task != null) task.stop(this);
        super.remove(reason);
    }

    // ---------- Saving ----------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.put("Pockets", inventory.createTag(registryAccess()));
        tag.putString("Mode", mode.name());
        tag.putBoolean("Guard", guard);
        tag.putInt("Form", getForm() == FORM_HOLLOW ? FORM_UNEASY : getForm() == FORM_LANTERN || getForm() == FORM_VAST ? formBeforeLantern : getForm());
        if (stayPos != null) tag.putLong("StayPos", stayPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) setOwner(tag.getUUID("Owner"));
        inventory.fromTag(tag.getList("Pockets", Tag.TAG_COMPOUND), registryAccess());
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.FOLLOW;
        }
        guard = tag.getBoolean("Guard");
        // The boss form is never saved, so make sure the boss stats aren't either.
        int form = tag.getInt("Form");
        setForm(form == FORM_HOLLOW || form == FORM_LANTERN || form == FORM_VAST ? FORM_UNEASY : form);
        resetAttributes();
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        if (tag.contains("StayPos")) stayPos = BlockPos.of(tag.getLong("StayPos"));
    }
}
