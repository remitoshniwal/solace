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

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(SolaceEntity.class, EntityDataSerializers.INT);

    public enum Mode { FOLLOW, STAY, WANDER }

    public final SimpleContainer inventory = new SimpleContainer(27);
    private Mode mode = Mode.FOLLOW;
    private boolean guard;
    private BlockPos stayPos;
    @Nullable
    private SolaceTask task;
    private int stareTicks;

    // Boss ("Hollow") state
    @Nullable
    private ServerBossEvent bossEvent;
    private int abilityCooldown = 100;
    private boolean phaseTwo;
    private int ownerMissingTicks;

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
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SolaceGoals.StareGoal(this));
        this.goalSelector.addGoal(2, new SolaceGoals.TaskGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(4, new SolaceGoals.FollowOwnerGoal(this));
        this.goalSelector.addGoal(5, new SolaceGoals.StayGoal(this));
        this.goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(7, new SolaceGoals.WanderGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new SolaceGoals.DefendOwnerGoal(this));
        this.targetSelector.addGoal(2, new SolaceGoals.AssistOwnerGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> guard && !isHollow() && !(e instanceof Creeper) && SolaceConfig.ALLOW_COMBAT.get()));
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
        if (ticks > 0) getNavigation().stop();
    }

    public void tickStare() {
        if (stareTicks > 0) stareTicks--;
    }

    // ---------- Form ----------

    public int getForm() {
        return this.entityData.get(DATA_FORM);
    }

    public void setForm(int form) {
        if (getForm() != form) this.entityData.set(DATA_FORM, form);
    }

    public boolean isHollow() {
        return getForm() == FORM_HOLLOW;
    }

    /** Turns Solace into the boss for the "Free Maren" ending. */
    public void becomeHollow() {
        setTask(null);
        setGuard(false);
        this.mode = Mode.FOLLOW;
        setForm(FORM_HOLLOW);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0);
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
        super.aiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
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
        } else if (tickCount % 40 == 0) {
            equipBestWeapon();
        }
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
            SolaceBrain.sayScripted(owner, this, "Why won't you just STAY?", "glitch");
            spawnVisualLightning(level, owner.blockPosition());
        }
        if (--abilityCooldown > 0) return;
        abilityCooldown = phaseTwo ? 70 : 120;

        switch (random.nextInt(phaseTwo ? 5 : 4)) {
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
            case 2 -> summonEchoes(level, owner);
            case 3 -> { // shockwave
                level.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
                Sounds.playAt(this, "entity.warden.sonic_boom", 1.0F, 1.4F);
                if (owner.distanceTo(this) < 6) {
                    owner.hurt(damageSources().mobAttack(this), 5.0F);
                    owner.knockback(1.5, getX() - owner.getX(), getZ() - owner.getZ());
                }
            }
            default -> spawnVisualLightning(level, owner.blockPosition().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
        }
    }

    private void summonEchoes(ServerLevel level, ServerPlayer owner) {
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
            vex.setTarget(owner);
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source)) return super.hurt(source, amount);
        if (!isHollow() && amount >= getHealth() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // Solace can't really die. It vanishes... and comes back later.
            SolaceManager.fakeDeath(this);
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result && !isHollow() && source.getEntity() instanceof ServerPlayer player && isOwner(player)) {
            SolaceBrain.onOwnerHurtSolace(player, this);
        }
        return result;
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
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isHollow();
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
            SolaceBrain.sayScripted((ServerPlayer) player, this, "My pockets are full.", "calm");
            return InteractionResult.CONSUME;
        }
        player.setItemInHand(hand, leftover);
        Sounds.playAt(this, "entity.item.pickup", 0.6F, 1.2F);
        SolaceBrain.onGift((ServerPlayer) player, this, copy.getHoverName().getString(), given);
        return InteractionResult.CONSUME;
    }

    public void openInventory(Player player) {
        player.openMenu(new SimpleMenuProvider((id, playerInv, p) -> ChestMenu.threeRows(id, playerInv, inventory),
                Component.literal("Solace's pockets")));
    }

    // ---------- Items ----------

    /** Puts an item in Solace's pockets; whatever doesn't fit is dropped on the ground. */
    public void storeOrDrop(ItemStack stack) {
        ItemStack leftover = inventory.addItem(stack);
        if (!leftover.isEmpty()) spawnAtLocation(leftover);
    }

    /** Breaks a block like a player would, keeping the drops. */
    public void harvest(BlockPos pos) {
        ServerLevel level = (ServerLevel) level();
        BlockState state = level.getBlockState(pos);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, this, getMainHandItem());
        level.destroyBlock(pos, false, this);
        drops.forEach(this::storeOrDrop);
        ItemStack tool = getMainHandItem();
        if (!tool.isEmpty() && tool.isDamageableItem()) tool.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
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
        tag.putInt("Form", getForm() == FORM_HOLLOW ? FORM_UNEASY : getForm());
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
        setForm(tag.getInt("Form") == FORM_HOLLOW ? FORM_UNEASY : tag.getInt("Form"));
        resetAttributes();
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        if (tag.contains("StayPos")) stayPos = BlockPos.of(tag.getLong("StayPos"));
    }
}
