package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class TotemProtecaoProtectionToggleInteraction extends ChoiceInteraction {
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;

   public TotemProtecaoProtectionToggleInteraction(TotemProtecaoPlugin plugin, int centerX, int centerZ) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
   }

   public void run(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
      if (this.plugin != null && store != null && ref != null && playerRef != null) {
         UUID actor = playerRef.getUuid();
         if (actor != null) {
            ClaimStore claims = this.plugin.getClaimStore();
            if (claims != null) {
               Claim claim = claims.findClaimByCenter(this.centerX, this.centerZ);
               if (claim != null) {
                  if (!this.plugin.isOpBypass(actor) && !claim.getOwner().equals(actor)) {
                     this.plugin.sendPlayerMessage(playerRef, "Solo el propietario puede activar o desactivar la proteccion");
                  } else {
                     long nowMs = System.currentTimeMillis();
                     if (claim.isProtectionPaused()) {
                        boolean resumed = claim.resumeProtection(nowMs);
                        if (!resumed) {
                           this.plugin.sendPlayerMessage(playerRef, "Sin tiempo disponible para activar la proteccion");
                        } else {
                           this.plugin.sendPlayerMessage(playerRef, "Proteccion activada");
                        }
                     } else {
                        claim.pauseProtection(nowMs);
                        this.plugin.sendPlayerMessage(playerRef, "Proteccion pausada");
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
