package net.marcelto.hytale.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import net.marcelto.hytale.gui.StoragePortalPage;

/**
 * Dismisses an open Storage Portal window when its player leaves the world.
 *
 * <p>The page manager only dismisses a page that is replaced or reset, so a player who disconnects
 * with the window open would otherwise leave the portal lid open for good.
 */
public class StoragePortalCleanupSystem extends RefSystem<EntityStore> {

  @Override
  public void onEntityAdded(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull AddReason reason,
      @Nonnull Store<EntityStore> store,
      @Nonnull CommandBuffer<EntityStore> commandBuffer) {}

  @Override
  public void onEntityRemove(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull RemoveReason reason,
      @Nonnull Store<EntityStore> store,
      @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    final Player player = commandBuffer.getComponent(ref, Player.getComponentType());
    if (player == null) return;

    if (player.getPageManager().getCustomPage() instanceof StoragePortalPage page) {
      page.onDismiss(ref, store);
    }
  }

  @Override
  public Query<EntityStore> getQuery() {
    return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
  }
}
