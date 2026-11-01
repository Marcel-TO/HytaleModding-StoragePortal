package net.marcelto.hytale.storage;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.StateData;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
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

  private static final String OPEN_IDLE_STATE = "OpenIdle";

  private record AnimationKey(World world, int x, int y, int z) {}

  private static final ConcurrentHashMap<AnimationKey, Object> PENDING_ANIMATIONS =
      new ConcurrentHashMap<>();

  private PortalBlock() {}

  /** Invalidates delayed animation handoffs when the plugin stops. */
  public static void clearPendingAnimations() {
    PENDING_ANIMATIONS.clear();
  }

  /** Resumes idle motion if a chunk was saved during a lid animation. */
  public static void restoreIdle(
      @Nonnull World world, @Nonnull Ref<ChunkStore> sectionRef, int x, int y, int z) {
    if (!world.isAlive() || !sectionRef.isValid()) return;
    final ChunkStore chunkStore = world.getChunkStore();
    final BlockSection section =
        chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
    if (section == null) return;
    final BlockType blockType = BlockType.getAssetMap().getAsset(section.get(x, y, z));
    if (blockType == null || !"Storage_Portal".equals(baseKey(blockType))) return;

    PENDING_ANIMATIONS.remove(new AnimationKey(world, x, y, z));
    final boolean open =
        PortalViewers.hasViewers(new PortalViewers.PortalKey(world.getName(), x, y, z));
    BlockOperations.setBlockInteractionState(
        chunkStore,
        sectionRef,
        x,
        y,
        z,
        blockType,
        open ? OPEN_IDLE_STATE : StateData.NULL_STATE_ID,
        false);
  }

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
    final BlockType stateType = blockType.getBlockForState(state);
    if (stateType == null) {
      LOGGER.atWarning().log("Missing portal animation state %s for %s", state, portalKey);
      return;
    }

    BlockOperations.setBlockInteractionState(
        chunkStore, sectionRef, x, y, z, blockType, state, false);
    scheduleIdle(
        new AnimationKey(world, x, y, z),
        portalKey,
        stateType,
        state,
        open ? OPEN_IDLE_STATE : StateData.NULL_STATE_ID);

    final int soundIndex = stateType.getInteractionSoundEventIndex();
    if (soundIndex == SoundEvent.EMPTY_ID) return;

    final Vector3d soundPos = new Vector3d();
    blockType.getBlockCenter(section.getRotationIndex(x, y, z), soundPos);
    soundPos.add(x, y, z);
    SoundUtil.playSoundEvent3d(null, soundIndex, soundPos, world.getEntityStore().getStore());
  }

  private static void scheduleIdle(
      AnimationKey key,
      String portalKey,
      BlockType transitionType,
      String transitionState,
      String idleState) {
    PENDING_ANIMATIONS.remove(key);
    final String animationPath = transitionType.getCustomModelAnimation();
    final BlockyAnimationCache.BlockyAnimation animation =
        animationPath == null ? null : BlockyAnimationCache.getNow(animationPath);
    if (animation == null || transitionType.getBlockForState(idleState) == null) {
      LOGGER.atWarning().log(
          "Cannot resume portal idle animation for %s: missing clip %s or state %s",
          portalKey, animationPath, idleState);
      return;
    }

    final float speed = transitionType.getCustomModelAnimationSpeed();
    if (speed <= 0) {
      LOGGER.atFine().log("Portal animation %s is paused; skipped the idle handoff", animationPath);
      return;
    }

    final long delayMillis = (long) Math.ceil(animation.getDurationMillis() / speed);
    final Object transition = new Object();
    PENDING_ANIMATIONS.put(key, transition);
    try {
      HytaleServer.SCHEDULED_EXECUTOR.schedule(
          () -> {
            if (!key.world().isAlive()) {
              PENDING_ANIMATIONS.remove(key, transition);
              return;
            }
            try {
              key.world()
                  .execute(
                      () ->
                          finishAnimation(key, transition, portalKey, transitionState, idleState));
            } catch (SkipSentryException e) {
              PENDING_ANIMATIONS.remove(key, transition);
              LOGGER.atFine().log("World is shutting down, skipped the portal idle handoff");
            }
          },
          delayMillis,
          TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      PENDING_ANIMATIONS.remove(key, transition);
      LOGGER.atWarning().withCause(e).log("Could not schedule the portal idle handoff");
    }
  }

  private static void finishAnimation(
      AnimationKey key,
      Object transition,
      String portalKey,
      String transitionState,
      String idleState) {
    // A rapid close/reopen must not let an older timer finish the newer animation.
    if (!PENDING_ANIMATIONS.remove(key, transition) || !key.world().isAlive()) return;

    final BlockType blockType = typeAt(key.world(), key.x(), key.y(), key.z());
    if (blockType == null
        || !portalKey.equals(baseKey(blockType))
        || !transitionState.equals(blockType.getStateForBlock(blockType))) return;

    final ChunkStore chunkStore = key.world().getChunkStore();
    final Ref<ChunkStore> sectionRef =
        chunkStore.getChunkSectionReferenceAtBlock(key.x(), key.y(), key.z());
    if (sectionRef == null || !sectionRef.isValid()) return;

    BlockOperations.setBlockInteractionState(
        chunkStore, sectionRef, key.x(), key.y(), key.z(), blockType, idleState, false);
  }
}
