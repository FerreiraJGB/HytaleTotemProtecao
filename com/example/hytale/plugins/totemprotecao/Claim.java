package com.example.hytale.plugins.totemprotecao;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Claim {
   public static final int PERM_PLACE = 1;
   public static final int PERM_BREAK = 2;
   public static final int PERM_USE = 4;
   public static final int PERM_ALL = 7;
   public static final int UNKNOWN_Y = Integer.MIN_VALUE;
   private final UUID owner;
   private final String ownerName;
   private final int centerX;
   private final int centerY;
   private final int centerZ;
   private final int radius;
   private long protectionEndsAtMs;
   private long protectionPausedRemainingMs;
   private boolean protectionPaused;
   private final Map<UUID, Integer> trusted = new HashMap();

   public Claim(UUID owner, int centerX, int centerZ, int radius) {
      this.owner = owner;
      this.ownerName = null;
      this.centerX = centerX;
      this.centerY = Integer.MIN_VALUE;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
   }

   public Claim(UUID owner, int centerX, int centerY, int centerZ, int radius) {
      this.owner = owner;
      this.ownerName = null;
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
   }

   public Claim(UUID owner, String ownerName, int centerX, int centerZ, int radius) {
      this.owner = owner;
      this.ownerName = ownerName;
      this.centerX = centerX;
      this.centerY = Integer.MIN_VALUE;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
   }

   public Claim(UUID owner, String ownerName, int centerX, int centerY, int centerZ, int radius) {
      this.owner = owner;
      this.ownerName = ownerName;
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
   }

   public Claim(UUID owner, int centerX, int centerZ, int radius, Map<UUID, Integer> trusted) {
      this.owner = owner;
      this.ownerName = null;
      this.centerX = centerX;
      this.centerY = Integer.MIN_VALUE;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
      if (trusted != null && !trusted.isEmpty()) {
         this.trusted.putAll(trusted);
      }

   }

   public Claim(UUID owner, int centerX, int centerY, int centerZ, int radius, Map<UUID, Integer> trusted) {
      this.owner = owner;
      this.ownerName = null;
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
      if (trusted != null && !trusted.isEmpty()) {
         this.trusted.putAll(trusted);
      }

   }

   public Claim(UUID owner, String ownerName, int centerX, int centerZ, int radius, Map<UUID, Integer> trusted) {
      this.owner = owner;
      this.ownerName = ownerName;
      this.centerX = centerX;
      this.centerY = Integer.MIN_VALUE;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
      if (trusted != null && !trusted.isEmpty()) {
         this.trusted.putAll(trusted);
      }

   }

   public Claim(UUID owner, String ownerName, int centerX, int centerY, int centerZ, int radius,
         Map<UUID, Integer> trusted) {
      this.owner = owner;
      this.ownerName = ownerName;
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = 0L;
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
      if (trusted != null && !trusted.isEmpty()) {
         this.trusted.putAll(trusted);
      }

   }

   public Claim(UUID owner, String ownerName, int centerX, int centerY, int centerZ, int radius,
         Map<UUID, Integer> trusted, long protectionEndsAtMs) {
      this.owner = owner;
      this.ownerName = ownerName;
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = Math.max(0L, protectionEndsAtMs);
      this.protectionPausedRemainingMs = 0L;
      this.protectionPaused = false;
      if (trusted != null && !trusted.isEmpty()) {
         this.trusted.putAll(trusted);
      }

   }

   public Claim(UUID owner, String ownerName, int centerX, int centerY, int centerZ, int radius,
         Map<UUID, Integer> trusted, long protectionEndsAtMs, boolean protectionPaused,
         long protectionPausedRemainingMs) {
      this.owner = owner;
      this.ownerName = ownerName;
      this.centerX = centerX;
      this.centerY = centerY;
      this.centerZ = centerZ;
      this.radius = radius;
      this.protectionEndsAtMs = Math.max(0L, protectionEndsAtMs);
      this.protectionPaused = protectionPaused;
      this.protectionPausedRemainingMs = Math.max(0L, protectionPausedRemainingMs);
      if (trusted != null && !trusted.isEmpty()) {
         this.trusted.putAll(trusted);
      }

   }

   public UUID getOwner() {
      return this.owner;
   }

   public String getOwnerName() {
      return this.ownerName;
   }

   public int getCenterX() {
      return this.centerX;
   }

   public int getCenterY() {
      return this.centerY;
   }

   public int getCenterZ() {
      return this.centerZ;
   }

   public int getRadius() {
      return this.radius;
   }

   public synchronized long getProtectionEndsAtMs() {
      return this.protectionEndsAtMs;
   }

   public synchronized void setProtectionEndsAtMs(long protectionEndsAtMs) {
      this.protectionEndsAtMs = Math.max(0L, protectionEndsAtMs);
      if (this.protectionEndsAtMs > 0L) {
         this.protectionPaused = false;
         this.protectionPausedRemainingMs = 0L;
      }
   }

   public synchronized boolean isProtectionPaused() {
      return this.protectionPaused;
   }

   public synchronized long getProtectionPausedRemainingMs() {
      return this.protectionPausedRemainingMs;
   }

   public synchronized void pauseProtection(long nowMs) {
      if (!this.protectionPaused) {
         long remaining = this.getProtectionRemainingMs(nowMs);
         this.protectionPausedRemainingMs = remaining;
         this.protectionEndsAtMs = 0L;
         this.protectionPaused = true;
      }
   }

   public synchronized boolean resumeProtection(long nowMs) {
      if (this.protectionPaused) {
         if (this.protectionPausedRemainingMs <= 0L) {
            return false;
         }

         this.protectionEndsAtMs = nowMs + this.protectionPausedRemainingMs;
         this.protectionPausedRemainingMs = 0L;
         this.protectionPaused = false;
      }

      return true;
   }

   public synchronized long getProtectionRemainingMs(long nowMs) {
      if (this.protectionPaused) {
         return this.protectionPausedRemainingMs > 0L ? this.protectionPausedRemainingMs : 0L;
      } else {
         if (this.protectionEndsAtMs <= 0L) {
            return 0L;
         } else {
            long remaining = this.protectionEndsAtMs - nowMs;
            return remaining > 0L ? remaining : 0L;
         }
      }
   }

   public synchronized boolean isProtectionActive(long nowMs) {
      return !this.protectionPaused && this.getProtectionRemainingMs(nowMs) > 0L;
   }

   public synchronized long addProtectionMs(long nowMs, long addMs) {
      if (addMs <= 0L) {
         return this.getProtectionRemainingMs(nowMs);
      } else {
         if (this.protectionPaused) {
            this.protectionPausedRemainingMs += addMs;
            return this.protectionPausedRemainingMs;
         } else {
            long base = this.protectionEndsAtMs > nowMs ? this.protectionEndsAtMs : nowMs;
            this.protectionEndsAtMs = base + addMs;
            return this.protectionEndsAtMs - nowMs;
         }
      }
   }

   public synchronized void setProtectionRemainingMs(long nowMs, long remainingMs, boolean paused) {
      long normalized = Math.max(0L, remainingMs);
      if (paused) {
         this.protectionPaused = true;
         this.protectionPausedRemainingMs = normalized;
         this.protectionEndsAtMs = 0L;
      } else {
         this.protectionPaused = false;
         this.protectionPausedRemainingMs = 0L;
         this.protectionEndsAtMs = normalized > 0L ? nowMs + normalized : 0L;
      }
   }

   public synchronized Map<UUID, Integer> getTrusted() {
      return Collections.unmodifiableMap(new HashMap(this.trusted));
   }

   public synchronized int getPermissionsFor(UUID player) {
      if (player == null) {
         return 0;
      } else if (this.owner.equals(player)) {
         return 7;
      } else {
         Integer p = (Integer) this.trusted.get(player);
         return p == null ? 0 : p;
      }
   }

   public synchronized boolean hasPermission(UUID player, int perm) {
      return (this.getPermissionsFor(player) & perm) != 0;
   }

   public synchronized void setTrusted(UUID player, int perms) {
      if (player != null && !this.owner.equals(player)) {
         int p = perms & 7;
         if (p == 0) {
            this.trusted.remove(player);
         } else {
            this.trusted.put(player, p);
         }
      }
   }

   public synchronized void removeTrusted(UUID player) {
      if (player != null) {
         this.trusted.remove(player);
      }
   }

   public boolean contains(int x, int z) {
      long dx = Math.abs((long) x - (long) this.centerX);
      long dz = Math.abs((long) z - (long) this.centerZ);
      long r = (long) this.radius;
      return dx <= r && dz <= r;
   }
}
