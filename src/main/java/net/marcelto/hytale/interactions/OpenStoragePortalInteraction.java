package net.marcelto.hytale.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.marcelto.hytale.gui.StoragePortalPage;
import net.marcelto.hytale.storage.PortalBlock;
import org.joml.Vector3i;

/**
 * Opens the Storage Portal window for the block that was used.
 *
 * <p>Both settings can be changed per block in its item json, next to the interaction type.
 */
public class OpenStoragePortalInteraction extends SimpleBlockInteraction {

  public static final int DEFAULT_RADIUS = 40;
  public static final int DEFAULT_MAX_CONTAINERS = 128;

  public static final BuilderCodec<OpenStoragePortalInteraction> CODEC =
      BuilderCodec.builder(
              OpenStoragePortalInteraction.class,
              OpenStoragePortalInteraction::new,
              SimpleBlockInteraction.CODEC)
          .documentation(
              "Opens a window that lists the items of all containers around the used block.")
          .append(
              new KeyedCodec<>("Radius", Codec.INTEGER),
              (interaction, radius) -> interaction.radius = radius,
              interaction -> Integer.valueOf(interaction.radius))
          .add()
          .append(
              new KeyedCodec<>("MaxContainers", Codec.INTEGER),
              (interaction, max) -> interaction.maxContainers = max,
              interaction -> Integer.valueOf(interaction.maxContainers))
          .add()
          .build();

  /** How far around the portal containers are included, in blocks. */
  protected int radius = DEFAULT_RADIUS;

  /** How many containers are included at most, nearest first. */
  protected int maxContainers = DEFAULT_MAX_CONTAINERS;

  @Override
  protected void interactWithBlock(
      @Nonnull World world,
      @Nonnull CommandBuffer<EntityStore> commandBuffer,
      @Nonnull InteractionType type,
      @Nonnull InteractionContext context,
      @Nullable ItemStack itemInHand,
      @Nonnull Vector3i pos,
      @Nonnull CooldownHandler cooldownHandler) {
    final Ref<EntityStore> ref = context.getEntity();
    final Player player = commandBuffer.getComponent(ref, Player.getComponentType());
    final PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
    final UUIDComponent uuid = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
    final BlockType blockType = PortalBlock.typeAt(world, pos.x, pos.y, pos.z);
    if (player == null || playerRef == null || uuid == null || blockType == null) {
      context.getState().state = InteractionState.Failed;
      return;
    }

    final StoragePortalPage page =
        new StoragePortalPage(
            playerRef,
            world,
            pos,
            PortalBlock.baseKey(blockType),
            uuid.getUuid(),
            Math.max(1, radius),
            Math.max(1, maxContainers));
    player.getPageManager().openCustomPage(ref, ref.getStore(), page);
    page.onOpened();
  }

  @Override
  protected void simulateInteractWithBlock(
      @Nonnull InteractionType type,
      @Nonnull InteractionContext context,
      @Nullable ItemStack itemInHand,
      @Nonnull World world,
      @Nonnull Vector3i targetBlock) {}
}
