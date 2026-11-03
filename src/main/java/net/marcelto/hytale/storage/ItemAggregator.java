package net.marcelto.hytale.storage;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;

/** Lists individual stacks or merges containers into a sorted list of item kinds. */
public final class ItemAggregator {

  private static final Comparator<StoredItem> ORDER =
      Comparator.comparing((StoredItem item) -> item.name().toLowerCase(Locale.ROOT))
          .thenComparing(item -> item.prototype().getItemId())
          .thenComparing(item -> item.prototype().getDurability(), Comparator.reverseOrder())
          .thenComparingInt(item -> item.prototype().hashCode())
          .thenComparing(StoredItem::total, Comparator.reverseOrder());

  private ItemAggregator() {}

  /** Lists every slot separately, including empty slots, in container order. */
  @Nonnull
  public static List<StoredItem> stacks(
      @Nonnull ItemContainer container, @Nonnull ItemNames names) {
    final List<StoredItem> items = new ArrayList<>();
    final short capacity = container.getCapacity();
    for (short slot = 0; slot < capacity; slot++) {
      final ItemStack stack = container.getItemStack(slot);
      if (ItemStack.isEmpty(stack)) {
        items.add(new StoredItem(ItemStack.EMPTY, "", 0));
      } else {
        items.add(new StoredItem(stack.withQuantity(1), names.of(stack), stack.getQuantity()));
      }
    }
    return items;
  }

  /** Sums every stack in the containers per item kind, sorted by name. */
  @Nonnull
  public static List<StoredItem> aggregate(
      @Nonnull Collection<? extends ItemContainer> containers, @Nonnull ItemNames names) {
    final Map<ItemStack, Long> totals = new HashMap<>();
    for (final ItemContainer container : containers) {
      container.forEach(
          (slot, stack) -> {
            final ItemStack prototype = stack.withQuantity(1);
            if (prototype != null) totals.merge(prototype, (long) stack.getQuantity(), Long::sum);
          });
    }

    final List<StoredItem> items = new ArrayList<>(totals.size());
    totals.forEach(
        (prototype, total) -> items.add(new StoredItem(prototype, names.of(prototype), total)));
    items.sort(ORDER);
    return items;
  }

  /** Keeps the items whose name or id contains every whitespace-separated word of the query. */
  @Nonnull
  public static List<StoredItem> filter(@Nonnull List<StoredItem> items, @Nonnull String query) {
    if (query.isEmpty()) return items;

    final String[] words = query.split("\\s+");
    final List<StoredItem> matches = new ArrayList<>();
    for (final StoredItem item : items) {
      final String haystack =
          (item.name() + ' ' + item.prototype().getItemId().replace('_', ' '))
              .toLowerCase(Locale.ROOT);
      if (containsAll(haystack, words)) matches.add(item);
    }
    return matches;
  }

  private static boolean containsAll(@Nonnull String haystack, @Nonnull String[] words) {
    for (final String word : words) {
      if (!haystack.contains(word)) return false;
    }
    return true;
  }
}
