package net.marcelto.hytale.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import net.marcelto.hytale.gui.StoragePortalPage;

/** Keeps open Storage Portal windows in step with the containers around the portal. */
public class StoragePortalSystem extends DelayedEntitySystem<EntityStore> {

  private static final float INTERVAL_SECONDS = 0.5f;

  public StoragePortalSystem() {
    super(INTERVAL_SECONDS);
  }

  @Override
  public void tick(
      float dt,
      int index,
      @Nonnull ArchetypeChunk<EntityStore> chunk,
      @Nonnull Store<EntityStore> store,
      @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    final Player player = chunk.getComponent(index, Player.getComponentType());
    if (player == null) return;

    if (player.getPageManager().getCustomPage() instanceof StoragePortalPage page) {
      page.tick(chunk.getReferenceTo(index), commandBuffer);
    }
  }

  @Override
  public Query<EntityStore> getQuery() {
    return Query.and(Player.getComponentType(), PlayerRef.getComponentType());
  }
}
