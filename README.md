# Chest Portal Sorter

Chest Portal Sorter adds the **Storage Portal**, a chest-like block that shows the items of every container around it in one window. Open it to see, take and store items without walking from chest to chest. Workbenches look into nearby chests in a similar way.

## The Storage Portal

The block is a gold-and-stone portal with a floating chest glyph. It holds nothing itself. Its window lists the combined contents of the containers within **20 blocks**, nearest first, up to **64 containers**.

The chest gently hovers, the surrounding sparks drift, and the energy panel subtly expands and contracts while its dark flecks rise through it, all in a four-second idle loop. The stone frame and the chest emblem on the back of the panel stay still.

The item has the ID `Storage_Portal` and **no crafting recipe**. Get it from the creative menu or with:

```
/give Storage_Portal
```

### Using it

Press **F** on the block, the same interact key a chest uses, to open the window.

| Part | What it does |
| --- | --- |
| Top grid | Every item of the nearby containers, summed per item type. Hover an item to see its name and total. |
| Search box | Filters the top grid by item name. |
| Click an item (top grid) | Takes one stack into your inventory. |
| Right-click an item (top grid) | Takes a single item. |
| Bottom grid | Your storage and hotbar items, summed per item type. |
| Click an item (bottom grid) | Stores all of that item in nearby containers, preferring containers that already hold it. |
| Right-click an item (bottom grid) | Stores a single item. |
| Quick stack | Moves every item of your inventory storage (not the hotbar) that a nearby container already holds into that container. |

The window closes when you walk more than 8 blocks away from the portal, change world, or the block is removed. The glyph's lid stays open while anyone has the window open and closes when the last viewer leaves. Idle motion resumes after each lid animation, with the lid held in the appropriate pose.

All amounts are computed on the server from the live containers each time you click, so a stale window cannot move more than the containers actually hold. The window refreshes about twice per second.

## Configuration

The range is set on the block's interaction in `Server/Item/Items/Storage_Portal.json`:

```json
"Interactions": {
  "Use": {
    "Interactions": [
      { "Type": "OpenStoragePortal", "Radius": 20, "MaxContainers": 64 }
    ]
  }
}
```

| Key | Default | Meaning |
| --- | --- | --- |
| `Radius` | 20 | How far around the portal containers are included, in blocks. |
| `MaxContainers` | 64 | How many containers are included at most. The nearest ones win. |

The custom model, texture and idle animations live in `Common/Blocks/StoragePortal`. The open and close states reuse the vanilla chest lid animations and sounds. Their completion times come from the animation assets; the plugin then switches to the closed or open idle loop. Idle motion runs on the client, without a server-side per-frame update.

The energy panel scrolls its texture upward one pixel at a time. Its faces sample two 18×98 strips at the bottom of the texture (`x` 0 and 18, `y` 64), each holding the 49-pixel energy tile twice so the loop wraps without a seam. The original energy tiles at the top of the texture are no longer used by the model; repaint the strips if you change the energy look. The chest emblem is a separate cutout overlay (`Energy_Emblem`) so it does not scroll.

When a chunk loads, saved open or close states return to an idle pose based on whether the portal still has viewers. Existing portals therefore animate without being replaced.

## Install

Put the mod JAR in the Hytale `UserData\Mods` folder (or the `mods` folder of a dedicated server) and enable **ChestPortalSorter** for the world. The JAR contains the plugin and its asset pack (item, translations and UI), so there is nothing else to install.

## Build from source

You need Java 25. From the repository root:

```powershell
.\gradlew.bat build
```

The JAR is written to `build\libs`. `.\gradlew.bat spotlessApply` formats the code, and the build runs Checkstyle.

## Project layout

| Path | Purpose |
| --- | --- |
| `net.marcelto.hytale.ChestPortalSorter` | Plugin entry point. Registers the `OpenStoragePortal` interaction and the refresh system. |
| `interactions/OpenStoragePortalInteraction` | Block interaction that opens the window. |
| `systems/StoragePortalSystem` | Refreshes open windows and closes invalid ones. |
| `systems/StoragePortalCleanupSystem` | Dismisses the window when its player disconnects, so the lid closes. |
| `systems/StoragePortalAnimationSystem` | Restores portal idle poses when saved chunk sections load. |
| `gui/StoragePortalPage` | The window: scan, aggregate, diff-based refresh and click actions. |
| `gui/ItemGridView`, `gui/ItemKeys` | Item grids and the item identifiers the client sends back. |
| `storage/` | Finding containers, summing items, moving items, and the lid state. |
| `Common/UI/Custom/Pages/StoragePortal*.ui` | The window layout, item card and grid spacer. |

## Known limitations

- **No permission checks.** The portal exposes every container within its range to whoever opens it. It does not know about claims or protection plugins, so use a small `Radius` on shared servers.
- **Not tested in a client yet.** On a headless 0.7.4 test server the plugin loads, enables and shuts down cleanly, and the item asset validates with the custom `OpenStoragePortal` interaction resolving. The window layout, item rendering and click behavior have not been seen in game.
- Large (double) chests should show up once because containers are de-duplicated by identity, but this has not been checked in game.
- The item grid may jump back to the top when the set of item types changes while you scroll.

## Compatibility

- Hytale server: 0.7.5 or later
- Mod ID: `Marcel-TO:ChestPortalSorter`
- License: BSD-3-Clause
