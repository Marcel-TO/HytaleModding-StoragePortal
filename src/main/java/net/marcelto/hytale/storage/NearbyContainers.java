package net.marcelto.hytale.storage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.function.consumer.TriIntConsumer;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.joml.Vector3dc;
import org.joml.Vector3i;

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
    final List<ItemContainer> containers = new ArrayList<>();
    for (final Ref<ChunkStore> ref : findRefs(world, center, radius, limit)) {
      containers.add(
          store.getComponent(ref, ItemContainerBlock.getComponentType()).getItemContainer());
    }
    return containers;
  }

  /**
   * Resolves live matching containers to their rotated block footprints, using the same scan limits
   * as transfers.
   */
  @Nonnull
  public static List<ContainerBounds> findMatchingBounds(
      @Nonnull World world,
      @Nonnull Vector3dc center,
      double radius,
      int limit,
      @Nonnull ItemStack prototype)
      throws UnavailableContainerException {
    final Store<ChunkStore> store = world.getChunkStore().getStore();
    final List<ContainerBounds> bounds = new ArrayList<>();
    for (final Ref<ChunkStore> ref : findRefs(world, center, radius, limit)) {
      final ItemContainer container =
          store.getComponent(ref, ItemContainerBlock.getComponentType()).getItemContainer();
      if (!container.containsItemStacksStackableWith(prototype)) continue;

      final BlockModule.BlockStateInfo info =
          store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
      final Vector3i position = new Vector3i();
      if (info == null || !info.fillWorldPos(store, position)) {
        throw new UnavailableContainerException("Cannot locate a matching storage container");
      }
      final BlockSection section =
          store.getComponent(info.getSectionRef(), BlockSection.getComponentType());
      final BlockType type =
          section == null ? null : BlockType.getAssetMap().getAsset(section.get(info.getIndex()));
      final BlockBoundingBoxes hitbox =
          type == null
              ? null
              : BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
      if (hitbox == null) {
        throw new UnavailableContainerException("Cannot resolve a matching container's footprint");
      }
      bounds.add(boundsOf(position, hitbox.get(section.getRotationIndex(info.getIndex()))));
    }
    return bounds;
  }

  @Nonnull
  static ContainerBounds boundsOf(
      @Nonnull Vector3i position, @Nonnull BlockBoundingBoxes.RotatedVariantBoxes hitbox) {
    final Vector3i min = new Vector3i();
    final Vector3i max = new Vector3i();
    FillerBlockUtil.forEachFillerBlock(
        hitbox,
        (x, y, z) -> {
          min.set(Math.min(min.x, x), Math.min(min.y, y), Math.min(min.z, z));
          max.set(Math.max(max.x, x), Math.max(max.y, y), Math.max(max.z, z));
        });
    return new ContainerBounds(
        position.x + min.x,
        position.y + min.y,
        position.z + min.z,
        position.x + max.x,
        position.y + max.y,
        position.z + max.z);
  }

  /** Inclusive world-space block bounds of one container, including its filler blocks. */
  public record ContainerBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public int width() {
      return maxX - minX + 1;
    }

    public int height() {
      return maxY - minY + 1;
    }

    public int depth() {
      return maxZ - minZ + 1;
    }

    public double centerX() {
      return minX + width() / 2.0;
    }

    public double centerY() {
      return minY + height() / 2.0;
    }

    public double centerZ() {
      return minZ + depth() / 2.0;
    }

    public void forEachBlock(@Nonnull TriIntConsumer consumer) {
      for (int x = 0; x < width(); x++) {
        for (int y = 0; y < height(); y++) {
          for (int z = 0; z < depth(); z++) {
            consumer.accept(minX + x, minY + y, minZ + z);
          }
        }
      }
    }
  }

  /** A matching container cannot be located in the currently loaded chunk data. */
  public static final class UnavailableContainerException extends Exception {

    private UnavailableContainerException(@Nonnull String message) {
      super(message);
    }
  }

  @Nonnull
  private static List<Ref<ChunkStore>> findRefs(
      @Nonnull World world, @Nonnull Vector3dc center, double radius, int limit) {
    final Store<ChunkStore> store = world.getChunkStore().getStore();
    final SpatialResource<Ref<ChunkStore>, ChunkStore> resource =
        store.getResource(BlockModule.get().getItemContainerSpatialResourceType());

    final List<Ref<ChunkStore>> refs = SpatialResource.<ChunkStore>getThreadLocalReferenceList();
    resource.getSpatialStructure().ordered(center, radius, refs);

    final Set<ItemContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    final List<Ref<ChunkStore>> containers = new ArrayList<>();
    for (final Ref<ChunkStore> ref : refs) {
      if (containers.size() >= limit) break;
      if (!ref.isValid()) continue;

      final ItemContainerBlock block =
          store.getComponent(ref, ItemContainerBlock.getComponentType());
      if (block == null) continue;

      final ItemContainer container = block.getItemContainer();
      if (seen.add(container)) containers.add(ref);
    }
    return containers;
  }
}
