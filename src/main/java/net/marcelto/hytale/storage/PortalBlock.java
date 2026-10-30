package net.marcelto.hytale.storage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Reads the portal block from the world and drives its open and close lid states. */
public final class PortalBlock {

  private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

  /** Block state names shared with the vanilla chests. */
  private static final String OPEN_STATE = "OpenWindow";

  private static final String CLOSE_STATE = "CloseWindow";

  private PortalBlock() {}

  /** Returns the block type at the position, or null if its chunk is not loaded. */
  @Nullable
  public static BlockType typeAt(@Nonnull World world, int x, int y, int z) {
    final ChunkStore chunkStore = world.getChunkStore();
    final Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
    if (sectionRef == null || !sectionRef.isValid()) return null;

    final BlockSection section =
        chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
    if (section == null) return null;

    return BlockType.getAssetMap().getAsset(section.get(x, y, z));
  }

  /** Returns the key shared by a block and all of its state variants (open, closed). */
  @Nonnull
  public static String baseKey(@Nonnull BlockType type) {
    return Objects.requireNonNullElse(type.getDefaultStateKey(), type.getId());
  }

  /** Returns the base key of the block at the position, or null if it cannot be read. */
  @Nullable
  public static String keyAt(@Nonnull World world, int x, int y, int z) {
    final BlockType type = typeAt(world, x, y, z);
    return type == null ? null : baseKey(type);
  }

  /**
   * Switches the lid of the portal at the position, on the world thread.
   *
   * <p>Nothing happens if the block is no longer the portal identified by {@code portalKey}.
   */
  public static void setLid(
      @Nonnull World world, @Nonnull Vector3i pos, @Nonnull String portalKey, boolean open) {
    if (!world.isAlive()) return;

    final int x = pos.x;
    final int y = pos.y;
    final int z = pos.z;
    try {
      world.execute(() -> applyLid(world, x, y, z, portalKey, open));
    } catch (SkipSentryException e) {
      LOGGER.atFine().log("World is shutting down, skipped the portal lid update");
    }
  }

  private static void applyLid(
      @Nonnull World world, int x, int y, int z, @Nonnull String portalKey, boolean open) {
    if (!world.isAlive()) return;

    final ChunkStore chunkStore = world.getChunkStore();
    final Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
    if (sectionRef == null || !sectionRef.isValid()) return;

    final BlockSection section =
        chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
    if (section == null) return;

    final BlockType blockType = BlockType.getAssetMap().getAsset(section.get(x, y, z));
    if (blockType == null || !portalKey.equals(baseKey(blockType))) return;

    final String state = open ? OPEN_STATE : CLOSE_STATE;
    BlockOperations.setBlockInteractionState(
        chunkStore, sectionRef, x, y, z, blockType, state, false);

    final BlockType stateType = blockType.getBlockForState(state);
    if (stateType == null) return;

    final int soundIndex = stateType.getInteractionSoundEventIndex();
    if (soundIndex == SoundEvent.EMPTY_ID) return;

    final Vector3d soundPos = new Vector3d();
    blockType.getBlockCenter(section.getRotationIndex(x, y, z), soundPos);
    soundPos.add(x, y, z);
    SoundUtil.playSoundEvent3d(null, soundIndex, soundPos, world.getEntityStore().getStore());
  }
}
