package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;

public final class ClaimStore {
   private final Path file;
   private final HytaleLogger logger;
   private final List<Claim> claims = new ArrayList();
   private final Map<Long, List<Claim>> claimsByChunk = new HashMap();
   private final Map<Long, Claim> claimsByCenter = new HashMap();
   private int maxChunkRadius;
   private boolean dirty;

   public ClaimStore(Path file, HytaleLogger logger) {
      this.file = file;
      this.logger = logger;
   }

   public synchronized List<Claim> getClaims() {
      return Collections.unmodifiableList(new ArrayList(this.claims));
   }

   public synchronized int countClaimsForOwner(UUID owner) {
      if (owner == null) {
         return 0;
      } else {
         int count = 0;
         Iterator var3 = this.claims.iterator();

         while (var3.hasNext()) {
            Claim c = (Claim) var3.next();
            if (owner.equals(c.getOwner())) {
               ++count;
            }
         }

         return count;
      }
   }

   public synchronized Claim findClaimAt(int x, int z) {
      int chunkX = floorDiv(x, 16);
      int chunkZ = floorDiv(z, 16);
      Claim found = null;
      int range = Math.max(0, this.maxChunkRadius);

      for (int dx = -range; dx <= range; ++dx) {
         for (int dz = -range; dz <= range; ++dz) {
            List<Claim> bucket = (List) this.claimsByChunk.get(chunkKey(chunkX + dx, chunkZ + dz));
            if (bucket != null) {
               Iterator var10 = bucket.iterator();

               while (var10.hasNext()) {
                  Claim c = (Claim) var10.next();
                  if (c.contains(x, z)) {
                     found = c;
                     break;
                  }
               }

               if (found != null) {
                  break;
               }
            }
         }

         if (found != null) {
            break;
         }
      }

      return found;
   }

   public synchronized Claim findClaimByCenter(int centerX, int centerZ) {
      return (Claim) this.claimsByCenter.get(centerKey(centerX, centerZ));
   }

   public synchronized boolean updateClaimCenterYIfUnknown(int centerX, int centerZ, int centerY) {
      Claim existing = (Claim) this.claimsByCenter.get(centerKey(centerX, centerZ));
      if (existing == null) {
         return false;
      } else if (existing.getCenterY() != Integer.MIN_VALUE) {
         return false;
      } else {
         Claim updated = new Claim(existing.getOwner(), existing.getOwnerName(), existing.getCenterX(), centerY,
               existing.getCenterZ(), existing.getRadius(), existing.getTrusted(), existing.getProtectionEndsAtMs(),
               existing.isProtectionPaused(), existing.getProtectionPausedRemainingMs());
         this.claimsByCenter.put(centerKey(centerX, centerZ), updated);

         for (int i = 0; i < this.claims.size(); ++i) {
            if (this.claims.get(i) == existing) {
               this.claims.set(i, updated);
               break;
            }
         }

         long bucketKey = chunkKey(floorDiv(centerX, 16), floorDiv(centerZ, 16));
         List<Claim> bucket = (List) this.claimsByChunk.get(bucketKey);
         if (bucket != null) {
            for (int i = 0; i < bucket.size(); ++i) {
               if (bucket.get(i) == existing) {
                  bucket.set(i, updated);
                  break;
               }
            }
         }

         this.dirty = true;
         return true;
      }
   }

   public synchronized boolean updateClaimCenterY(int centerX, int centerZ, int centerY) {
      Claim existing = (Claim) this.claimsByCenter.get(centerKey(centerX, centerZ));
      if (existing == null) {
         return false;
      } else if (existing.getCenterY() == centerY) {
         return false;
      } else {
         Claim updated = new Claim(existing.getOwner(), existing.getOwnerName(), existing.getCenterX(), centerY,
               existing.getCenterZ(), existing.getRadius(), existing.getTrusted(), existing.getProtectionEndsAtMs(),
               existing.isProtectionPaused(), existing.getProtectionPausedRemainingMs());
         this.claimsByCenter.put(centerKey(centerX, centerZ), updated);

         for (int i = 0; i < this.claims.size(); ++i) {
            if (this.claims.get(i) == existing) {
               this.claims.set(i, updated);
               break;
            }
         }

         long bucketKey = chunkKey(floorDiv(centerX, 16), floorDiv(centerZ, 16));
         List<Claim> bucket = (List) this.claimsByChunk.get(bucketKey);
         if (bucket != null) {
            for (int i = 0; i < bucket.size(); ++i) {
               if (bucket.get(i) == existing) {
                  bucket.set(i, updated);
                  break;
               }
            }
         }

         this.dirty = true;
         return true;
      }
   }

   public synchronized void markDirty() {
      this.dirty = true;
   }

   public synchronized boolean intersectsAny(int centerX, int centerZ, int radius) {
      int chunkX = floorDiv(centerX, 16);
      int chunkZ = floorDiv(centerZ, 16);
      int range = Math.max(0, this.maxChunkRadius + chunkRadiusFor(radius));

      for (int dx = -range; dx <= range; ++dx) {
         for (int dz = -range; dz <= range; ++dz) {
            List<Claim> bucket = (List) this.claimsByChunk.get(chunkKey(chunkX + dx, chunkZ + dz));
            if (bucket != null) {
               Iterator var10 = bucket.iterator();

               while (var10.hasNext()) {
                  Claim c = (Claim) var10.next();
                  if (intersects(c, centerX, centerZ, radius)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   public synchronized boolean addClaim(Claim claim) {
      if (this.claimsByCenter.containsKey(centerKey(claim.getCenterX(), claim.getCenterZ()))) {
         return false;
      } else {
         this.claims.add(claim);
         this.claimsByCenter.put(centerKey(claim.getCenterX(), claim.getCenterZ()), claim);
         long bucketKey = chunkKey(floorDiv(claim.getCenterX(), 16), floorDiv(claim.getCenterZ(), 16));
         ((List) this.claimsByChunk.computeIfAbsent(bucketKey, (k) -> {
            return new ArrayList();
         })).add(claim);
         int claimChunkRadius = chunkRadiusFor(claim.getRadius());
         if (claimChunkRadius > this.maxChunkRadius) {
            this.maxChunkRadius = claimChunkRadius;
         }

         this.dirty = true;
         return true;
      }
   }

   private static boolean intersects(Claim existing, int centerX, int centerZ, int radius) {
      long dx = Math.abs((long) existing.getCenterX() - (long) centerX);
      long dz = Math.abs((long) existing.getCenterZ() - (long) centerZ);
      long sum = (long) existing.getRadius() + (long) radius;
      return dx <= sum && dz <= sum;
   }

   public synchronized boolean removeClaimAt(int x, int z) {
      long cKey = centerKey(x, z);
      Claim claim = (Claim) this.claimsByCenter.remove(cKey);
      if (claim == null) {
         return false;
      } else {
         int removedChunkRadius = chunkRadiusFor(claim.getRadius());

         for (int i = 0; i < this.claims.size(); ++i) {
            Claim c = (Claim) this.claims.get(i);
            if (c == claim) {
               this.claims.remove(i);
               break;
            }
         }

         long bucketKey = chunkKey(floorDiv(claim.getCenterX(), 16), floorDiv(claim.getCenterZ(), 16));
         List<Claim> bucket = (List) this.claimsByChunk.get(bucketKey);
         if (bucket != null) {
            bucket.remove(claim);
            if (bucket.isEmpty()) {
               this.claimsByChunk.remove(bucketKey);
            }
         }

         if (removedChunkRadius >= this.maxChunkRadius) {
            this.recomputeMaxChunkRadius();
         }

         this.dirty = true;
         return true;
      }
   }

   public synchronized void load() {
      this.claims.clear();
      this.claimsByChunk.clear();
      this.claimsByCenter.clear();
      this.maxChunkRadius = 0;
      this.dirty = false;
      if (Files.exists(this.file, new LinkOption[0])) {
         try {
            String json = Files.readString(this.file, StandardCharsets.UTF_8).trim();
            if (json.isEmpty()) {
               return;
            }

            if (!json.startsWith("[") || !json.endsWith("]")) {
               this.logger.at(Level.WARNING).log("TotemProtecao claims.json invalid format, ignoring");
               return;
            }

            String body = json.substring(1, json.length() - 1).trim();
            if (body.isEmpty()) {
               return;
            }

            int end;
            for (int idx = 0; idx < body.length(); idx = end + 1) {
               int start = body.indexOf(123, idx);
               if (start < 0) {
                  break;
               }

               int depth = 0;
               end = -1;

               for (int i = start; i < body.length(); ++i) {
                  char ch = body.charAt(i);
                  if (ch == '{') {
                     ++depth;
                  } else if (ch == '}') {
                     --depth;
                     if (depth == 0) {
                        end = i;
                        break;
                     }
                  }
               }

               if (end < 0) {
                  break;
               }

               String obj = body.substring(start + 1, end);
               Claim claim = this.parseClaim(obj);
               if (claim != null) {
                  this.claims.add(claim);
                  this.claimsByCenter.put(centerKey(claim.getCenterX(), claim.getCenterZ()), claim);
                  long bucketKey = chunkKey(floorDiv(claim.getCenterX(), 16), floorDiv(claim.getCenterZ(), 16));
                  ((List) this.claimsByChunk.computeIfAbsent(bucketKey, (k) -> {
                     return new ArrayList();
                  })).add(claim);
                  int claimChunkRadius = chunkRadiusFor(claim.getRadius());
                  if (claimChunkRadius > this.maxChunkRadius) {
                     this.maxChunkRadius = claimChunkRadius;
                  }
               }
            }
         } catch (IOException var12) {
            ((Api) this.logger.at(Level.WARNING).withCause(var12)).log("TotemProtecao failed to load claims.json");
         }

      }
   }

   private Claim parseClaim(String obj) {
      try {
         String ownerStr = readJsonString(obj, "owner");
         String ownerName = readJsonString(obj, "ownerName");
         Integer x = readJsonInt(obj, "x");
         Integer y = readJsonInt(obj, "y");
         Integer z = readJsonInt(obj, "z");
         Integer r = readJsonInt(obj, "r");
         Long protectionEndsAtMs = readJsonLong(obj, "protectionEndsAtMs");
         Boolean protectionPaused = readJsonBoolean(obj, "protectionPaused");
         Long protectionPausedRemainingMs = readJsonLong(obj, "protectionPausedRemainingMs");
         if (ownerStr != null && x != null && z != null && r != null) {
            UUID owner = UUID.fromString(ownerStr);
            Map<UUID, Integer> trusted = readTrusted(obj);
            int cy = y == null ? Integer.MIN_VALUE : y;
            long endsAtMs = protectionEndsAtMs == null ? 0L : Math.max(0L, protectionEndsAtMs);
            boolean paused = protectionPaused != null && protectionPaused;
            long pausedRemaining = protectionPausedRemainingMs == null ? 0L : Math.max(0L, protectionPausedRemainingMs);
            return new Claim(owner, ownerName, x, cy, z, r, trusted, endsAtMs, paused, pausedRemaining);
         } else {
            return null;
         }
      } catch (Exception var11) {
         return null;
      }
   }

   private static Map<UUID, Integer> readTrusted(String obj) {
      String key = "\"trusted\"";
      int k = obj.indexOf(key);
      if (k < 0) {
         return Collections.emptyMap();
      } else {
         int colon = obj.indexOf(58, k + key.length());
         if (colon < 0) {
            return Collections.emptyMap();
         } else {
            int start = obj.indexOf(123, colon + 1);
            if (start < 0) {
               return Collections.emptyMap();
            } else {
               int depth = 0;
               int end = -1;

               for (int i = start; i < obj.length(); ++i) {
                  char ch = obj.charAt(i);
                  if (ch == '{') {
                     ++depth;
                  } else if (ch == '}') {
                     --depth;
                     if (depth == 0) {
                        end = i;
                        break;
                     }
                  }
               }

               if (end < 0) {
                  return Collections.emptyMap();
               } else {
                  String body = obj.substring(start + 1, end).trim();
                  if (body.isEmpty()) {
                     return Collections.emptyMap();
                  } else {
                     Map<UUID, Integer> map = new HashMap();

                     int comma;
                     for (int idx = 0; idx < body.length(); idx = comma + 1) {
                        int q1 = body.indexOf(34, idx);
                        if (q1 < 0) {
                           break;
                        }

                        int q2 = body.indexOf(34, q1 + 1);
                        if (q2 < 0) {
                           break;
                        }

                        String uuidStr = body.substring(q1 + 1, q2);
                        int c = body.indexOf(58, q2 + 1);
                        if (c < 0) {
                           break;
                        }

                        int i;
                        for (i = c + 1; i < body.length() && Character.isWhitespace(body.charAt(i)); ++i) {
                        }

                        int j;
                        for (j = i; j < body.length()
                              && (body.charAt(j) == '-' || Character.isDigit(body.charAt(j))); ++j) {
                        }

                        if (j == i) {
                           break;
                        }

                        try {
                           UUID u = UUID.fromString(uuidStr);
                           int p = Integer.parseInt(body.substring(i, j));
                           if (p != 0) {
                              map.put(u, p);
                           }
                        } catch (IllegalArgumentException var18) {
                        }

                        comma = body.indexOf(44, j);
                        if (comma < 0) {
                           break;
                        }
                     }

                     return (Map) (map.isEmpty() ? Collections.emptyMap() : map);
                  }
               }
            }
         }
      }
   }

   private static String readJsonString(String obj, String key) {
      String pattern = "\"" + key + "\"";
      int k = obj.indexOf(pattern);
      if (k < 0) {
         return null;
      } else {
         int colon = obj.indexOf(58, k + pattern.length());
         if (colon < 0) {
            return null;
         } else {
            int firstQuote = obj.indexOf(34, colon + 1);
            if (firstQuote < 0) {
               return null;
            } else {
               int secondQuote = obj.indexOf(34, firstQuote + 1);
               return secondQuote < 0 ? null : obj.substring(firstQuote + 1, secondQuote);
            }
         }
      }
   }

   private static Integer readJsonInt(String obj, String key) {
      String pattern = "\"" + key + "\"";
      int k = obj.indexOf(pattern);
      if (k < 0) {
         return null;
      } else {
         int colon = obj.indexOf(58, k + pattern.length());
         if (colon < 0) {
            return null;
         } else {
            int i;
            for (i = colon + 1; i < obj.length() && Character.isWhitespace(obj.charAt(i)); ++i) {
            }

            int j;
            for (j = i; j < obj.length() && (obj.charAt(j) == '-' || Character.isDigit(obj.charAt(j))); ++j) {
            }

            if (j == i) {
               return null;
            } else {
               int sign = 1;
               int idx = i;
               if (obj.charAt(i) == '-') {
                  sign = -1;
                  idx = i + 1;
                  if (idx >= j) {
                     return null;
                  }
               }

               int value;
               for (value = 0; idx < j; ++idx) {
                  char ch = obj.charAt(idx);
                  if (!Character.isDigit(ch)) {
                     return null;
                  }

                  value = value * 10 + (ch - 48);
               }

               return sign * value;
            }
         }
      }
   }

   private static Boolean readJsonBoolean(String obj, String key) {
      String pattern = "\"" + key + "\"";
      int k = obj.indexOf(pattern);
      if (k < 0) {
         return null;
      } else {
         int colon = obj.indexOf(58, k + pattern.length());
         if (colon < 0) {
            return null;
         } else {
            int i;
            for (i = colon + 1; i < obj.length() && Character.isWhitespace(obj.charAt(i)); ++i) {
            }

            if (i >= obj.length()) {
               return null;
            } else if (obj.startsWith("true", i)) {
               return Boolean.TRUE;
            } else {
               return obj.startsWith("false", i) ? Boolean.FALSE : null;
            }
         }
      }
   }

   private static Long readJsonLong(String obj, String key) {
      String pattern = "\"" + key + "\"";
      int k = obj.indexOf(pattern);
      if (k < 0) {
         return null;
      } else {
         int colon = obj.indexOf(58, k + pattern.length());
         if (colon < 0) {
            return null;
         } else {
            int i;
            for (i = colon + 1; i < obj.length() && Character.isWhitespace(obj.charAt(i)); ++i) {
            }

            int j;
            for (j = i; j < obj.length() && (obj.charAt(j) == '-' || Character.isDigit(obj.charAt(j))); ++j) {
            }

            if (j == i) {
               return null;
            } else {
               int sign = 1;
               int idx = i;
               if (obj.charAt(i) == '-') {
                  sign = -1;
                  idx = i + 1;
                  if (idx >= j) {
                     return null;
                  }
               }

               long value;
               for (value = 0L; idx < j; ++idx) {
                  char ch = obj.charAt(idx);
                  if (!Character.isDigit(ch)) {
                     return null;
                  }

                  value = value * 10L + (long) (ch - 48);
               }

               return (long) sign * value;
            }
         }
      }
   }

   public synchronized void flushIfDirty() {
      if (this.dirty) {
         this.saveNow();
      }
   }

   public synchronized void saveNow() {
      try {
         Files.createDirectories(this.file.getParent());
         Api var10000 = this.logger.at(Level.INFO);
         String var10001 = String.valueOf(this.file.toAbsolutePath().normalize());
         var10000.log("TotemProtecao saving claims file=" + var10001 + " count=" + this.claims.size());
         StringBuilder sb = new StringBuilder();
         sb.append('[');

         for (int i = 0; i < this.claims.size(); ++i) {
            Claim c = (Claim) this.claims.get(i);
            if (i > 0) {
               sb.append(',');
            }

            sb.append('{');
            sb.append("\"owner\":\"").append(c.getOwner()).append("\"");
            if (c.getOwnerName() != null && !c.getOwnerName().isEmpty()) {
               sb.append(',');
               sb.append("\"ownerName\":\"").append(c.getOwnerName()).append("\"");
            }

            sb.append(',');
            sb.append("\"x\":").append(c.getCenterX());
            if (c.getCenterY() != Integer.MIN_VALUE) {
               sb.append(',');
               sb.append("\"y\":").append(c.getCenterY());
            }

            sb.append(',');
            sb.append("\"z\":").append(c.getCenterZ());
            sb.append(',');
            sb.append("\"r\":").append(c.getRadius());
            long endsAtMs = c.getProtectionEndsAtMs();
            if (endsAtMs > 0L) {
               sb.append(',');
               sb.append("\"protectionEndsAtMs\":").append(endsAtMs);
            }
            boolean paused = c.isProtectionPaused();
            long pausedRemaining = c.getProtectionPausedRemainingMs();
            if (paused) {
               sb.append(',');
               sb.append("\"protectionPaused\":true");
            }
            if (pausedRemaining > 0L) {
               sb.append(',');
               sb.append("\"protectionPausedRemainingMs\":").append(pausedRemaining);
            }
            Map<UUID, Integer> trusted = c.getTrusted();
            if (!trusted.isEmpty()) {
               sb.append(',');
               sb.append("\"trusted\":{");
               int ti = 0;
               Iterator var6 = trusted.entrySet().iterator();

               while (var6.hasNext()) {
                  Entry<UUID, Integer> e = (Entry) var6.next();
                  if (e.getKey() != null && e.getValue() != null) {
                     int p = (Integer) e.getValue();
                     if (p != 0) {
                        if (ti > 0) {
                           sb.append(',');
                        }

                        sb.append('"').append(e.getKey()).append('"').append(':').append(p);
                        ++ti;
                     }
                  }
               }

               sb.append('}');
            }

            sb.append('}');
         }

         sb.append(']');
         Files.writeString(this.file, sb.toString(), StandardCharsets.UTF_8, new OpenOption[0]);
         this.dirty = false;
      } catch (IOException var9) {
         ((Api) this.logger.at(Level.WARNING).withCause(var9)).log("TotemProtecao failed to save claims.json");
      }

   }

   private static long centerKey(int x, int z) {
      return (long) x << 32 ^ (long) z & 4294967295L;
   }

   private static long chunkKey(int chunkX, int chunkZ) {
      return (long) chunkX << 32 ^ (long) chunkZ & 4294967295L;
   }

   private static int floorDiv(int x, int d) {
      int r = x / d;
      if ((x ^ d) < 0 && r * d != x) {
         --r;
      }

      return r;
   }

   private static int chunkRadiusFor(int radiusBlocks) {
      if (radiusBlocks <= 0) {
         return 0;
      } else {
         int chunks = (radiusBlocks + 15) / 16;
         return Math.max(0, chunks);
      }
   }

   private void recomputeMaxChunkRadius() {
      int max = 0;
      Iterator var2 = this.claims.iterator();

      while (var2.hasNext()) {
         Claim c = (Claim) var2.next();
         int cr = chunkRadiusFor(c.getRadius());
         if (cr > max) {
            max = cr;
         }
      }

      this.maxChunkRadius = max;
   }
}
