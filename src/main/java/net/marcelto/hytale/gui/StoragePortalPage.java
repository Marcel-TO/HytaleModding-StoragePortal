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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
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
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
  private static final String HOTBAR_GRID = "#HotbarGrid";

  /** The window closes when the player walks farther than this from the portal, in blocks. */
  private static final double MAX_DISTANCE = 8.0;

  private static final String HINT =
      "Click takes or stores a stack, right click opens the item menu";

  private static final String TAKE_STACK = "TakeStack";
  private static final String TAKE_HALF_STACK = "TakeHalfStack";
  private static final String TAKE_ONE = "TakeOne";
  private static final String DEPOSIT_ALL = "DepositAll";
  private static final String DEPOSIT_STACK = "DepositStack";
  private static final String DEPOSIT_HALF_STACK = "DepositHalfStack";
  private static final String DEPOSIT_ONE = "DepositOne";
  private static final String QUICK_STACK = "QuickStack";
  private static final String SELECT_STORAGE = "SelectStorage";
  private static final String SELECT_INVENTORY = "SelectInventory";
  private static final String CLOSE_SELECTION = "CloseSelection";
  private static final String HIGHLIGHT = "Highlight";

  private static final String HIGHLIGHT_PARTICLES = "StoragePortal_Container_Highlight";
  private static final String HIGHLIGHT_DOUBLE_X = "StoragePortal_Container_Highlight_Double_X";
  private static final String HIGHLIGHT_DOUBLE_Z = "StoragePortal_Container_Highlight_Double_Z";
  private static final float HIGHLIGHT_SECONDS = 10.0f;

  private static final String STORAGE_SECTION = "storage";
  private static final String HOTBAR_SECTION = "hotbar";

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
  private final ItemGridView hotbarView;

  private String search = "";
  private String status = HINT;
  private String shownInfo;
  private String shownStatus;
  private ItemSelection selection;
  private ItemSelection shownSelection;
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
    this.storageView = new ItemGridView(STORAGE_GRID, TAKE_STACK, SELECT_STORAGE, keys, 9, 72);
    this.inventoryView =
        new ItemGridView(
            INVENTORY_GRID,
            DEPOSIT_ALL,
            SELECT_INVENTORY,
            keys,
            InventoryComponent.DEFAULT_STORAGE_COLUMNS,
            72,
            STORAGE_SECTION);
    this.hotbarView =
        new ItemGridView(
            HOTBAR_GRID,
            DEPOSIT_ALL,
            SELECT_INVENTORY,
            keys,
            InventoryComponent.DEFAULT_STORAGE_COLUMNS,
            72,
            HOTBAR_SECTION);
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
    events.addEventBinding(
        CustomUIEventBindingType.Activating,
        "#SelectionCloseButton",
        EventData.of(PageEvent.ACTION, CLOSE_SELECTION),
        false);
    events.addEventBinding(
        CustomUIEventBindingType.Activating,
        "#SelectionDismissArea",
        EventData.of(PageEvent.ACTION, CLOSE_SELECTION),
        false);

    search = "";
    shownInfo = null;
    shownStatus = null;
    selection = null;
    shownSelection = null;
    storageView.reset();
    inventoryView.reset();
    hotbarView.reset();
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
    selection = null;
    shownSelection = null;
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

    if (data.search != null) {
      search = data.search.trim().toLowerCase(Locale.ROOT);
      selection = null;
    }
    if (HIGHLIGHT.equals(data.action)) {
      if (highlight(ref, store, data.key, data.slot)) return;
    } else if (data.action != null) {
      status = perform(ref, store, data.action, data.key, data.slot);
    }

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
    final InventoryComponent.Storage inventory =
        accessor.getComponent(ref, InventoryComponent.Storage.getComponentType());
    final InventoryComponent.Hotbar hotbar =
        accessor.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
    final List<StoredItem> carried =
        inventory == null ? List.of() : ItemAggregator.stacks(inventory.getInventory(), names);
    final List<StoredItem> hotbarItems =
        hotbar == null ? List.of() : ItemAggregator.stacks(hotbar.getInventory(), names);

    boolean changed = storageView.update(commands, events, stored);
    changed |= inventoryView.update(commands, events, carried);
    changed |= hotbarView.update(commands, events, hotbarItems);
    changed |= updateSelection(commands, events, stored, carried, hotbarItems);

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

  private boolean updateSelection(
      @Nonnull UICommandBuilder commands,
      @Nonnull UIEventBuilder events,
      @Nonnull List<StoredItem> stored,
      @Nonnull List<StoredItem> carried,
      @Nonnull List<StoredItem> hotbarItems) {
    if (selection != null) {
      final ItemStack prototype = selection.prototype();
      final InventorySlot slot = selection.slot();
      final boolean present;
      if (selection.storage()) {
        present = stored.stream().anyMatch(item -> item.prototype().equals(prototype));
      } else if (slot != null) {
        final List<StoredItem> section = slot.hotbar() ? hotbarItems : carried;
        present =
            slot.index() < section.size()
                && section.get(slot.index()).prototype().equals(prototype);
      } else {
        present =
            carried.stream().anyMatch(item -> item.prototype().equals(prototype))
                || hotbarItems.stream().anyMatch(item -> item.prototype().equals(prototype));
      }
      if (!present) {
        status = unavailableItem(selection.storage());
        selection = null;
      }
    }
    if (Objects.equals(selection, shownSelection)) return false;

    shownSelection = selection;
    commands.set("#ItemSelectionOverlay.Visible", selection != null);
    if (selection == null) return true;

    commands.set("#SelectionTitle.Text", names.of(selection.prototype()));
    commands.set(
        "#SelectionDirection.Text",
        selection.storage() ? "Take from storage" : "Store in nearby containers");
    final String key = String.valueOf(keys.idOf(selection.prototype()));
    final String slot = selection.slot() == null ? null : selection.slot().encode();
    bindSelection(events, "#SelectionOne", selection.storage() ? TAKE_ONE : DEPOSIT_ONE, key, slot);
    bindSelection(
        events,
        "#SelectionHalfStack",
        selection.storage() ? TAKE_HALF_STACK : DEPOSIT_HALF_STACK,
        key,
        slot);
    bindSelection(
        events, "#SelectionStack", selection.storage() ? TAKE_STACK : DEPOSIT_STACK, key, slot);
    bindSelection(events, "#SelectionHighlight", HIGHLIGHT, key, slot);
    return true;
  }

  private static void bindSelection(
      @Nonnull UIEventBuilder events,
      @Nonnull String selector,
      @Nonnull String action,
      @Nonnull String key,
      @Nullable String slot) {
    EventData data = EventData.of(PageEvent.ACTION, action).append(PageEvent.KEY, key);
    if (slot != null) data = data.append(PageEvent.SLOT, slot);
    events.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
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
      @Nullable String key,
      @Nullable String slotText) {
    selection = null;
    final InventorySlot slot = InventorySlot.parse(slotText);
    return switch (action) {
      case SELECT_STORAGE, SELECT_INVENTORY -> {
        final boolean storage = SELECT_STORAGE.equals(action);
        final ItemStack prototype = prototypeOf(key);
        if (prototype == null) yield unavailableItem(storage);

        selection = new ItemSelection(prototype, storage, storage ? null : slot);
        yield status;
      }
      case CLOSE_SELECTION -> status;
      case QUICK_STACK -> quickStack(ref, store);
      case TAKE_STACK, TAKE_HALF_STACK, TAKE_ONE -> take(ref, store, prototypeOf(key), action);
      case DEPOSIT_ALL, DEPOSIT_STACK, DEPOSIT_HALF_STACK, DEPOSIT_ONE ->
          deposit(ref, store, prototypeOf(key), slot, action);
      default -> {
        LOGGER.atFine().log("Ignoring an unknown portal action");
        yield status;
      }
    };
  }

  private boolean highlight(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nullable String key,
      @Nullable String slotText) {
    final ItemSelection selected = selection;
    selection = null;
    if (selected == null || !selected.matches(prototypeOf(key), slotText)) {
      status = "That item is no longer available";
      return false;
    }

    final ItemStack prototype = selected.prototype();
    if (!selected.storage()) {
      final InventorySlot slot = selected.slot();
      final ItemContainer inventory = slot == null ? null : inventorySection(ref, store, slot);
      if (slot == null || inventory == null || !slotContains(inventory, slot.index(), prototype)) {
        status = unavailableItem(false);
        return false;
      }
    }

    final List<NearbyContainers.ContainerBounds> bounds;
    try {
      bounds =
          NearbyContainers.findMatchingBounds(
              world, new Vector3d(pos.x, pos.y, pos.z), radius, maxContainers, prototype);
    } catch (NearbyContainers.UnavailableContainerException e) {
      LOGGER.atWarning().withCause(e).log("Cannot locate a container for highlighting");
      status = "Highlight is unavailable: cannot locate a matching container";
      return false;
    }
    if (bounds.isEmpty()) {
      status = "No nearby containers hold that item";
      return false;
    }
    for (final NearbyContainers.ContainerBounds container : bounds) {
      final String particles = highlightParticles(container);
      if (ParticleSystem.getAssetMap().getAsset(particles) == null) {
        LOGGER.atWarning().log("Missing storage container highlight particle asset %s", particles);
        status = "Highlight is unavailable: missing particle asset";
        return false;
      }
    }

    final List<Ref<EntityStore>> viewers = List.of(ref);
    for (final NearbyContainers.ContainerBounds container : bounds) {
      final String particles = highlightParticles(container);
      if (HIGHLIGHT_PARTICLES.equals(particles)) {
        container.forEachBlock(
            (x, y, z) -> spawnHighlight(particles, x + 0.5, y + 0.5, z + 0.5, viewers, store));
      } else {
        spawnHighlight(
            particles,
            container.centerX(),
            container.centerY(),
            container.centerZ(),
            viewers,
            store);
      }
    }
    playerRef.sendMessage(
        Message.raw(
            "Highlighted "
                + bounds.size()
                + (bounds.size() == 1 ? " container" : " containers")
                + " holding "
                + names.of(prototype)
                + " for 10 seconds"));
    close();
    return true;
  }

  @Nonnull
  private static String highlightParticles(@Nonnull NearbyContainers.ContainerBounds bounds) {
    if (bounds.height() == 1) {
      if (bounds.width() == 2 && bounds.depth() == 1) return HIGHLIGHT_DOUBLE_X;
      if (bounds.width() == 1 && bounds.depth() == 2) return HIGHLIGHT_DOUBLE_Z;
    }
    return HIGHLIGHT_PARTICLES;
  }

  private static void spawnHighlight(
      @Nonnull String particles,
      double x,
      double y,
      double z,
      @Nonnull List<Ref<EntityStore>> viewers,
      @Nonnull Store<EntityStore> store) {
    ParticleUtil.spawnParticleEffect(
        particles, x, y, z, 0, 0, 0, 1, null, null, viewers, store, HIGHLIGHT_SECONDS);
  }

  @Nonnull
  private String take(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nullable ItemStack prototype,
      @Nonnull String action) {
    if (prototype == null) return "That item is no longer available";

    final List<ItemContainer> containers = containers();
    final int wanted = transferAmount(action, prototype.getItem().getMaxStack());
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
      @Nullable InventorySlot slot,
      @Nonnull String action) {
    if (prototype == null) return "That item is no longer in your inventory";

    final List<ItemContainer> containers = containers();
    if (containers.isEmpty()) return "No containers in range";

    final int wanted = transferAmount(action, prototype.getItem().getMaxStack());
    if (slot != null) return depositSlot(ref, store, prototype, slot, wanted, containers);

    final CombinedItemContainer inventory =
        InventoryComponent.getCombined(store, ref, InventoryComponent.STORAGE_FIRST);

    final int moved = ItemTransfers.deposit(inventory, prototype, wanted, containers);
    if (moved > 0) return "Stored " + moved + "x " + names.of(prototype);

    return ItemTransfers.count(List.of(inventory), prototype) > 0
        ? "No room in nearby containers"
        : "That item is no longer in your inventory";
  }

  /** Stores items from the one inventory slot the player clicked, never from other stacks. */
  @Nonnull
  private String depositSlot(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nonnull ItemStack prototype,
      @Nonnull InventorySlot slot,
      int wanted,
      @Nonnull List<ItemContainer> containers) {
    final ItemContainer inventory = inventorySection(ref, store, slot);
    if (inventory == null) return "That stack is no longer in your inventory";
    final int moved =
        ItemTransfers.depositSlot(inventory, slot.index(), prototype, wanted, containers);
    if (moved > 0) return "Stored " + moved + "x " + names.of(prototype);

    return slotContains(inventory, slot.index(), prototype)
        ? "No room in nearby containers"
        : "That stack is no longer in your inventory";
  }

  @Nullable
  private static ItemContainer inventorySection(
      @Nonnull Ref<EntityStore> ref,
      @Nonnull Store<EntityStore> store,
      @Nonnull InventorySlot slot) {
    final InventoryComponent section =
        slot.hotbar()
            ? store.getComponent(ref, InventoryComponent.Hotbar.getComponentType())
            : store.getComponent(ref, InventoryComponent.Storage.getComponentType());
    return section == null ? null : section.getInventory();
  }

  private static boolean slotContains(
      @Nonnull ItemContainer inventory, short slot, @Nonnull ItemStack prototype) {
    if (slot < 0 || slot >= inventory.getCapacity()) return false;

    final ItemStack current = inventory.getItemStack(slot);
    return !ItemStack.isEmpty(current) && current.isStackableWith(prototype);
  }

  private static int transferAmount(@Nonnull String action, int maxStack) {
    return switch (action) {
      case TAKE_ONE, DEPOSIT_ONE -> 1;
      case TAKE_HALF_STACK, DEPOSIT_HALF_STACK -> Math.max(1, maxStack / 2);
      case TAKE_STACK, DEPOSIT_STACK -> Math.max(1, maxStack);
      case DEPOSIT_ALL -> Integer.MAX_VALUE;
      default -> throw new IllegalArgumentException("Unknown transfer action: " + action);
    };
  }

  @Nonnull
  private static String unavailableItem(boolean storage) {
    return storage
        ? "That item is no longer available"
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

  private record ItemSelection(
      @Nonnull ItemStack prototype, boolean storage, @Nullable InventorySlot slot) {

    private boolean matches(@Nullable ItemStack eventPrototype, @Nullable String eventSlot) {
      return prototype.equals(eventPrototype)
          && Objects.equals(slot == null ? null : slot.encode(), eventSlot);
    }
  }

  /** One slot of the player's inventory storage or hotbar, as named by a client event. */
  private record InventorySlot(boolean hotbar, short index) {

    @Nullable
    static InventorySlot parse(@Nullable String text) {
      if (text == null) return null;

      final int colon = text.indexOf(':');
      if (colon < 0) return null;

      final String section = text.substring(0, colon);
      final boolean hotbar;
      if (HOTBAR_SECTION.equals(section)) {
        hotbar = true;
      } else if (STORAGE_SECTION.equals(section)) {
        hotbar = false;
      } else {
        return null;
      }
      try {
        final int index = Integer.parseInt(text.substring(colon + 1));
        if (index < 0 || index > Short.MAX_VALUE) return null;
        return new InventorySlot(hotbar, (short) index);
      } catch (NumberFormatException e) {
        LOGGER.atFine().log("Ignoring a malformed inventory slot");
        return null;
      }
    }

    @Nonnull
    String encode() {
      return (hotbar ? HOTBAR_SECTION : STORAGE_SECTION) + ":" + index;
    }
  }

  /** What a client event can carry: an action on an item, or a new search text. */
  public static final class PageEvent {

    public static final String ACTION = "Action";
    public static final String KEY = "Key";
    public static final String SLOT = "Slot";
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
                new KeyedCodec<>(SLOT, Codec.STRING),
                (event, value) -> event.slot = value,
                event -> event.slot)
            .add()
            .append(
                new KeyedCodec<>(SEARCH, Codec.STRING),
                (event, value) -> event.search = value,
                event -> event.search)
            .add()
            .build();

    private String action;
    private String key;
    private String slot;
    private String search;
  }
}
