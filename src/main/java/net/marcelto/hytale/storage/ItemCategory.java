package net.marcelto.hytale.storage;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fixed storage filters based on loaded item metadata, including inherited tags. */
public enum ItemCategory {
  ALL("All", "", ""),
  BLOCKS("Blocks", "Furniture", "Blocks"),
  WEAPONS("Weapons", "Weapon", "Items.Weapons"),
  TOOLS("Tools", "Tool", "Items.Tools"),
  ARMOR("Armor", "Armor", "Items.Armors"),
  FOOD("Food", "Food", "Items.Foods"),
  MATERIALS("Materials", "Ingredient", "Items.Ingredients");

  private final String label;
  private final String typeTag;
  private final String assetCategory;

  ItemCategory(String label, String typeTag, String assetCategory) {
    this.label = label;
    this.typeTag = typeTag;
    this.assetCategory = assetCategory;
  }

  @Nonnull
  public String label() {
    return label;
  }

  public boolean matches(@Nonnull Item item) {
    if (this == ALL) return true;
    if (this == BLOCKS && item.hasBlockType()) return true;

    final AssetExtraInfo.Data data = item.getData();
    final int tagIndex =
        AssetRegistry.getTagIndex("Type" + AssetExtraInfo.Data.TAG_VALUE_SEPARATOR + typeTag);
    if (data != null && data.getExpandedTagIndexes().contains(tagIndex)) return true;

    final String[] categories = item.getCategories();
    if (categories != null) {
      for (final String category : categories) {
        if (isCategory(category, assetCategory)
            || (this == BLOCKS && isCategory(category, "Furniture"))) return true;
      }
    }
    return false;
  }

  @Nullable
  public static ItemCategory fromId(@Nonnull String id) {
    for (final ItemCategory category : values()) {
      if (category.name().equals(id)) return category;
    }
    return null;
  }

  private static boolean isCategory(@Nonnull String category, @Nonnull String parent) {
    return category.equals(parent) || category.startsWith(parent + ".");
  }
}
