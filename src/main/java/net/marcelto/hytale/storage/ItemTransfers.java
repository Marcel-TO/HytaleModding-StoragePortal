package net.marcelto.hytale.storage;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/** Moves items between the player's inventory and the containers around a portal. */
public final class ItemTransfers {

  private ItemTransfers() {}

  /** Counts the items stackable with {@code prototype} across the containers. */
  public static long count(@Nonnull List<ItemContainer> containers, @Nonnull ItemStack prototype) {
    final long[] total = {0};
    for (final ItemContainer container : containers) {
      container.forEach(
          (slot, stack) -> {
            if (stack.isStackableWith(prototype)) total[0] += stack.getQuantity();
          });
    }
    return total[0];
  }

  /**
   * Moves up to {@code wanted} items from the nearest containers into the target.
   *
   * @return how many items actually moved.
   */
  public static int take(
      @Nonnull List<ItemContainer> sources,
      @Nonnull ItemStack prototype,
      int wanted,
      @Nonnull ItemContainer target) {
    int moved = 0;
    for (final ItemContainer source : sources) {
      if (moved >= wanted) break;
      moved += moveMatching(source, prototype, wanted - moved, target);
    }
    return moved;
  }

  /**
   * Moves up to {@code wanted} items from the source into the containers, preferring the ones that
   * already hold the same item.
   *
   * @return how many items actually moved.
   */
  public static int deposit(
      @Nonnull ItemContainer source,
      @Nonnull ItemStack prototype,
      int wanted,
      @Nonnull List<ItemContainer> targets) {
    int moved = 0;
    for (final ItemContainer target : holdersFirst(targets, prototype)) {
      if (moved >= wanted) break;
      moved += moveMatching(source, prototype, wanted - moved, target);
    }
    return moved;
  }

  /**
   * Moves up to {@code wanted} items from one slot of the source into the containers, preferring
   * the ones that already hold the same item. Nothing moves if the slot no longer holds an item
   * stackable with {@code prototype}.
   *
   * @return how many items actually moved.
   */
  public static int depositSlot(
      @Nonnull ItemContainer source,
      short slot,
      @Nonnull ItemStack prototype,
      int wanted,
      @Nonnull List<ItemContainer> targets) {
    if (slot < 0 || slot >= source.getCapacity()) return 0;

    int moved = 0;
    for (final ItemContainer target : holdersFirst(targets, prototype)) {
      if (moved >= wanted) break;

      final ItemStack stack = source.getItemStack(slot);
      if (ItemStack.isEmpty(stack) || !stack.isStackableWith(prototype)) break;

      final int before = stack.getQuantity();
      source.moveItemStackFromSlot(slot, Math.min(before, wanted - moved), target, false, true);
      moved += Math.max(0, before - quantityAt(source, slot));
    }
    return moved;
  }

  /**
   * Moves only items with a stackable match in the containers, filling the ones that already hold
   * the same item before trying the remaining containers in their supplied nearest-first order.
   *
   * @return how many items actually moved.
   */
  public static int quickStack(
      @Nonnull ItemContainer source, @Nonnull List<ItemContainer> targets) {
    if (targets.isEmpty()) return 0;

    int moved = 0;
    final short capacity = source.getCapacity();
    for (short slot = 0; slot < capacity; slot++) {
      final ItemStack stack = source.getItemStack(slot);
      if (ItemStack.isEmpty(stack)) continue;
      if (targets.stream().noneMatch(target -> target.containsItemStacksStackableWith(stack))) {
        continue;
      }

      moved += depositSlot(source, slot, stack, stack.getQuantity(), targets);
    }
    return moved;
  }

  private static int moveMatching(
      @Nonnull ItemContainer from,
      @Nonnull ItemStack prototype,
      int limit,
      @Nonnull ItemContainer to) {
    int moved = 0;
    final short capacity = from.getCapacity();
    for (short slot = 0; slot < capacity && moved < limit; slot++) {
      final ItemStack stack = from.getItemStack(slot);
      if (ItemStack.isEmpty(stack) || !stack.isStackableWith(prototype)) continue;

      final int before = stack.getQuantity();
      from.moveItemStackFromSlot(slot, Math.min(before, limit - moved), to, false, true);

      final int delta = before - quantityAt(from, slot);
      if (delta <= 0) break;
      moved += delta;
    }
    return moved;
  }

  private static int quantityAt(@Nonnull ItemContainer container, short slot) {
    final ItemStack stack = container.getItemStack(slot);
    return ItemStack.isEmpty(stack) ? 0 : stack.getQuantity();
  }

  @Nonnull
  private static List<ItemContainer> holdersFirst(
      @Nonnull List<ItemContainer> targets, @Nonnull ItemStack prototype) {
    final List<ItemContainer> ordered = new ArrayList<>(targets.size());
    final List<ItemContainer> others = new ArrayList<>();
    for (final ItemContainer target : targets) {
      if (target.containsItemStacksStackableWith(prototype)) {
        ordered.add(target);
      } else {
        others.add(target);
      }
    }
    ordered.addAll(others);
    return ordered;
  }
}
