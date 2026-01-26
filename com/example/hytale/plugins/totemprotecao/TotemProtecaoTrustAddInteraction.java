package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;

public final class TotemProtecaoTrustAddInteraction extends ChoiceInteraction {
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;
   private final UUID target;
   private final String username;

   public TotemProtecaoTrustAddInteraction(TotemProtecaoPlugin plugin, int centerX, int centerZ, UUID target,
         String username) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.target = target;
      this.username = username;
   }

   public void run(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
      if (this.plugin != null && store != null && ref != null && playerRef != null && this.target != null) {
         ClaimStore claims = this.plugin.getClaimStore();
         if (claims != null) {
            Claim claim = claims.findClaimByCenter(this.centerX, this.centerZ);
            if (claim != null) {
               UUID actor = playerRef.getUuid();
               if (actor != null) {
                  if (!this.plugin.isOpBypass(actor) && !claim.getOwner().equals(actor)) {
                     this.plugin.sendPlayerMessage(playerRef, "Somente o dono pode adicionar amigos");
                  } else if (this.target.equals(claim.getOwner())) {
                     this.plugin.sendPlayerMessage(playerRef, "O dono ja tem acesso total");
                  } else {
                     if (this.username != null && !this.username.isEmpty()) {
                        this.plugin.rememberUsername(this.target, this.username);
                     }

                     claim.setTrusted(this.target, 7);
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
