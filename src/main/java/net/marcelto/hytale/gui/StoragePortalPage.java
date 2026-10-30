package net.marcelto.hytale.gui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.marcelto.hytale.storage.ItemAggregator;
import net.marcelto.hytale.storage.ItemNames;
import net.marcelto.hytale.storage.ItemTransfers;
import net.marcelto.hytale.storage.NearbyContainers;
import net.marcelto.hytale.storage.PortalBlock;
import net.marcelto.hytale.storage.PortalViewers;
import net.marcelto.hytale.storage.StoredItem;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * The window of a Storage Portal: every item of the containers around the portal in one grid, next
 * to the items of the player's inventory.
 *
 * <p>The client only names an action and an item. Every amount is worked out here from the live
 * containers, so a stale or forged event cannot move more than the containers hold.
 */
public final class StoragePortalPage extends InteractiveCustomUIPage<StoragePortalPage.PageEvent> {

  private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

  private static final String PAGE = "Pages/StoragePortalPage.ui";
  private static final String STORAGE_GRID = "#StorageGrid";
  private static final String INVENTORY_GRID = "#InventoryGrid";

  /** The window closes when the player walks farther than this from the portal, in blocks. */
  private static final double MAX_DISTANCE = 8.0;

  private static final String HINT =
      "Click takes a stack or stores all, right click moves one item";

  private static final String TAKE_STACK = "TakeStack";
  private static final String TAKE_ONE = "TakeOne";
  private static final String DEPOSIT_ALL = "DepositAll";
  private static final String DEPOSIT_ONE = "DepositOne";
  private static final String QUICK_STACK = "QuickStack";

  private final World world;
  private final Vector3i pos;
  private final String portalKey;
  private final PortalViewers.PortalKey viewerKey;
  private final UUID viewerId;
  private final int radius;
  private final int maxContainers;

  private final ItemNames names;
  private final ItemKeys keys = new ItemKeys();
  private final ItemGridView storageView;
  private final ItemGridView inventoryView;

  private String search = "";
  private String status = HINT;
  private String shownInfo;
  private String shownStatus;
  private boolean registered;

  /**
   * Creates the window for one portal block.
   *
   * @param pos the position of the portal block.
   * @param portalKey the block key that identifies the portal, used to detect it being removed.
   * @param viewerId the player the window is for.
   * @param radius how far around the portal containers are included, in blocks.
   * @param maxContainers how many containers are included at most, nearest first.
   */
  public StoragePortalPage(
      @Nonnull PlayerRef playerRef,
      @Nonnull World world,
      @Nonnull Vector3i pos,
      @Nonnull String portalKey,
      @Nonnull UUID viewerId,
      int radius,
      int maxContainers) {
    super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageEvent.CODEC);
    this.world = world;
    this.pos = new Vector3i(pos);
    this.portalKey = portalKey;
    this.viewerId = viewerId;
    this.viewerKey = new PortalViewers.PortalKey(world.getName(), pos.x, pos.y, pos.z);
    this.radius = radius;
    this.maxContainers = maxContainers;
    this.names = new ItemNames(playerRef.getLanguage());
    this.storageView = new ItemGridView(STORAGE_GRID, TAKE_STACK, TAKE_ONE, keys);
    this.inventoryView = new ItemGridView(INVENTORY_GRID, DEPOSIT_ALL, DEPOSIT_ONE, keys);
  }

  @Override
  public void build(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull UICommandBuilder commands,
      @Nonnull UIEventBuilder events,
      @Nonnull Store<EntityStore> store) {
    commands.append(PAGE);

    events.addEventBinding(
        CustomUIEventBindingType.ValueChanged,
        "#SearchInput",
        EventData.of(PageEvent.SEARCH, "#SearchInput.Value"),
        false);
    events.addEventBinding(
        CustomUIEventBindingType.Activating,
        "#QuickStackButton",
        EventData.of(PageEvent.ACTION, QUICK_STACK),
        false);

    search = "";
    shownInfo = null;
    shownStatus = null;
    storageView.reset();
    inventoryView.reset();
    apply(ref, store, commands, events);
  }

  /** Called once the window is open. Opens the portal lid for the first viewer. */
  public void onOpened() {
    if (registered) return;

    registered = true;
    if (PortalViewers.add(viewerKey, viewerId)) {
      PortalBlock.setLid(world, pos, portalKey, true);
    }
  }

  @Override
  public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
    if (!registered) return;

    registered = false;
    if (PortalViewers.remove(viewerKey, viewerId)) {
      PortalBlock.setLid(world, pos, portalKey, false);
    }
  }

  /** Closes the window if the player left or the portal is gone, otherwise refreshes it. */
  public void tick(
      @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
    if (!isStillValid(ref, accessor)) {
      close();
      return;
    }
    refresh(ref, accessor);
  }

  @Override
  public void handleDataEvent(
      @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageEvent data) {
    if (!isStillValid(ref, store)) {
      close();
      return;
    }

    if (data.search != null) search = data.search.trim().toLowerCase(Locale.ROOT);
    if (data.action != null) status = perform(ref, store, data.action, data.key);

    refresh(ref, store);
  }

  private void refresh(
      @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
    final UICommandBuilder commands = new UICommandBuilder();
    final UIEventBuilder events = new UIEventBuilder();
    if (apply(ref, accessor, commands, events)) sendUpdate(commands, events, false);
  }

  /**
   * Scans the surroundings and adds whatever differs from what the client shows.
   *
   * @return true if anything was added, so the update has to be sent.
   */
  private boolean apply(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull ComponentAccessor<EntityStore> accessor,
      @Nonnull UICommandBuilder commands,
      @Nonnull UIEventBuilder events) {
    final List<ItemContainer> containers = containers();
    final List<StoredItem> all = ItemAggregator.aggregate(containers, names);
    final List<StoredItem> stored = ItemAggregator.filter(all, search);
    final List<StoredItem> carried =
        ItemAggregator.aggregate(
            List.of(
                InventoryComponent.getCombined(accessor, ref, InventoryComponent.STORAGE_FIRST)),
            names);

    boolean changed = storageView.update(commands, events, stored);
    changed |= inventoryView.update(commands, events, carried);

    final String info = describe(containers.size(), all, stored);
    if (!info.equals(shownInfo)) {
      shownInfo = info;
      commands.set("#ContainerInfo.Text", info);
      changed = true;
    }
    if (!status.equals(shownStatus)) {
      shownStatus = status;
      commands.set("#StatusLabel.Text", status);
      changed = true;
    }
    return changed;
  }

  @Nonnull
  private static String describe(
      int containerCount, @Nonnull List<StoredItem> all, @Nonnull List<StoredItem> shown) {
    if (containerCount == 0) return "No containers in range";
    if (shown.isEmpty() && !all.isEmpty()) return "No items match your search";

    long items = 0;
    for (final StoredItem item : all) {
      items += item.total();
    }
    return containerCount
        + (containerCount == 1 ? " container | " : " containers | ")
        + all.size()
        + " item types | "
        + items
        + " items";
  }

  @Nonnull
  private List<ItemContainer> containers() {
    return NearbyContainers.find(world, new Vector3d(pos.x, pos.y, pos.z), radius, maxContainers);
  }

  @Nonnull
  private String perform(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nonnull String action,
      @Nullable String key) {
    return switch (action) {
      case QUICK_STACK -> quickStack(ref, store);
      case TAKE_STACK, TAKE_ONE -> take(ref, store, prototypeOf(key), TAKE_ONE.equals(action));
      case DEPOSIT_ALL, DEPOSIT_ONE ->
          deposit(ref, store, prototypeOf(key), DEPOSIT_ONE.equals(action));
      default -> {
        LOGGER.atFine().log("Ignoring an unknown portal action");
        yield status;
      }
    };
  }

  @Nonnull
  private String take(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nullable ItemStack prototype,
      boolean single) {
    if (prototype == null) return "That item is no longer available";

    final List<ItemContainer> containers = containers();
    final int wanted = single ? 1 : Math.max(1, prototype.getItem().getMaxStack());
    final CombinedItemContainer inventory =
        InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);

    final int moved = ItemTransfers.take(containers, prototype, wanted, inventory);
    if (moved > 0) return "Took " + moved + "x " + names.of(prototype);

    return ItemTransfers.count(containers, prototype) > 0
        ? "Not enough room in your inventory"
        : "That item is no longer available";
  }

  @Nonnull
  private String deposit(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nullable ItemStack prototype,
      boolean single) {
    if (prototype == null) return "That item is no longer in your inventory";

    final List<ItemContainer> containers = containers();
    if (containers.isEmpty()) return "No containers in range";

    final CombinedItemContainer inventory =
        InventoryComponent.getCombined(store, ref, InventoryComponent.STORAGE_FIRST);

    final int wanted = single ? 1 : Integer.MAX_VALUE;
    final int moved = ItemTransfers.deposit(inventory, prototype, wanted, containers);
    if (moved > 0) return "Stored " + moved + "x " + names.of(prototype);

    return ItemTransfers.count(List.of(inventory), prototype) > 0
        ? "No room in nearby containers"
        : "That item is no longer in your inventory";
  }

  @SuppressWarnings("unchecked")
  @Nonnull
  private String quickStack(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
    final List<ItemContainer> containers = containers();
    if (containers.isEmpty()) return "No containers in range";

    final CombinedItemContainer storage =
        InventoryComponent.getCombined(store, ref, InventoryComponent.Storage.getComponentType());

    final int moved = ItemTransfers.quickStack(storage, containers);
    return moved > 0 ? "Quick stacked " + moved + " items" : "Nothing to quick stack";
  }

  @Nullable
  private ItemStack prototypeOf(@Nullable String key) {
    if (key == null) return null;

    try {
      return keys.prototypeOf(Integer.parseInt(key));
    } catch (NumberFormatException e) {
      LOGGER.atFine().log("Ignoring a malformed item key");
      return null;
    }
  }

  private boolean isStillValid(
      @Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> accessor) {
    if (!world.isAlive() || !ref.isValid()) return false;
    if (accessor.getExternalData().getWorld() != world) return false;

    final TransformComponent transform =
        accessor.getComponent(ref, TransformComponent.getComponentType());
    if (transform == null) return false;

    final Vector3d position = transform.getPosition();
    final double dx = position.x() - (pos.x + 0.5);
    final double dy = position.y() - (pos.y + 0.5);
    final double dz = position.z() - (pos.z + 0.5);
    if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE) return false;

    return portalKey.equals(PortalBlock.keyAt(world, pos.x, pos.y, pos.z));
  }

  /** What a client event can carry: an action on an item, or a new search text. */
  public static final class PageEvent {

    public static final String ACTION = "Action";
    public static final String KEY = "Key";
    public static final String SEARCH = "@SearchQuery";

    public static final BuilderCodec<PageEvent> CODEC =
        BuilderCodec.builder(PageEvent.class, PageEvent::new)
            .append(
                new KeyedCodec<>(ACTION, Codec.STRING),
                (event, value) -> event.action = value,
                event -> event.action)
            .add()
            .append(
                new KeyedCodec<>(KEY, Codec.STRING),
                (event, value) -> event.key = value,
                event -> event.key)
            .add()
            .append(
                new KeyedCodec<>(SEARCH, Codec.STRING),
                (event, value) -> event.search = value,
                event -> event.search)
            .add()
            .build();

    private String action;
    private String key;
    private String search;
  }
}
