package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TotemProtecaoEnterExitTickSystem extends EntityTickingSystem<EntityStore> {
   private static final long CHECK_COOLDOWN_MS = 250L;
   private final TotemProtecaoPlugin plugin;
   private final ConcurrentHashMap<UUID, Long> lastCheckMsByPlayer = new ConcurrentHashMap();

   public TotemProtecaoEnterExitTickSystem(TotemProtecaoPlugin plugin) {
      this.plugin = plugin;
   }

   public Query<EntityStore> getQuery() {
      return Query.and(new Query[] { PlayerRef.getComponentType() });
   }

   public void tick(float deltaSeconds, int entityIndex, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
         CommandBuffer<EntityStore> commandBuffer) {
      if (this.plugin != null && chunk != null) {
         PlayerRef player = (PlayerRef) chunk.getComponent(entityIndex, PlayerRef.getComponentType());
         if (player != null) {
            UUID uuid = player.getUuid();
            if (uuid != null) {
               long nowMs = System.currentTimeMillis();
               Long lastCheck = (Long) this.lastCheckMsByPlayer.get(uuid);
               if (lastCheck == null || nowMs - lastCheck >= 250L) {
                  this.lastCheckMsByPlayer.put(uuid, nowMs);

                  Transform t;
                  try {
                     t = player.getTransform();
                  } catch (Exception var22) {
                     t = null;
                  }

                  if (t != null && t.getPosition() != null) {
                     Vector3d pos = t.getPosition();

                     try {
                        this.plugin.rememberUsername(uuid, player.getUsername());
                     } catch (Exception var21) {
                     }

                     int x = (int) Math.floor(pos.x);
                     int z = (int) Math.floor(pos.z);
                     Claim claim = this.plugin.getClaimStore().findClaimAt(x, z);
                     if (claim != null && !claim.isProtectionActive(nowMs)) {
                        claim = null;
                     }
                     Long prev = this.plugin.getLastZoneKey(uuid);
                     Long now = null;
                     if (claim != null) {
                        now = TotemProtecaoPlugin.centerKey(claim.getCenterX(), claim.getCenterZ());
                     }

                     if (prev != null || now != null) {
                        boolean changed = prev == null ? now != null : !prev.equals(now);
                        if (changed) {
                           try {
                              this.plugin.updatePlayerClaimMembership(uuid, prev, now, player.getUsername());
                           } catch (Exception var20) {
                              this.plugin.updatePlayerClaimMembership(uuid, prev, now, (String) null);
                           }

                           this.plugin.setLastZoneKey(uuid, now);
                           if (prev != null && now == null) {
                              Claim prevClaim = resolveClaim(this.plugin, prev);
                              String owner = resolveOwnerName(this.plugin, prevClaim);
                              this.plugin.sendPlayerMessage(player,
                                    "You left a protected area (owner: " + owner + ")");
                           } else {
                              String owner;
                              if (prev == null && now != null) {
                                 owner = resolveOwnerName(this.plugin, claim);
                                 this.plugin.sendPlayerMessage(player,
                                       "You entered a protected area (owner: " + owner + ")");
                              } else {
                                 owner = resolveOwnerName(this.plugin, claim);
                                 this.plugin.sendPlayerMessage(player,
                                       "You entered a protected area (owner: " + owner + ")");
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

   private static String resolveOwnerName(TotemProtecaoPlugin plugin, Claim claim) {
      if (claim == null) {
         return "?";
      } else {
         String n = claim.getOwnerName();
         if (n != null && !n.isEmpty()) {
            return n;
         } else {
            if (plugin != null && claim.getOwner() != null) {
               String cached = plugin.getKnownUsername(claim.getOwner());
               if (cached != null && !cached.isEmpty()) {
                  return cached;
               }
            }

            return claim.getOwner() == null ? "?" : claim.getOwner().toString();
         }
      }
   }

   private static Claim resolveClaim(TotemProtecaoPlugin plugin, long centerKey) {
      if (plugin == null) {
         return null;
      } else {
         int centerX = (int) (centerKey >> 32);
         int centerZ = (int) centerKey;
         return plugin.getClaimStore().findClaimByCenter(centerX, centerZ);
      }
   }
}
