package net.marcelto.hytale.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import net.marcelto.hytale.storage.PortalBlock;

/** Restores idle motion for portal states saved before a chunk unloaded or the server stopped. */
public final class StoragePortalAnimationSystem extends RefSystem<ChunkStore> {

  @Override
  public void onEntityAdded(
      @Nonnull Ref<ChunkStore> ref,
      @Nonnull AddReason reason,
      @Nonnull Store<ChunkStore> store,
      @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
    if (reason != AddReason.LOAD) return;
    final BlockSection blocks = store.getComponent(ref, BlockSection.getComponentType());
    if (blocks == null) return;

    final Set<Integer> portalStates = new HashSet<>();
    blocks.forEachValue(
        id -> {
          final BlockType type = BlockType.getAssetMap().getAsset(id);
          if (type != null
              && type.isState()
              && "Storage_Portal".equals(PortalBlock.baseKey(type))) {
            portalStates.add(id);
          }
        });
    if (portalStates.isEmpty()) return;

    commandBuffer.run(
        loadedStore -> {
          if (!ref.isValid()) return;
          final BlockSection loadedBlocks =
              loadedStore.getComponent(ref, BlockSection.getComponentType());
          final ChunkSection section =
              loadedStore.getComponent(ref, ChunkSection.getComponentType());
          if (loadedBlocks == null || section == null) return;

          final World world = loadedStore.getExternalData().getWorld();
          for (int index = 0; index < ChunkUtil.SIZE_BLOCKS; index++) {
            if (!portalStates.contains(loadedBlocks.get(index))
                || loadedBlocks.getFiller(index) != 0) continue;
            PortalBlock.restoreIdle(
                world,
                ref,
                ChunkUtil.minBlock(section.getX()) + ChunkUtil.xFromIndex(index),
                ChunkUtil.minBlock(section.getY()) + ChunkUtil.yFromIndex(index),
                ChunkUtil.minBlock(section.getZ()) + ChunkUtil.zFromIndex(index));
          }
        });
  }

  @Override
  public void onEntityRemove(
      @Nonnull Ref<ChunkStore> ref,
      @Nonnull RemoveReason reason,
      @Nonnull Store<ChunkStore> store,
      @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
    // No per-section state is retained.
  }

  @Override
  public Query<ChunkStore> getQuery() {
    return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
  }
}
