package net.marcelto.hytale.storage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3dc;

/** Finds the item containers around a point, the same way workbenches find their chests. */
public final class NearbyContainers {

  private NearbyContainers() {}

  /**
   * Returns the containers within {@code radius} blocks of {@code center}, nearest first.
   *
   * @param limit the maximum number of containers to return.
   */
  @Nonnull
  public static List<ItemContainer> find(
      @Nonnull World world, @Nonnull Vector3dc center, double radius, int limit) {
    final Store<ChunkStore> store = world.getChunkStore().getStore();
    final SpatialResource<Ref<ChunkStore>, ChunkStore> resource =
        store.getResource(BlockModule.get().getItemContainerSpatialResourceType());

    final List<Ref<ChunkStore>> refs = SpatialResource.<ChunkStore>getThreadLocalReferenceList();
    resource.getSpatialStructure().ordered(center, radius, refs);

    final Set<ItemContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<ItemContainer> containers = new ArrayList<>();
    for (final Ref<ChunkStore> ref : refs) {
      if (containers.size() >= limit) break;
      if (!ref.isValid()) continue;

      final ItemContainerBlock block =
          store.getComponent(ref, ItemContainerBlock.getComponentType());
      if (block == null) continue;

      final ItemContainer container = block.getItemContainer();
      if (seen.add(container)) containers.add(container);
    }
    return containers;
  }
}
