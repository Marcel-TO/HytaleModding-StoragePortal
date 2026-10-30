package net.marcelto.hytale.gui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import net.marcelto.hytale.storage.StoredItem;

/**
 * Mirrors a list of items into a grid of cards.
 *
 * <p>The view remembers what the client shows, so an update only sends the cards that changed. When
 * the list keeps the same items in the same order, only the quantities are patched in place, which
 * keeps the scroll position of the grid.
 */
public final class ItemGridView {

  private static final int COLUMNS = 10;
  private static final String CARD = "Pages/StoragePortalCard.ui";
  private static final String SPACER = "Pages/StoragePortalSpacer.ui";

  private final String grid;
  private final String primaryAction;
  private final String secondaryAction;
  private final ItemKeys keys;

  private final List<ItemStack> shownItems = new ArrayList<>();
  private final List<Long> shownTotals = new ArrayList<>();

  /**
   * Creates a view for one grid.
   *
   * @param grid the selector of the grid group, for example {@code #StorageGrid}.
   * @param primaryAction the action sent when a card is clicked.
   * @param secondaryAction the action sent when a card is right-clicked.
   * @param keys the registry that names the items in the events.
   */
  public ItemGridView(
      @Nonnull String grid,
      @Nonnull String primaryAction,
      @Nonnull String secondaryAction,
      @Nonnull ItemKeys keys) {
    this.grid = grid;
    this.primaryAction = primaryAction;
    this.secondaryAction = secondaryAction;
    this.keys = keys;
  }

  /** Forgets what the client shows, for example because the page document was sent again. */
  public void reset() {
    shownItems.clear();
    shownTotals.clear();
  }

  /**
   * Adds the commands that bring the grid in line with the items.
   *
   * @return true if any command was added, so the update has to be sent.
   */
  public boolean update(
      @Nonnull UICommandBuilder commands,
      @Nonnull UIEventBuilder events,
      @Nonnull List<StoredItem> items) {
    if (!sameItems(items)) {
      rebuild(commands, events, items);
      return true;
    }

    boolean changed = false;
    for (int i = 0; i < items.size(); i++) {
      final StoredItem item = items.get(i);
      if (shownTotals.get(i) == item.total()) continue;

      shownTotals.set(i, item.total());
      final String card = card(i);
      commands.set(card + " #Qty.Text", formatQuantity(item.total()));
      commands.set(card + " #Button.TooltipText", tooltip(item));
      changed = true;
    }
    return changed;
  }

  private void rebuild(
      @Nonnull UICommandBuilder commands,
      @Nonnull UIEventBuilder events,
      @Nonnull List<StoredItem> items) {
    commands.clear(grid);
    reset();

    for (int i = 0; i < items.size(); i++) {
      final StoredItem item = items.get(i);
      final String card = card(i);
      final String key = String.valueOf(keys.idOf(item.prototype()));

      commands.append(grid, CARD);
      commands.set(card + " #Slot.ItemId", safeItemId(item.prototype().getItemId()));
      commands.set(card + " #Qty.Text", formatQuantity(item.total()));
      commands.set(card + " #Button.TooltipText", tooltip(item));
      events.addEventBinding(
          CustomUIEventBindingType.Activating,
          card + " #Button",
          EventData.of(StoragePortalPage.PageEvent.ACTION, primaryAction)
              .append(StoragePortalPage.PageEvent.KEY, key),
          false);
      events.addEventBinding(
          CustomUIEventBindingType.RightClicking,
          card + " #Button",
          EventData.of(StoragePortalPage.PageEvent.ACTION, secondaryAction)
              .append(StoragePortalPage.PageEvent.KEY, key),
          false);

      shownItems.add(item.prototype());
      shownTotals.add(item.total());
    }

    final int remainder = items.size() % COLUMNS;
    if (remainder == 0) return;

    // Without spacers, a wrapping layout centers the cards of the last row.
    for (int i = remainder; i < COLUMNS; i++) {
      commands.append(grid, SPACER);
    }
  }

  private boolean sameItems(@Nonnull List<StoredItem> items) {
    if (items.size() != shownItems.size()) return false;

    for (int i = 0; i < items.size(); i++) {
      if (!items.get(i).prototype().equals(shownItems.get(i))) return false;
    }
    return true;
  }

  @Nonnull
  private String card(int index) {
    return grid + "[" + index + "]";
  }

  @Nonnull
  private static String tooltip(@Nonnull StoredItem item) {
    return item.name() + " x" + item.total();
  }

  @Nonnull
  private static String safeItemId(@Nonnull String itemId) {
    return ItemModule.exists(itemId) ? itemId : BlockType.UNKNOWN_KEY;
  }

  /** Shortens large quantities so they fit on a card. A single item shows no quantity. */
  @Nonnull
  static String formatQuantity(long total) {
    if (total <= 1) return "";
    if (total < 10_000) return Long.toString(total);
    if (total < 1_000_000) return (total / 1_000) + "k";
    return String.format(Locale.ROOT, "%.1fM", total / 1_000_000.0);
  }
}
