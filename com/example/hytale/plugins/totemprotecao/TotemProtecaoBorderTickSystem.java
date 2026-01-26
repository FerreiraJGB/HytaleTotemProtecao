package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.UUID;

public final class TotemProtecaoBorderTickSystem extends EntityTickingSystem<EntityStore> {
   private static final long SPAWN_COOLDOWN_MS = 650L;
   private static final int STEP = 2;
   private static final String PARTICLE_SYSTEM_ID = "Impact_Critical";
   private static final float PARTICLE_SCALE = 0.25F;
   private static final Color PARTICLE_COLOR = new Color((byte) 0, (byte) 96, (byte) 127);
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoBorderTickSystem(TotemProtecaoPlugin plugin) {
      this.plugin = plugin;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   public void tick(float deltaSeconds, int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer) {
      if (this.plugin != null && chunk != null && store != null) {
         PlayerRef player = (PlayerRef) chunk.getComponent(entityIndex, PlayerRef.getComponentType());
         if (player != null) {
            UUID uuid = player.getUuid();
            if (uuid != null) {
               Long centerKey = this.plugin.getBorderCenterKey(uuid);
               if (centerKey != null) {
                  long now = System.currentTimeMillis();
                  if (this.plugin.shouldSpawnBorderNow(uuid, now, 650L)) {
                     int centerX = (int) (centerKey >> 32);
                     int centerZ = (int) (long) centerKey;
                     Claim claim = this.plugin.getClaimStore().findClaimByCenter(centerX, centerZ);
                     if (claim == null || !claim.isProtectionActive(now)) {
                        this.plugin.disableBorder(uuid);
                     } else {
                        Transform t;
                        try {
                           t = player.getTransform();
                        } catch (Exception var25) {
                           t = null;
                        }

                        if (t != null && t.getPosition() != null) {
                           Vector3d p = t.getPosition();
                           double yBase;
                           if (claim.getCenterY() != Integer.MIN_VALUE) {
                              yBase = (double) claim.getCenterY() + 0.15D;
                           } else {
                              yBase = Math.floor(p.y) + 0.15D;
                           }

                           Ref<EntityStore> ref = player.getReference();
                           if (ref != null && ref.isValid()) {
                              int r = claim.getRadius();
                              int minX = centerX - r;
                              int maxX = centerX + r;
                              int minZ = centerZ - r;
                              int maxZ = centerZ + r;

                              int z;
                              for (z = minX; z <= maxX; z += 2) {
                                 spawnPoint(store, ref, z, yBase, minZ);
                                 spawnPoint(store, ref, z, yBase, maxZ);
                              }

                              for (z = minZ; z <= maxZ; z += 2) {
                                 spawnPoint(store, ref, minX, yBase, z);
                                 spawnPoint(store, ref, maxX, yBase, z);
                              }

                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void spawnPoint(Store<EntityStore> store, Ref<EntityStore> ref, int x, double y, int z) {
      Vector3d pos = new Vector3d((double) x + 0.5D, y, (double) z + 0.5D);
      ParticleUtil.spawnParticleEffect("Impact_Critical", pos, 0.0F, 0.0F, 0.0F, 0.25F, PARTICLE_COLOR,
            Collections.singletonList(ref), store);
   }
}
