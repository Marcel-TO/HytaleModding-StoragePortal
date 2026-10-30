package net.marcelto.hytale;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;
import net.marcelto.hytale.interactions.OpenStoragePortalInteraction;
import net.marcelto.hytale.storage.PortalViewers;
import net.marcelto.hytale.systems.StoragePortalCleanupSystem;
import net.marcelto.hytale.systems.StoragePortalSystem;

public class StoragePortal extends JavaPlugin {

  public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

  public StoragePortal(@Nonnull JavaPluginInit init) {
    super(init);
    LOGGER.atInfo().log("Initializing ChestPortalSorter...");
  }

  @Override
  protected void setup() {
    this.getCodecRegistry(Interaction.CODEC)
        .register(
            "OpenStoragePortal",
            OpenStoragePortalInteraction.class,
            OpenStoragePortalInteraction.CODEC);
    this.getEntityStoreRegistry().registerSystem(new StoragePortalSystem());
    this.getEntityStoreRegistry().registerSystem(new StoragePortalCleanupSystem());
  }

  @Override
  protected void shutdown() {
    PortalViewers.clear();
  }
}
