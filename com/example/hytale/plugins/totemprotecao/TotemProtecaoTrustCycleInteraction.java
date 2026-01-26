package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class TotemProtecaoTrustCycleInteraction extends ChoiceInteraction {
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;
   private final UUID target;

   public TotemProtecaoTrustCycleInteraction(TotemProtecaoPlugin plugin, int centerX, int centerZ, UUID target) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.target = target;
   }

   public void run(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
      if (this.plugin != null && store != null && ref != null && playerRef != null) {
         ClaimStore claims = this.plugin.getClaimStore();
         if (claims != null) {
            Claim claim = claims.findClaimByCenter(this.centerX, this.centerZ);
            if (claim != null && this.target != null) {
               UUID actor = playerRef.getUuid();
               if (actor != null) {
                  if (!this.plugin.isOpBypass(actor) && !claim.getOwner().equals(actor)) {
                     this.plugin.sendPlayerMessage(playerRef, "Only the owner can edit friends/permissions");
                  } else {
                     int current = claim.getPermissionsFor(this.target);
                     byte next;
                     if (current == 0) {
                        next = 1;
                     } else if (current == 1) {
                        next = 2;
                     } else if (current == 2) {
                        next = 4;
                     } else if (current == 4) {
                        next = 7;
                     } else {
                        next = 0;
                     }

                     if (next == 0) {
                        claim.removeTrusted(this.target);
                     } else {
                        claim.setTrusted(this.target, next);
                     }

                     claims.markDirty();
                     Player playerEntity = (Player) store.getComponent(ref, Player.getComponentType());
                     if (playerEntity != null) {
                        PageManager pages = playerEntity.getPageManager();
                        if (pages != null) {
                           pages.openCustomPage(ref, store,
                                 new TotemProtecaoTrustListPage(this.plugin, playerRef, this.centerX, this.centerZ));
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
