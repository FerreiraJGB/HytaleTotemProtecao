package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public final class TotemProtecaoPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoPlaceSystem(TotemProtecaoPlugin plugin) {
      super(PlaceBlockEvent.class);
      this.plugin = plugin;
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

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer, PlaceBlockEvent event) {
      PlayerRef player = (PlayerRef) chunk.getComponent(entityIndex, PlayerRef.getComponentType());
      if (player != null) {
         UUID uuid = player.getUuid();
         boolean bypass = this.plugin.isOpBypass(uuid);
         int x = event.getTargetBlock().x;
         int y = event.getTargetBlock().y;
         int z = event.getTargetBlock().z;
         long nowMs = System.currentTimeMillis();
         ClaimStore claims = this.plugin.getClaimStore();
         ItemStack inHand = event.getItemInHand();
         if (inHand != null && this.isTotemProtecaoItem(inHand.getItemId())) {
            if (!bypass) {
               int owned = claims.countClaimsForOwner(uuid);
               if (owned >= this.plugin.getMaxClaimsPerPlayer()) {
                  event.setCancelled(true);
                  this.plugin.sendPlayerMessage(player,
                        "No fue posible colocar el TotemProtecao: limite de protecciones alcanzado (" + owned + "/"
                              + this.plugin.getMaxClaimsPerPlayer() + ")");
                  return;
               }

               Claim existing = claims.findClaimAt(x, z);
               if (existing != null && existing.isProtectionActive(nowMs) && !existing.getOwner().equals(uuid)) {
                  event.setCancelled(true);
                  this.plugin.sendPlayerMessage(player, "No fue posible colocar el TotemProtecao: area protegida");
                  return;
               }
            }

            if (claims.intersectsAny(x, z, this.plugin.getClaimRadius())) {
               event.setCancelled(true);
               this.plugin.sendPlayerMessage(player,
                     "No fue posible colocar el TotemProtecao: la proteccion se superpondria a otra proteccion");
            } else {
               String ownerName = null;

               try {
                  ownerName = player.getUsername();
               } catch (Exception var16) {
               }

               Claim newClaim = new Claim(uuid, ownerName, x, y, z, this.plugin.getClaimRadius());
               long storedRemaining = TotemProtecaoPlugin.readProtectionRemainingMs(inHand);
               if (storedRemaining > 0L) {
                  newClaim.setProtectionRemainingMs(nowMs, storedRemaining, false);
               }

               boolean added = claims.addClaim(newClaim);
               if (added) {
                  this.plugin.markRecentClaimPlacement(x, z);
                  this.plugin.sendPlayerMessage(player, "Proteccion creada (radio " + this.plugin.getClaimRadius() + ")");
                  Api var10000 = this.plugin.getLogger().at(Level.INFO);
                  String var10001 = String.valueOf(uuid);
                  var10000.log("TotemProtecao claim created owner=" + var10001 + " center=" + x + "," + z + " radius="
                        + this.plugin.getClaimRadius() + " itemId=" + inHand.getItemId());
               } else {
                  this.plugin.sendPlayerMessage(player, "No fue posible colocar el TotemProtecao aqui");
               }

            }
         } else {
            Claim claim = claims.findClaimAt(x, z);
            if (claim != null && claim.isProtectionActive(nowMs)) {
               if (!bypass) {
                  if (!claim.getOwner().equals(uuid) && !claim.hasPermission(uuid, 1)) {
                     event.setCancelled(true);
                     this.plugin.sendPlayerMessage(player, "No puedes colocar bloques dentro de esta area protegida");
                  }

               }
            }
         }
      }
   }
}
