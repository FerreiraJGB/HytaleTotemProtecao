package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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
   private static final int DEFAULT_CONSUME_QUANTITY = 1;
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
            boolean canManageCenter = bypass || claim.getOwner().equals(uuid);
            boolean isCenterColumn = claim.getCenterX() == x && claim.getCenterZ() == z;
            boolean isCenter = false;
            if (isCenterColumn) {
               if (claim.getCenterY() == y) {
                  isCenter = true;
               } else if (canManageCenter && isTotemProtecaoBlock(event.getBlockType(), this.plugin.getTotemProtecaoItemId())) {
                  if (claim.getCenterY() == Integer.MIN_VALUE) {
                     claims.updateClaimCenterYIfUnknown(claim.getCenterX(), claim.getCenterZ(), y);
                  } else {
                     claims.updateClaimCenterY(claim.getCenterX(), claim.getCenterZ(), y);
                  }

                  claim = claims.findClaimByCenter(claim.getCenterX(), claim.getCenterZ());
                  if (claim == null) {
                     return;
                  }

                  isCenter = claim.getCenterY() == y;
               }
            }

            if (isCenter && canManageCenter) {
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
                        "Erro da UI do TotemProtecao: nao foi possivel ler o componente Player");
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
                        "Erro da UI do TotemProtecao: falha ao abrir a pagina (veja o log do servidor)");
               }

            } else if (claim.isProtectionActive(nowMs)) {
               if (!bypass) {
                  if (!claim.getOwner().equals(uuid) && !claim.hasPermission(uuid, 4)) {
                     event.setCancelled(true);
                     this.plugin.sendPlayerMessage(player, "Voce nao pode usar blocos dentro desta area protegida");
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
               TotemProtecaoPlugin.RechargeItemConfig rechargeItem = this.plugin.findRechargeItemConfig(itemId);
               if (rechargeItem == null) {
                  return false;
               }

               int consume = DEFAULT_CONSUME_QUANTITY;
               if (qty < consume) {
                  return false;
               }

               long addedMs = rechargeItem.getDurationMs() * (long) consume;
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
                     "Protecao recarregada: +" + addedText + " (restante " + remainingText + ")");
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

   private static boolean isTotemProtecaoBlock(BlockType blockType, String totemProtecaoItemId) {
      if (blockType == null || totemProtecaoItemId == null || totemProtecaoItemId.isEmpty()) {
         return false;
      } else {
         String blockId = null;

         try {
            blockId = blockType.getId();
         } catch (Exception var4) {
         }

         if (blockId == null || blockId.isEmpty()) {
            return false;
         } else {
            String blockLower = blockId.toLowerCase(Locale.ROOT);
            String protecaoLower = totemProtecaoItemId.toLowerCase(Locale.ROOT);
            if (blockLower.equals(protecaoLower)) {
               return true;
            } else {
               return blockLower.endsWith(":" + protecaoLower) ? true : blockLower.contains(protecaoLower);
            }
         }
      }
   }

}
