package net.marcelto.hytale.storage;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import javax.annotation.Nonnull;

/**
 * An item in a portal grid, representing an individual stack or an aggregated item kind.
 *
 * @param prototype a single-quantity stack, or {@link ItemStack#EMPTY} for an empty inventory slot.
 * @param name the display name in the viewer's language.
 * @param total the quantity of the stack or the sum across all scanned containers.
 */
public record StoredItem(@Nonnull ItemStack prototype, @Nonnull String name, long total) {}
