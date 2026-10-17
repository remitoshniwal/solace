package com.solacemod.solace.story;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Maren's torn journal pages, as readable written books. */
public final class Fragments {
    public static final String TAG = "solace_fragment";
    public static final int RELEASE_LETTER = 100;
    public static final int TOGETHER_LETTER = 101;

    private Fragments() {
    }

    public static ItemStack create(int index) {
        String text;
        String title;
        if (index == RELEASE_LETTER) {
            text = StoryText.RELEASE_LETTER;
            title = "A Letter";
        } else if (index == TOGETHER_LETTER) {
            text = StoryText.TOGETHER_LETTER;
            title = "A Letter";
        } else {
            text = StoryText.FRAGMENTS.get(Math.max(0, Math.min(StoryText.FRAGMENT_COUNT - 1, index)));
            title = "Torn Page (" + (index + 1) + "/" + StoryText.FRAGMENT_COUNT + ")";
        }
        List<Filterable<Component>> pages = new ArrayList<>();
        for (String page : paginate(text)) pages.add(Filterable.passThrough(Component.literal(page)));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "Maren", 3, pages, true));
        book.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("The handwriting shakes.").withStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.DARK_GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG, index);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return book;
    }

    /** Which fragment this item is, or -1 if it's not one. */
    public static int indexOf(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK)) return -1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        CompoundTag tag = data.copyTag();
        return tag.contains(TAG) ? tag.getInt(TAG) : -1;
    }

    /** Leaves a glowing page on the ground. */
    public static void dropAt(ServerLevel level, Vec3 pos, int index) {
        ItemEntity item = new ItemEntity(level, pos.x, pos.y + 0.2, pos.z, create(index));
        item.setDeltaMovement(Vec3.ZERO);
        item.setPickUpDelay(10);
        item.setUnlimitedLifetime();
        item.setGlowingTag(true);
        level.addFreshEntity(item);
    }

    /** Slips a page straight into the player's inventory (or at their feet if it's full). */
    public static void giveTo(ServerPlayer player, int index) {
        ItemStack book = create(index);
        if (!player.getInventory().add(book)) dropAt(player.serverLevel(), player.position(), index);
    }

    /** Splits text into book pages of about 220 characters, breaking between words. */
    static List<String> paginate(String text) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String word : text.split(" ")) {
            if (page.length() + word.length() + 1 > 220 && !page.isEmpty()) {
                pages.add(page.toString().trim());
                page.setLength(0);
            }
            page.append(word).append(' ');
        }
        if (!page.toString().isBlank()) pages.add(page.toString().trim());
        return pages;
    }
}
