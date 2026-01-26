package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent.Pre;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;

public final class TotemProtecaoCraftSystem extends EntityEventSystem<EntityStore, Pre> {
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoCraftSystem(TotemProtecaoPlugin plugin) {
      super(Pre.class);
      this.plugin = plugin;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   private boolean isTotemProtecaoItem(String itemId) {
      if (itemId == null) {
         return false;
      } else {
         String configured = this.plugin.getTotemProtecaoItemId();
         if (configured != null && !configured.isEmpty()) {
            String itemLower = itemId.toLowerCase(Locale.ROOT);
            String confLower = configured.toLowerCase(Locale.ROOT);
            if (itemLower.equals(confLower)) {
               return true;
            } else {
               return itemLower.endsWith(":" + confLower) ? true : itemLower.contains(confLower);
            }
         } else {
            return false;
         }
      }
   }

   public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer, Pre event) {
      if (event != null) {
         if (!this.plugin.isAllowCrafting()) {
            CraftingRecipe recipe;
            try {
               recipe = event.getCraftedRecipe();
            } catch (Exception var12) {
               return;
            }

            if (recipe != null) {
               MaterialQuantity out;
               try {
                  out = recipe.getPrimaryOutput();
               } catch (Exception var11) {
                  return;
               }

               if (out != null) {
                  String outId;
                  try {
                     outId = out.getItemId();
                  } catch (Exception var10) {
                     return;
                  }

                  if (this.isTotemProtecaoItem(outId)) {
                     event.setCancelled(true);
                     PlayerRef player = (PlayerRef) chunk.getComponent(entityIndex, PlayerRef.getComponentType());
                     if (player != null) {
                        this.plugin.sendPlayerMessage(player,
                              "Protection Totem crafting is disabled on this server");
                     }

                  }
               }
            }
         }
      }
   }
}
