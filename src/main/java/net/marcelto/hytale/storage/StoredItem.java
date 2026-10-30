package net.marcelto.hytale.storage;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import javax.annotation.Nonnull;

/**
 * One kind of item across all scanned containers.
 *
 * @param prototype a single-quantity stack; stacks that are stackable with each other share one.
 * @param name the display name in the viewer's language.
 * @param total how many of the item exist across all scanned containers.
 */
public record StoredItem(@Nonnull ItemStack prototype, @Nonnull String name, long total) {}
