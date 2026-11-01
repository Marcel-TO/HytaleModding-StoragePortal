package net.marcelto.hytale.storage;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Tracks who has a portal open, so its lid opens for the first viewer and closes after the last.
 */
public final class PortalViewers {

  /** Identifies one placed portal block. */
  public record PortalKey(@Nonnull String world, int x, int y, int z) {}

  private static final ConcurrentHashMap<PortalKey, Set<UUID>> VIEWERS = new ConcurrentHashMap<>();

  private PortalViewers() {}

  /** Returns whether a loaded portal should keep its lid open. */
  public static boolean hasViewers(@Nonnull PortalKey key) {
    return VIEWERS.containsKey(key);
  }

  /**
   * Registers a viewer.
   *
   * @return true if the viewer is the first one, so the lid should open.
   */
  public static boolean add(@Nonnull PortalKey key, @Nonnull UUID viewer) {
    final boolean[] first = {false};
    VIEWERS.compute(
        key,
        (k, viewers) -> {
          final Set<UUID> set = viewers != null ? viewers : new HashSet<>();
          first[0] = set.isEmpty();
          set.add(viewer);
          return set;
        });
    return first[0];
  }

  /**
   * Unregisters a viewer.
   *
   * @return true if the viewer was the last one, so the lid should close.
   */
  public static boolean remove(@Nonnull PortalKey key, @Nonnull UUID viewer) {
    final boolean[] last = {false};
    VIEWERS.computeIfPresent(
        key,
        (k, viewers) -> {
          last[0] = viewers.remove(viewer) && viewers.isEmpty();
          return viewers.isEmpty() ? null : viewers;
        });
    return last[0];
  }

  /** Forgets every viewer, for example on plugin shutdown. */
  public static void clear() {
    VIEWERS.clear();
  }
}
