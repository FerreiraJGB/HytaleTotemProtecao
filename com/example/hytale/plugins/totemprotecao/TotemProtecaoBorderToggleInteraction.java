package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.UUID;

public final class TotemProtecaoBorderToggleInteraction extends ChoiceInteraction {
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;

   public TotemProtecaoBorderToggleInteraction(TotemProtecaoPlugin plugin, int centerX, int centerZ) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
   }

   public void run(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
      if (this.plugin != null && store != null && ref != null && playerRef != null) {
         UUID actor = playerRef.getUuid();
         if (actor != null) {
            Claim claim = this.plugin.getClaimStore().findClaimByCenter(this.centerX, this.centerZ);
            if (claim != null) {
               if (!claim.isProtectionActive(System.currentTimeMillis())) {
                  this.plugin.sendPlayerMessageImmediate(playerRef, "Borde: proteccion expirada");
                  return;
               }

               long key = TotemProtecaoPlugin.centerKey(this.centerX, this.centerZ);
               boolean currentlyOn = this.plugin.isBorderEnabled(actor, key);
               if (currentlyOn) {
                  this.plugin.disableBorder(actor);
                  this.plugin.sendPlayerMessageImmediate(playerRef, "Borde: DESACTIVADA");
               } else {
                  this.plugin.enableBorder(actor, this.centerX, this.centerZ);
                  this.plugin.sendPlayerMessageImmediate(playerRef, "Borde: ACTIVADA");

                  try {
                     Transform t = playerRef.getTransform();
                     if (t != null && t.getPosition() != null) {
                        Vector3d pos = t.getPosition();
                        ParticleUtil.spawnParticleEffect("Impact_Critical", new Vector3d(pos.x, pos.y + 1.0D, pos.z),
                              Collections.singletonList(playerRef.getReference()), store);
                     }
                  } catch (Exception var11) {
                  }
               }

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
