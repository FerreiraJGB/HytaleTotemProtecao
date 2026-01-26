package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;

public final class TotemProtecaoBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoBreakSystem(TotemProtecaoPlugin plugin) {
      super(BreakBlockEvent.class);
      this.plugin = plugin;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer, BreakBlockEvent event) {
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
            boolean isCenter = claim.getCenterX() == x && claim.getCenterZ() == z
                  && (claim.getCenterY() == Integer.MIN_VALUE || claim.getCenterY() == y);
            if (!claim.isProtectionActive(nowMs)) {
               if (isCenter && isTotemProtecaoBlock(event.getBlockType(), this.plugin.getTotemProtecaoItemId())) {
                  long remainingMs = claim.getProtectionRemainingMs(nowMs);
                  if (remainingMs > 0L) {
                     this.plugin.setPendingProtectionItem(uuid, remainingMs);
                  }
                  claims.removeClaimAt(x, z);
                  this.plugin.clearBorderForClaim(x, z);
               }

               return;
            }

            if (!isCenter || !this.plugin.shouldIgnoreCenterBreak(x, z)) {
               if (bypass) {
                  if (isCenter) {
                     this.plugin.setPendingProtectionItem(uuid, claim.getProtectionRemainingMs(nowMs));
                     claims.removeClaimAt(x, z);
                     this.plugin.clearBorderForClaim(x, z);
                     this.plugin.sendPlayerMessage(player, "Proteccion eliminada");
                  }

               } else if (isCenter) {
                  if (!claim.getOwner().equals(uuid)) {
                     event.setCancelled(true);
                     this.plugin.sendPlayerMessage(player, "No puedes romper esta proteccion");
                  } else {
                     this.plugin.setPendingProtectionItem(uuid, claim.getProtectionRemainingMs(nowMs));
                     claims.removeClaimAt(x, z);
                     this.plugin.clearBorderForClaim(x, z);
                     this.plugin.sendPlayerMessage(player, "Proteccion eliminada");
                  }
               } else if (!claim.getOwner().equals(uuid) && !claim.hasPermission(uuid, 2)) {
                  event.setCancelled(true);
                  this.plugin.sendPlayerMessage(player, "No puedes romper bloques dentro de esta area protegida");
               }
            }
         }
      }
   }

   private static boolean isTotemProtecaoBlock(BlockType blockType, String totemProtecaoItemId) {
      if (blockType == null || totemProtecaoItemId == null || totemProtecaoItemId.isEmpty()) {
         return false;
      }

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
