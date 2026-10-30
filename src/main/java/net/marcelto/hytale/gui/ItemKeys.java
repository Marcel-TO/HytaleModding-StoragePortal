package net.marcelto.hytale.gui;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hands out small ids for item prototypes, so a client can name an item in an event.
 *
 * <p>An id stays valid for the lifetime of the page, even when the card showing it moves.
 */
public final class ItemKeys {

  private final Map<ItemStack, Integer> ids = new HashMap<>();
  private final Map<Integer, ItemStack> prototypes = new HashMap<>();

  public int idOf(@Nonnull ItemStack prototype) {
    return ids.computeIfAbsent(
        prototype,
        key -> {
          final int id = prototypes.size();
          prototypes.put(id, key);
          return id;
        });
  }

  /** Returns the prototype that was handed out under the id, or null for an unknown id. */
  @Nullable
  public ItemStack prototypeOf(int id) {
    return prototypes.get(id);
  }
}
