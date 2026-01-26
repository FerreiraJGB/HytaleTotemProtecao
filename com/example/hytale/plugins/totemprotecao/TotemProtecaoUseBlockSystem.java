package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent.Pre;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class TotemProtecaoUseBlockSystem extends EntityEventSystem<EntityStore, Pre> {
   private static final String VOIDHEART_ITEM_ID = "Ingredient_Voidheart";
   private static final String VOID_ESSENCE_ITEM_ID = "Ingredient_Void_Essence";
   private static final long VOIDHEART_MS = 43200000L;
   private static final long VOID_ESSENCE_MS = 240000L;
   private static final int VOID_ESSENCE_BATCH = 20;
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoUseBlockSystem(TotemProtecaoPlugin plugin) {
      super(Pre.class);
      this.plugin = plugin;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer, Pre event) {
      InteractionType interactionType = event == null ? null : event.getInteractionType();
      boolean canInteract = interactionType == null || interactionType == InteractionType.Primary
            || isUseInteraction(interactionType);

      PlayerRef player = (PlayerRef) chunk.getComponent(entityIndex, PlayerRef.getComponentType());
      if (player != null) {
         UUID uuid = player.getUuid();
         boolean bypass = this.plugin.isOpBypass(uuid);
         int x = event.getTargetBlock().x;
         int y = event.getTargetBlock().y;
         int z = event.getTargetBlock().z;
         long nowMs = System.currentTimeMillis();
         ClaimStore claims = this.plugin.getClaimStore();
         Claim claim = claims.findClaimAt(x, z);
         if (claim != null) {
            if (claim.getCenterY() == Integer.MIN_VALUE) {
               claims.updateClaimCenterYIfUnknown(claim.getCenterX(), claim.getCenterZ(), y);
               claim = claims.findClaimByCenter(claim.getCenterX(), claim.getCenterZ());
               if (claim == null) {
                  return;
               }
            }

            boolean isCenter = claim.getCenterX() == x && claim.getCenterZ() == z && claim.getCenterY() == y;
            if (isCenter && (bypass || claim.getOwner().equals(uuid))) {
               if (!canInteract) {
                  return;
               }

               if (this.tryRecharge(event, player, claim, claims, nowMs)) {
                  event.setCancelled(true);
                  return;
               }

               event.setCancelled(true);
               Ref ref = null;

               try {
                  ref = player.getReference();
               } catch (Throwable var21) {
               }

               if (ref == null) {
                  ref = chunk.getReferenceTo(entityIndex);
               }

               Player playerEntity;
               try {
                  playerEntity = (Player) store.getComponent(ref, Player.getComponentType());
               } catch (Exception var20) {
                  ((Api) this.plugin.getLogger().at(Level.WARNING).withCause(var20))
                        .log("TotemProtecao UI: failed to get Player component");
                  this.plugin.sendPlayerMessage(player,
                        "TotemProtecao UI error: could not read Player component");
                  return;
               }

               PageManager pages = playerEntity.getPageManager();

               try {
                  pages.openCustomPage(ref, store,
                        new TotemProtecaoTrustListPage(this.plugin, player, claim.getCenterX(), claim.getCenterZ()));
               } catch (Throwable var19) {
                  ((Api) this.plugin.getLogger().at(Level.WARNING).withCause(var19))
                        .log("TotemProtecao UI: openCustomPage failed");
                  this.plugin.sendPlayerMessage(player,
                        "TotemProtecao UI error: failed to open page (see server log)");
               }

            } else if (claim.isProtectionActive(nowMs)) {
               if (!bypass) {
                  if (!claim.getOwner().equals(uuid) && !claim.hasPermission(uuid, 4)) {
                     event.setCancelled(true);
                     this.plugin.sendPlayerMessage(player,
                           "You cannot use blocks inside this protected area");
                  }

               }
            }
         }
      }
   }

   private boolean tryRecharge(Pre event, PlayerRef player, Claim claim, ClaimStore claims, long nowMs) {
      InteractionContext context = event == null ? null : event.getContext();
      if (context == null) {
         return false;
      } else {
         ItemStack held = null;

         try {
            held = context.getHeldItem();
         } catch (Exception var13) {
         }

         if (held != null && !ItemStack.isEmpty(held)) {
            String itemId = null;

            try {
               itemId = held.getItemId();
            } catch (Exception var12) {
            }

            int qty = 0;

            try {
               qty = held.getQuantity();
            } catch (Exception var11) {
            }

            if (itemId != null && qty > 0) {
               int consume;
               long addedMs;
               if (this.isItemMatch(itemId, VOIDHEART_ITEM_ID)) {
                  consume = 1;
                  addedMs = VOIDHEART_MS;
               } else {
                  if (!this.isItemMatch(itemId, VOID_ESSENCE_ITEM_ID)) {
                     return false;
                  }

                  consume = Math.min(VOID_ESSENCE_BATCH, qty);
                  addedMs = VOID_ESSENCE_MS * (long) consume;
               }

               long remainingMs = claim.addProtectionMs(nowMs, addedMs);
               claims.markDirty();
               int newQty = qty - consume;
               ItemStack updatedStack = newQty <= 0 ? ItemStack.EMPTY : held.withQuantity(newQty);
               ItemContainer container = null;
               byte slot = 0;
               try {
                  container = context.getHeldItemContainer();
                  slot = context.getHeldItemSlot();
               } catch (Exception var16) {
               }

               if (container != null) {
                  container.setItemStackForSlot((short) slot, updatedStack);
               } else {
                  context.setHeldItem(updatedStack);
               }

               String addedText = TotemProtecaoPlugin.formatDuration(addedMs);
               String remainingText = TotemProtecaoPlugin.formatDuration(remainingMs);
               this.plugin.sendPlayerMessage(player,
                     "Protection recharged: +" + addedText + " (remaining " + remainingText + ")");
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   private static boolean isUseInteraction(InteractionType interactionType) {
      return interactionType == InteractionType.Secondary || interactionType == InteractionType.Use;
   }

   private boolean isItemMatch(String itemId, String expectedId) {
      if (itemId == null) {
         return false;
      } else if (expectedId == null) {
         return false;
      } else {
         String itemLower = itemId.toLowerCase(Locale.ROOT);
         String expectedLower = expectedId.toLowerCase(Locale.ROOT);
         if (itemLower.equals(expectedLower)) {
            return true;
         } else {
            return itemLower.endsWith(":" + expectedLower) ? true : itemLower.contains(expectedLower);
         }
      }
   }

}
