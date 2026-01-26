package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;

public final class TotemProtecaoPickupSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoPickupSystem(TotemProtecaoPlugin plugin) {
      super(InteractivelyPickupItemEvent.class);
      this.plugin = plugin;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer, InteractivelyPickupItemEvent event) {
      PlayerRef player = (PlayerRef) chunk.getComponent(entityIndex, PlayerRef.getComponentType());
      if (player != null && event != null) {
         UUID uuid = player.getUuid();
         if (uuid != null) {
            ItemStack stack = event.getItemStack();
            if (stack != null && this.isTotemProtecaoItem(stack.getItemId())) {
               TotemProtecaoPlugin.PendingProtection pending = this.plugin.consumePendingProtectionItem(uuid,
                     System.currentTimeMillis());
               if (pending != null) {
                  ItemStack updated = TotemProtecaoPlugin.applyProtectionMetadata(stack, pending.getRemainingMs(),
                        true);
                  event.setItemStack(updated);
               }
            }
         }
      }
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
}
