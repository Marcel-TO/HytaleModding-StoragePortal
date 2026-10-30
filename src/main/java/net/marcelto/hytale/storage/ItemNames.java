package net.marcelto.hytale.storage;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/** Resolves item display names for one language, caching each lookup. */
public final class ItemNames {

  private final String language;
  private final Map<String, String> cache = new HashMap<>();

  public ItemNames(@Nonnull String language) {
    this.language = language;
  }

  @Nonnull
  public String of(@Nonnull ItemStack stack) {
    return cache.computeIfAbsent(stack.getItemId(), id -> resolve(stack, id));
  }

  @Nonnull
  private String resolve(@Nonnull ItemStack stack, @Nonnull String itemId) {
    final String translated =
        I18nModule.get().getMessage(language, stack.getItem().getTranslationKey());
    if (translated != null && !translated.isBlank()) return translated;
    return itemId.replace('_', ' ');
  }
}
