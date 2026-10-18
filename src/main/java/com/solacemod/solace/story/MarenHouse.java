package com.solacemod.solace.story;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Maren's house (chapter 5): the house she drew when she was nine, the one Solace built for her.
 * A small ruined cottage the mod builds into your world, far enough away that you have to go and find it.
 * It's always 7x7 with the door facing north. Inside: her bed, a chest with page 4, and signs.
 */
public final class MarenHouse {
    public static final int SIZE = 7;

    private MarenHouse() {
    }

    /** Looks for a flat, dry, empty spot 40-70 blocks from the player. Returns the house's corner, or null. */
    @Nullable
    public static BlockPos findSpot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return null;
        RandomSource r = player.getRandom();
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = 40 + r.nextDouble() * 30;
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * dist, 0, player.getZ() + Math.sin(angle) * dist);
            if (!level.hasChunkAt(column) || !level.hasChunkAt(column.offset(SIZE, 0, SIZE))) continue;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            boolean ok = true;
            for (int x = -1; x <= SIZE && ok; x++) {
                for (int z = -1; z <= SIZE && ok; z++) {
                    BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.offset(x, 0, z));
                    BlockState ground = level.getBlockState(top.below());
                    // Only natural ground: never on water, never on anything someone might have built.
                    if (!ground.getFluidState().isEmpty() || !(ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.PODZOL)
                            || ground.is(Blocks.COARSE_DIRT) || ground.is(Blocks.SAND) || ground.is(Blocks.STONE) || ground.is(Blocks.SNOW_BLOCK)
                            || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.GRAVEL))) {
                        ok = false;
                    }
                    min = Math.min(min, top.getY());
                    max = Math.max(max, top.getY());
                }
            }
            if (!ok || max - min > 3) continue;
            return new BlockPos(column.getX(), max, column.getZ());
        }
        return null;
    }

    /** Builds the house with its corner at {@code o}. Returns the inside center (where the player has to stand). */
    public static BlockPos build(ServerLevel level, BlockPos o, RandomSource r) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        // Foundation, down to the ground, and clear the space above.
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = -1; y > -6; y--) {
                    BlockPos p = o.offset(x, y, z);
                    if (y < -1 && !level.getBlockState(p).canBeReplaced()) break;
                    set(level, p, r.nextFloat() < 0.3F ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                }
                for (int y = 0; y < 9; y++) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
                if (x > 0 && x < SIZE - 1 && z > 0 && z < SIZE - 1) set(level, o.offset(x, -1, z), planks);
            }
        }
        // Walls (4 high). Corners are logs. Some planks are missing: it's been a long time.
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    boolean edgeX = x == 0 || x == SIZE - 1, edgeZ = z == 0 || z == SIZE - 1;
                    if (!edgeX && !edgeZ) continue;
                    BlockPos p = o.offset(x, y, z);
                    if (edgeX && edgeZ) set(level, p, log);
                    else if (y > 0 && r.nextFloat() < 0.08F) set(level, p, Blocks.COBWEB.defaultBlockState());
                    else if (y > 0 && r.nextFloat() < 0.07F) continue; // a hole
                    else set(level, p, planks);
                }
            }
        }
        // Like a child's drawing: a door in the middle, a window either side, one on each side wall.
        for (int y = 1; y <= 2; y++) {
            set(level, o.offset(1, y, 0), Blocks.GLASS_PANE.defaultBlockState());
            set(level, o.offset(5, y, 0), Blocks.GLASS_PANE.defaultBlockState());
            set(level, o.offset(0, y, 3), Blocks.GLASS_PANE.defaultBlockState());
            set(level, o.offset(SIZE - 1, y, 3), Blocks.GLASS_PANE.defaultBlockState());
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        set(level, o.offset(3, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, o.offset(3, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // A pointed roof: eaves, three rows of stairs each side, a ridge on top, and the gable ends filled. A few gaps.
        BlockState up = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        BlockState down = Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int x = -1; x <= SIZE; x++) {
            set(level, o.offset(x, 3, -1), up);
            set(level, o.offset(x, 3, SIZE), down);
            for (int k = 0; k < 3; k++) {
                if (r.nextFloat() < 0.06F) continue;
                set(level, o.offset(x, 4 + k, k), up);
                set(level, o.offset(x, 4 + k, SIZE - 1 - k), down);
            }
            set(level, o.offset(x, 7, 3), Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        for (int k = 0; k < 4; k++) {
            for (int z = k; z <= SIZE - 1 - k; z++) {
                set(level, o.offset(0, 4 + k, z), planks);
                set(level, o.offset(SIZE - 1, 4 + k, z), planks);
            }
        }
        // The chimney every kid draws.
        for (int y = 5; y < 9; y++) set(level, o.offset(5, y, 5), Blocks.BRICKS.defaultBlockState());

        // Inside: her bed, a chest, a table, cobwebs in the corners.
        bed(level, o.offset(1, 0, 4), Direction.SOUTH);
        set(level, o.offset(5, 0, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, o.offset(1, 3, 1), Blocks.COBWEB.defaultBlockState());
        set(level, o.offset(5, 3, 4), Blocks.COBWEB.defaultBlockState());
        set(level, o.offset(5, 0, 3), Blocks.FLOWER_POT.defaultBlockState());
        BlockPos chestPos = o.offset(4, 0, 5);
        set(level, chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(13, Fragments.create(3)); // page 4
            chest.setItem(4, drawing());
            chest.setItem(22, named(new ItemStack(Items.POPPY), "for maren"));
            chest.setChanged();
        }
        wallSign(level, o.offset(3, 1, SIZE - 2), Direction.NORTH, "my house", "(age 9)");
        wallSign(level, o.offset(2, 1, 1), Direction.SOUTH, "it built this", "while i slept");
        BlockPos front = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.offset(1, 0, -2));
        if (Math.abs(front.getY() - o.getY()) <= 2) sign(level, front, 8, "welcome", "home");
        // Poppies out front.
        for (int x = 0; x < SIZE; x += 2) {
            BlockPos p = o.offset(x, 0, -2);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            if (Math.abs(ground.getY() - o.getY()) <= 2 && level.getBlockState(ground.below()).is(Blocks.GRASS_BLOCK)) {
                set(level, ground, Blocks.POPPY.defaultBlockState());
            }
        }
        return o.offset(3, 0, 3);
    }

    /** Removes the door and walls it up (the COLLECTED ending). */
    public static void sealDoor(ServerLevel level, BlockPos corner) {
        set(level, corner.offset(3, 1, 0), Blocks.AIR.defaultBlockState());
        set(level, corner.offset(3, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
        set(level, corner.offset(3, 1, 0), Blocks.OAK_PLANKS.defaultBlockState());
    }

    private static ItemStack drawing() {
        ItemStack paper = named(new ItemStack(Items.PAPER), "a drawing");
        paper.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("A house with a pointed roof, a chimney,").withStyle(ChatFormatting.GRAY),
                Component.literal("and a stick figure in the window.").withStyle(ChatFormatting.GRAY),
                Component.literal("\"me and my best friend\" - Maren, age 9").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))));
        return paper;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.ITALIC));
        return stack;
    }

    private static void set(ServerLevel level, BlockPos p, BlockState state) {
        level.setBlock(p, state, Block.UPDATE_ALL);
    }

    private static void bed(ServerLevel level, BlockPos foot, Direction facing) {
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
        set(level, foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
        set(level, foot.relative(facing), bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static void wallSign(ServerLevel level, BlockPos p, Direction facing, String... lines) {
        set(level, p.relative(facing.getOpposite()), Blocks.OAK_PLANKS.defaultBlockState()); // something solid to hang on
        set(level, p, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
        writeSign(level, p, lines);
    }

    private static void sign(ServerLevel level, BlockPos p, int rotation, String... lines) {
        if (!level.getBlockState(p).canBeReplaced()) return;
        set(level, p, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation));
        writeSign(level, p, lines);
    }

    private static void writeSign(ServerLevel level, BlockPos p, String... lines) {
        BlockEntity be = level.getBlockEntity(p);
        if (!(be instanceof SignBlockEntity sign)) return;
        SignText text = new SignText();
        for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]).withStyle(ChatFormatting.DARK_GRAY));
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(p, level.getBlockState(p), level.getBlockState(p), Block.UPDATE_ALL);
    }
}
