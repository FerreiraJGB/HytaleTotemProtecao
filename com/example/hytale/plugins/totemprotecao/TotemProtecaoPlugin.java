package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonValue;

public class TotemProtecaoPlugin extends JavaPlugin {
   private static final int DEFAULT_RADIUS = 16;
   private static final String DEFAULT_TOTEMPROTECAO_ITEM_ID = "TotemProtecao_Block";
   private static final int DEFAULT_MAX_CLAIMS_PER_PLAYER = 1;
   private static final int MAX_CLAIMS_PER_PLAYER_CAP = 5;
   private static final boolean DEFAULT_ALLOW_CRAFTING = true;
   private static final int[] ALLOWED_RADII = new int[] { 16, 32, 64, 128 };
   private static final long FLUSH_PERIOD_SECONDS = 10L;
   private static final long RECENT_PLACEMENT_IGNORE_BREAK_MS = 2000L;
   private static final long PLAYER_MESSAGE_COOLDOWN_MS = 1200L;
   private static final long PENDING_ITEM_TIMEOUT_MS = 60000L;
   private static final int MAX_RECHARGE_ITEMS = 2;
   private static final long MS_PER_MINUTE = 60000L;
   private static final String MOD_GROUP_NAME = "JogandoBem";
   private static final String MOD_PACK_NAME = "TotemProtecao";
   private static final String LEGACY_MOD_DIR_LOCAL = "Local_GeradorDeCampo";
   private static final String LEGACY_MOD_DIR_DATA = "TotemProtecaoData";
   private static final String LEGACY_DISABLED_MANIFEST_FILE = "manifest.legacy-disabled.json";
   private static final String CONFIG_DIR = "config";
   private static final String DATA_DIR = "data";
   private static final String MAIN_CONFIG_FILE = "config.json";
   private static final String RECHARGE_CONFIG_FILE = "recharge-items.json";
   private static final String CLAIMS_FILE = "claims.json";
   private static final String DEFAULT_RECHARGE_ITEM_1 = "Ingredient_Void_Essence";
   private static final int DEFAULT_RECHARGE_MINUTES_1 = 4;
   private static final String DEFAULT_RECHARGE_ITEM_2 = "Ingredient_Voidheart";
   private static final int DEFAULT_RECHARGE_MINUTES_2 = 720;
   public static final String META_PROTECTION_MS = "totemprotecaoProtectionMs";
   public static final String META_PROTECTION_PAUSED = "totemprotecaoProtectionPaused";
   private ClaimStore claimStore;
   private String totemProtecaoItemId;
   private int claimRadius;
   private int maxClaimsPerPlayer;
   private boolean allowCrafting;
   private ScheduledExecutorService flushExecutor;
   private Path absDataDir;
   private final ConcurrentHashMap<Long, Long> recentClaimPlacements = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, Long> lastPlayerMessageMs = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, Long> borderCenterByPlayer = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, Long> lastBorderSpawnMsByPlayer = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, Long> lastZoneKeyByPlayer = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, String> knownUsernameByUuid = new ConcurrentHashMap();
   private final ConcurrentHashMap<String, UUID> knownUuidByUsername = new ConcurrentHashMap();
   private final ConcurrentHashMap<Long, ConcurrentHashMap<UUID, String>> playersInClaim = new ConcurrentHashMap();
   private final ConcurrentHashMap<UUID, PendingProtection> pendingProtectionByPlayer = new ConcurrentHashMap();
   private volatile List<RechargeItemConfig> rechargeItems = Collections.emptyList();

   public TotemProtecaoPlugin(JavaPluginInit init) {
      super(init);

      try {
         Path dataDir = this.getDataDirectory();
         this.absDataDir = dataDir.toAbsolutePath().normalize();
         this.migrateLegacyModDirectories(this.absDataDir);
         this.disableLegacyAssetPacks(this.absDataDir);
         this.migrateLegacyDataLayout(this.absDataDir);
         this.ensureAssetPackManifest(this.absDataDir);
         this.ensureZoneConfigPageUi(this.absDataDir);
         this.ensureCustomTotemProtecaoItem(this.absDataDir);
         this.ensureTotemProtecaoLang(this.absDataDir);
      } catch (Exception var3) {
      }

   }

   private int loadClaimRadius(Path dataDir) {
      Path cfg = this.getMainConfigPath(dataDir);
      if (!Files.exists(cfg, new LinkOption[0])) {
         return 16;
      } else {
         try {
            String json = Files.readString(cfg, StandardCharsets.UTF_8);
            Integer r = readJsonInt(json, "claimRadius");
            int normalized = normalizeRadius(r == null ? 16 : r);
            return normalized > 0 ? normalized : 16;
         } catch (IOException var6) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var6)).log("TotemProtecao failed to read config.json");
            return 16;
         }
      }
   }

   private int loadMaxClaimsPerPlayer(Path dataDir) {
      Path cfg = this.getMainConfigPath(dataDir);
      if (!Files.exists(cfg, new LinkOption[0])) {
         return 1;
      } else {
         try {
            String json = Files.readString(cfg, StandardCharsets.UTF_8);
            Integer v = readJsonInt(json, "maxClaimsPerPlayer");
            int requested = v == null ? 1 : v;
            return clampMaxClaimsPerPlayer(requested);
         } catch (IOException var6) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var6)).log("TotemProtecao failed to read config.json");
            return 1;
         }
      }
   }

   private boolean loadAllowCrafting(Path dataDir) {
      Path cfg = this.getMainConfigPath(dataDir);
      if (!Files.exists(cfg, new LinkOption[0])) {
         return true;
      } else {
         try {
            String json = Files.readString(cfg, StandardCharsets.UTF_8);
            Boolean v = readJsonBoolean(json, "allowCrafting");
            return v == null ? true : v;
         } catch (IOException var5) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var5)).log("TotemProtecao failed to read config.json");
            return true;
         }
      }
   }

   private void persistConfig() {
      if (this.absDataDir != null) {
         Path cfg = this.getMainConfigPath(this.absDataDir);

         try {
            Files.createDirectories(cfg.getParent());
            String var10000 = this.totemProtecaoItemId == null ? "TotemProtecao_Block" : this.totemProtecaoItemId;
            String content = "{\"totemProtecaoItemId\":\"" + var10000 + "\",\"claimRadius\":" + this.getDefaultRadius()
                  + ",\"maxClaimsPerPlayer\":" + this.getMaxClaimsPerPlayer() + ",\"allowCrafting\":"
                  + this.isAllowCrafting() + "}";
            Files.writeString(cfg, content, StandardCharsets.UTF_8, new OpenOption[0]);
         } catch (IOException var3) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var3)).log("TotemProtecao failed to write config.json");
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
               try {
                  return Integer.parseInt(obj, i, j, 10);
               } catch (NumberFormatException var8) {
                  return null;
               }
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

   private static int clampMaxClaimsPerPlayer(int value) {
      int v = value;
      if (value < 1) {
         v = 1;
      }

      if (v > 5) {
         v = 5;
      }

      return v;
   }

   private static int normalizeRadius(int radius) {
      int[] var1 = ALLOWED_RADII;
      int var2 = var1.length;

      for (int var3 = 0; var3 < var2; ++var3) {
         int r = var1[var3];
         if (r == radius) {
            return r;
         }
      }

      return -1;
   }

   protected void setup() {
      Path dataDir = this.getDataDirectory();
      this.absDataDir = dataDir.toAbsolutePath().normalize();
      this.migrateLegacyModDirectories(this.absDataDir);
      this.disableLegacyAssetPacks(this.absDataDir);
      this.migrateLegacyDataLayout(this.absDataDir);
      this.ensureAssetPackManifest(this.absDataDir);
      this.ensureZoneConfigPageUi(this.absDataDir);
      this.ensureCustomTotemProtecaoItem(this.absDataDir);
      this.ensureTotemProtecaoLang(this.absDataDir);
      this.totemProtecaoItemId = this.loadTotemProtecaoItemId(this.absDataDir);
      this.claimRadius = this.loadClaimRadius(this.absDataDir);
      this.maxClaimsPerPlayer = this.loadMaxClaimsPerPlayer(this.absDataDir);
      this.allowCrafting = this.loadAllowCrafting(this.absDataDir);
      if (!this.reloadRechargeItemsFromDisk(true)) {
         this.rechargeItems = Collections.unmodifiableList(this.createDefaultRechargeItems());
      }

      this.claimStore = new ClaimStore(this.getClaimsFilePath(this.absDataDir), this.getLogger());
      this.claimStore.load();
      this.importLegacyClaimsIntoStore(this.absDataDir, this.claimStore);
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoPlaceSystem(this));
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoBreakSystem(this));
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoUseBlockSystem(this));
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoCraftSystem(this));
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoBorderTickSystem(this));
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoEnterExitTickSystem(this));
      this.getEntityStoreRegistry().registerSystem(new TotemProtecaoPickupSystem(this));
      this.flushExecutor = Executors.newSingleThreadScheduledExecutor((r) -> {
         Thread t = new Thread(r, "TotemProtecao-ClaimsFlush");
         t.setDaemon(true);
         return t;
      });
      this.flushExecutor.scheduleAtFixedRate(() -> {
         try {
            if (this.claimStore != null) {
               this.claimStore.flushIfDirty();
            }
         } catch (Exception var2) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var2)).log("TotemProtecao claims flush failed");
         }

      }, 10L, 10L, TimeUnit.SECONDS);
      Api var10000 = this.getLogger().at(Level.INFO);
      String var10001 = String.valueOf(this.absDataDir);
      var10000
            .log("TotemProtecao dataDir=" + var10001 + ", totemProtecaoItemId=" + this.totemProtecaoItemId
                  + ", claimRadius="
                  + this.claimRadius + ", maxClaimsPerPlayer=" + this.maxClaimsPerPlayer + ", allowCrafting="
                  + this.allowCrafting + ", rechargeItems=" + this.describeRechargeItems() + ", claimsLoaded="
                  + this.claimStore.getClaims().size());
      this.getLogger().at(Level.INFO).log("TotemProtecaoPlugin setup");
   }

   protected void start() {
      try {
         CommandManager.get().register(new TotemProtecaoCommand(this));
         CommandManager.get().register(new TotemProtecaoPreloadCommand(this));
      } catch (Exception var2) {
         ((Api) this.getLogger().at(Level.WARNING).withCause(var2)).log("TotemProtecao failed to register commands");
      }

      this.getLogger().at(Level.INFO).log("TotemProtecaoPlugin start");
   }

   protected void shutdown() {
      if (this.flushExecutor != null) {
         try {
            this.flushExecutor.shutdown();
            this.flushExecutor.awaitTermination(2L, TimeUnit.SECONDS);
         } catch (InterruptedException var2) {
            Thread.currentThread().interrupt();
         } catch (Exception var3) {
         }

         this.flushExecutor = null;
      }

      if (this.claimStore != null) {
         this.claimStore.flushIfDirty();
      }

      this.getLogger().at(Level.INFO).log("TotemProtecaoPlugin shutdown");
   }

   public ClaimStore getClaimStore() {
      return this.claimStore;
   }

   public int getDefaultRadius() {
      return this.claimRadius > 0 ? this.claimRadius : 16;
   }

   public int getClaimRadius() {
      return this.getDefaultRadius();
   }

   public boolean setClaimRadius(int radius) {
      int normalized = normalizeRadius(radius);
      if (normalized <= 0) {
         return false;
      } else {
         this.claimRadius = normalized;
         this.persistConfig();
         return true;
      }
   }

   public int getMaxClaimsPerPlayer() {
      return clampMaxClaimsPerPlayer(this.maxClaimsPerPlayer <= 0 ? 1 : this.maxClaimsPerPlayer);
   }

   public boolean setMaxClaimsPerPlayer(int maxClaimsPerPlayer) {
      this.maxClaimsPerPlayer = clampMaxClaimsPerPlayer(maxClaimsPerPlayer);
      this.persistConfig();
      return true;
   }

   public boolean isAllowCrafting() {
      return this.allowCrafting;
   }

   public boolean setAllowCrafting(boolean allowCrafting) {
      this.allowCrafting = allowCrafting;
      this.persistConfig();
      return true;
   }

   public String getTotemProtecaoItemId() {
      return this.totemProtecaoItemId;
   }

   public Path getAbsDataDir() {
      return this.absDataDir;
   }

   public String getRechargeConfigRelativePath() {
      return CONFIG_DIR + "/" + RECHARGE_CONFIG_FILE;
   }

   public List<RechargeItemConfig> getRechargeItemConfigs() {
      List<RechargeItemConfig> snapshot = this.rechargeItems;
      return snapshot == null ? Collections.emptyList() : snapshot;
   }

   public RechargeItemConfig findRechargeItemConfig(String itemId) {
      if (itemId == null || itemId.trim().isEmpty()) {
         return null;
      } else {
         List<RechargeItemConfig> snapshot = this.rechargeItems;
         if (snapshot == null || snapshot.isEmpty()) {
            return null;
         } else {
            Iterator var3 = snapshot.iterator();

            RechargeItemConfig cfg;
            do {
               if (!var3.hasNext()) {
                  return null;
               }

               cfg = (RechargeItemConfig) var3.next();
            } while (cfg == null || !isItemIdMatch(itemId, cfg.getItemId()));

            return cfg;
         }
      }
   }

   public synchronized boolean reloadRechargeItemsFromDisk(boolean createDefaultWhenMissing) {
      if (this.absDataDir == null) {
         return false;
      } else {
         Path cfg = this.getRechargeConfigPath(this.absDataDir);
         if (!Files.exists(cfg, new LinkOption[0])) {
            if (!createDefaultWhenMissing) {
               return false;
            }

            this.writeRechargeItemsConfig(cfg, this.createDefaultRechargeItems());
         }

         List<RechargeItemConfig> loaded = this.loadRechargeItemsFromConfig(cfg);
         if (loaded.isEmpty()) {
            return false;
         } else {
            this.rechargeItems = Collections.unmodifiableList(new ArrayList(loaded));
            return true;
         }
      }
   }

   public String describeRechargeItems() {
      List<RechargeItemConfig> snapshot = this.rechargeItems;
      if (snapshot != null && !snapshot.isEmpty()) {
         StringBuilder sb = new StringBuilder();
         int index = 0;
         Iterator var4 = snapshot.iterator();

         while (var4.hasNext()) {
            RechargeItemConfig item = (RechargeItemConfig) var4.next();
            if (item != null) {
               if (index > 0) {
                  sb.append(", ");
               }

               sb.append(item.getItemId()).append('=').append(item.getDurationMinutes()).append("min");
               ++index;
            }
         }

         if (sb.length() > 0) {
            return sb.toString();
         }
      }

      return "<none>";
   }

   private List<RechargeItemConfig> createDefaultRechargeItems() {
      List<RechargeItemConfig> defaults = new ArrayList(MAX_RECHARGE_ITEMS);
      defaults.add(new RechargeItemConfig(DEFAULT_RECHARGE_ITEM_1, DEFAULT_RECHARGE_MINUTES_1));
      defaults.add(new RechargeItemConfig(DEFAULT_RECHARGE_ITEM_2, DEFAULT_RECHARGE_MINUTES_2));
      return defaults;
   }

   private List<RechargeItemConfig> loadRechargeItemsFromConfig(Path cfg) {
      if (!Files.exists(cfg, new LinkOption[0])) {
         return Collections.emptyList();
      } else {
         try {
            String json = Files.readString(cfg, StandardCharsets.UTF_8);
            BsonDocument root = BsonDocument.parse(json);
            BsonValue value = root.get("items");
            if (!(value instanceof BsonArray)) {
               this.getLogger().at(Level.WARNING).log("TotemProtecao recharge-items.json missing items array");
               return Collections.emptyList();
            } else {
               List<RechargeItemConfig> loaded = new ArrayList(MAX_RECHARGE_ITEMS);
               Set<String> seen = new LinkedHashSet();
               Iterator var7 = ((BsonArray) value).iterator();

               while (var7.hasNext()) {
                  BsonValue itemValue = (BsonValue) var7.next();
                  if (itemValue instanceof BsonDocument) {
                     BsonDocument itemDoc = (BsonDocument) itemValue;
                     String itemId = readJsonStringFromBson(itemDoc, "itemId");
                     Integer durationMinutes = readJsonIntFromBson(itemDoc, "durationMinutes");
                     if (itemId != null && !itemId.isEmpty() && durationMinutes != null && durationMinutes > 0) {
                        String key = itemId.toLowerCase(Locale.ROOT);
                        if (!seen.contains(key)) {
                           loaded.add(new RechargeItemConfig(itemId, durationMinutes));
                           seen.add(key);
                           if (loaded.size() >= MAX_RECHARGE_ITEMS) {
                              break;
                           }
                        }
                     }
                  }
               }

               if (loaded.isEmpty()) {
                  this.getLogger().at(Level.WARNING)
                        .log("TotemProtecao recharge-items.json has no valid entries");
               } else {
                  int ignored = ((BsonArray) value).size() - loaded.size();
                  if (ignored > 0) {
                     this.getLogger().at(Level.WARNING).log(
                           "TotemProtecao recharge-items.json exceeded limits, ignored entries=" + ignored);
                  }
               }

               return loaded;
            }
         } catch (Exception var11) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var11))
                  .log("TotemProtecao failed to read recharge-items.json");
            return Collections.emptyList();
         }
      }
   }

   private void writeRechargeItemsConfig(Path cfg, List<RechargeItemConfig> items) {
      if (cfg != null && items != null) {
         try {
            Files.createDirectories(cfg.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"items\": [\n");

            for (int i = 0; i < items.size(); ++i) {
               RechargeItemConfig item = (RechargeItemConfig) items.get(i);
               if (item != null) {
                  if (i > 0) {
                     sb.append(",\n");
                  }

                  sb.append("    {\"itemId\":\"");
                  sb.append(item.getItemId());
                  sb.append("\",\"durationMinutes\":");
                  sb.append(item.getDurationMinutes());
                  sb.append('}');
               }
            }

            sb.append("\n");
            sb.append("  ]\n");
            sb.append("}\n");
            Files.writeString(cfg, sb.toString(), StandardCharsets.UTF_8, new OpenOption[0]);
         } catch (IOException var5) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var5))
                  .log("TotemProtecao failed to write recharge-items.json");
         }

      }
   }

   private static String readJsonStringFromBson(BsonDocument obj, String key) {
      if (obj == null || key == null || key.isEmpty()) {
         return null;
      } else {
         BsonValue value = obj.get(key);
         if (value == null || !value.isString()) {
            return null;
         } else {
            String raw = value.asString().getValue();
            if (raw == null) {
               return null;
            } else {
               String normalized = raw.trim();
               return normalized.isEmpty() ? null : normalized;
            }
         }
      }
   }

   private static Integer readJsonIntFromBson(BsonDocument obj, String key) {
      if (obj == null || key == null || key.isEmpty()) {
         return null;
      } else {
         BsonValue value = obj.get(key);
         if (value == null) {
            return null;
         } else if (value instanceof BsonInt32) {
            return ((BsonInt32) value).getValue();
         } else if (value instanceof BsonInt64) {
            long v = ((BsonInt64) value).getValue();
            return v >= -2147483648L && v <= 2147483647L ? (int) v : null;
         } else {
            return null;
         }
      }
   }

   private Path getMainConfigPath(Path dataDir) {
      return this.getConfigDirectoryPath(dataDir).resolve(MAIN_CONFIG_FILE);
   }

   private Path getRechargeConfigPath(Path dataDir) {
      return this.getConfigDirectoryPath(dataDir).resolve(RECHARGE_CONFIG_FILE);
   }

   private Path getClaimsFilePath(Path dataDir) {
      return dataDir.resolve(DATA_DIR).resolve(CLAIMS_FILE);
   }

   private Path getConfigDirectoryPath(Path dataDir) {
      return dataDir.resolve(CONFIG_DIR);
   }

   private void migrateLegacyDataLayout(Path dataDir) {
      if (dataDir != null) {
         this.maybeMoveLegacyFile(dataDir.resolve(MAIN_CONFIG_FILE), this.getMainConfigPath(dataDir));
         this.maybeMoveLegacyFile(dataDir.resolve(RECHARGE_CONFIG_FILE), this.getRechargeConfigPath(dataDir));
         this.maybeMoveLegacyFile(dataDir.resolve(CLAIMS_FILE), this.getClaimsFilePath(dataDir));
      }
   }

   private void migrateLegacyModDirectories(Path dataDir) {
      if (dataDir != null) {
         Path modsRoot = dataDir.getParent();
         if (modsRoot != null) {
            Path legacyLocal = modsRoot.resolve(LEGACY_MOD_DIR_LOCAL);
            Path legacyData = modsRoot.resolve(LEGACY_MOD_DIR_DATA);
            this.maybeCopyLegacyFileFromDir(legacyLocal, MAIN_CONFIG_FILE, this.getMainConfigPath(dataDir), true);
            this.maybeCopyLegacyFileFromDir(legacyData, MAIN_CONFIG_FILE, this.getMainConfigPath(dataDir), true);
            this.maybeCopyLegacyFileFromDir(legacyLocal, RECHARGE_CONFIG_FILE, this.getRechargeConfigPath(dataDir), true);
            this.maybeCopyLegacyFileFromDir(legacyData, RECHARGE_CONFIG_FILE, this.getRechargeConfigPath(dataDir), true);
            this.maybeCopyLegacyFileFromDir(legacyLocal, CLAIMS_FILE, this.getClaimsFilePath(dataDir), false);
            this.maybeCopyLegacyFileFromDir(legacyData, CLAIMS_FILE, this.getClaimsFilePath(dataDir), false);
         }
      }
   }

   private void maybeCopyLegacyFileFromDir(Path legacyDir, String fileName, Path targetFile, boolean configFile) {
      if (legacyDir != null && fileName != null && !fileName.isEmpty() && targetFile != null
            && !Files.exists(targetFile, new LinkOption[0]) && Files.exists(legacyDir, new LinkOption[0])) {
         Path primary = (configFile ? legacyDir.resolve(CONFIG_DIR) : legacyDir.resolve(DATA_DIR)).resolve(fileName);
         Path fallback = legacyDir.resolve(fileName);
         Path source = Files.exists(primary, new LinkOption[0]) ? primary
               : (Files.exists(fallback, new LinkOption[0]) ? fallback : null);
         if (source != null) {
            try {
               if (!source.toAbsolutePath().normalize().equals(targetFile.toAbsolutePath().normalize())) {
                  Files.createDirectories(targetFile.getParent());
                  Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
                  this.getLogger().at(Level.INFO)
                        .log("TotemProtecao migrated legacy file " + String.valueOf(source) + " -> "
                              + String.valueOf(targetFile));
               }
            } catch (IOException var8) {
               ((Api) this.getLogger().at(Level.WARNING).withCause(var8))
                     .log("TotemProtecao failed to migrate legacy file " + String.valueOf(source) + " -> "
                           + String.valueOf(targetFile));
            }
         }
      }
   }

   private void disableLegacyAssetPacks(Path dataDir) {
      if (dataDir != null) {
         Path modsRoot = dataDir.getParent();
         if (modsRoot != null) {
            this.disableLegacyAssetPackManifest(modsRoot.resolve(LEGACY_MOD_DIR_LOCAL));
            this.disableLegacyAssetPackManifest(modsRoot.resolve(LEGACY_MOD_DIR_DATA));
         }
      }
   }

   private void disableLegacyAssetPackManifest(Path legacyDir) {
      if (legacyDir != null && Files.exists(legacyDir, new LinkOption[0])) {
         Path manifest = legacyDir.resolve("manifest.json");
         if (Files.exists(manifest, new LinkOption[0])) {
            Path disabledManifest = legacyDir.resolve(LEGACY_DISABLED_MANIFEST_FILE);

            try {
               if (!Files.exists(disabledManifest, new LinkOption[0])) {
                  Files.move(manifest, disabledManifest, StandardCopyOption.REPLACE_EXISTING);
               } else {
                  Files.deleteIfExists(manifest);
               }

               this.getLogger().at(Level.INFO)
                     .log("TotemProtecao disabled legacy asset pack manifest at " + String.valueOf(manifest));
            } catch (IOException var5) {
               ((Api) this.getLogger().at(Level.WARNING).withCause(var5))
                     .log("TotemProtecao failed to disable legacy asset pack manifest at "
                           + String.valueOf(manifest));
            }
         }
      }
   }

   private void importLegacyClaimsIntoStore(Path dataDir, ClaimStore store) {
      if (dataDir != null && store != null) {
         Path modsRoot = dataDir.getParent();
         if (modsRoot != null) {
            int imported = 0;
            imported += this.importLegacyClaimsFromFile(store, this.resolveLegacyClaimsFile(modsRoot.resolve(LEGACY_MOD_DIR_LOCAL)));
            imported += this.importLegacyClaimsFromFile(store, this.resolveLegacyClaimsFile(modsRoot.resolve(LEGACY_MOD_DIR_DATA)));
            if (imported > 0) {
               this.getLogger().at(Level.INFO)
                     .log("TotemProtecao imported legacy claims count=" + imported);
            }
         }
      }
   }

   private int importLegacyClaimsFromFile(ClaimStore store, Path legacyClaimsFile) {
      if (store != null && legacyClaimsFile != null && Files.exists(legacyClaimsFile, new LinkOption[0])) {
         Path currentClaimsFile = this.getClaimsFilePath(this.absDataDir).toAbsolutePath().normalize();
         Path legacyNormalized = legacyClaimsFile.toAbsolutePath().normalize();
         if (currentClaimsFile.equals(legacyNormalized)) {
            return 0;
         } else {
            try {
               ClaimStore legacyStore = new ClaimStore(legacyNormalized, this.getLogger());
               legacyStore.load();
               int imported = 0;
               Iterator var6 = legacyStore.getClaims().iterator();

               while (var6.hasNext()) {
                  Claim c = (Claim) var6.next();
                  if (c != null && store.findClaimByCenter(c.getCenterX(), c.getCenterZ()) == null && store.addClaim(c)) {
                     ++imported;
                  }
               }

               if (imported > 0) {
                  this.getLogger().at(Level.INFO)
                        .log("TotemProtecao imported legacy claims from " + String.valueOf(legacyNormalized)
                              + " count=" + imported);
               }

               return imported;
            } catch (Exception var8) {
               ((Api) this.getLogger().at(Level.WARNING).withCause(var8))
                     .log("TotemProtecao failed to import legacy claims from " + String.valueOf(legacyClaimsFile));
               return 0;
            }
         }
      } else {
         return 0;
      }
   }

   private Path resolveLegacyClaimsFile(Path legacyDir) {
      if (legacyDir == null || !Files.exists(legacyDir, new LinkOption[0])) {
         return null;
      } else {
         Path inDataDir = legacyDir.resolve(DATA_DIR).resolve(CLAIMS_FILE);
         if (Files.exists(inDataDir, new LinkOption[0])) {
            return inDataDir;
         } else {
            Path inRoot = legacyDir.resolve(CLAIMS_FILE);
            return Files.exists(inRoot, new LinkOption[0]) ? inRoot : null;
         }
      }
   }

   private void maybeMoveLegacyFile(Path legacyFile, Path targetFile) {
      if (legacyFile != null && targetFile != null && Files.exists(legacyFile, new LinkOption[0])
            && !Files.exists(targetFile, new LinkOption[0])) {
         try {
            Files.createDirectories(targetFile.getParent());
            Files.move(legacyFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException var4) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var4))
                  .log("TotemProtecao failed to migrate " + legacyFile + " to " + targetFile);
         }
      }
   }

   public static boolean isItemIdMatch(String itemId, String expectedId) {
      if (itemId == null) {
         return false;
      } else if (expectedId == null) {
         return false;
      } else {
         String itemLower = itemId.toLowerCase(Locale.ROOT);
         String expectedLower = expectedId.toLowerCase(Locale.ROOT);
         if (itemLower.equals(expectedLower)) {
            return true;
         } else {
            return itemLower.endsWith(":" + expectedLower) ? true : itemLower.contains(expectedLower);
         }
      }
   }

   private String loadTotemProtecaoItemId(Path dataDir) {
      Path cfg = this.getMainConfigPath(dataDir);
      String json;
      if (!Files.exists(cfg, new LinkOption[0])) {
         try {
            Files.createDirectories(cfg.getParent());
            json = "{\"totemProtecaoItemId\":\"TotemProtecao_Block\",\"claimRadius\":16,\"maxClaimsPerPlayer\":1,\"allowCrafting\":true}";
            Files.writeString(cfg, json, StandardCharsets.UTF_8, new OpenOption[0]);
         } catch (IOException var10) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var10))
                  .log("TotemProtecao failed to write config.json");
         }

         return "TotemProtecao_Block";
      } else {
         try {
            json = Files.readString(cfg, StandardCharsets.UTF_8);
            String key = "\"totemProtecaoItemId\"";
            int k = json.indexOf(key);
            if (k < 0) {
               return "TotemProtecao_Block";
            } else {
               int colon = json.indexOf(58, k + key.length());
               if (colon < 0) {
                  return "TotemProtecao_Block";
               } else {
                  int q1 = json.indexOf(34, colon + 1);
                  if (q1 < 0) {
                     return "TotemProtecao_Block";
                  } else {
                     int q2 = json.indexOf(34, q1 + 1);
                     if (q2 < 0) {
                        return "TotemProtecao_Block";
                     } else {
                        String value = json.substring(q1 + 1, q2).trim();
                        return value.isEmpty() ? "TotemProtecao_Block" : value;
                     }
                  }
               }
            }
         } catch (IOException var11) {
            ((Api) this.getLogger().at(Level.WARNING).withCause(var11)).log("TotemProtecao failed to read config.json");
            return "TotemProtecao_Block";
         }
      }
   }

   public boolean isOpBypass(UUID playerUuid) {
      PermissionsModule perms = PermissionsModule.get();
      if (perms == null) {
         return false;
      } else {
         try {
            Iterator var3 = perms.getGroupsForUser(playerUuid).iterator();

            String gl;
            do {
               do {
                  String g;
                  do {
                     if (!var3.hasNext()) {
                        return false;
                     }

                     g = (String) var3.next();
                  } while (g == null);

                  gl = g.trim().toLowerCase();
               } while (gl.isEmpty());
            } while (!gl.equals("op") && !gl.equals("admin") && !gl.equals("operator"));

            return true;
         } catch (Exception var6) {
            return false;
         }
      }
   }

   public void markRecentClaimPlacement(int x, int z) {
      this.recentClaimPlacements.put(centerKey(x, z), System.currentTimeMillis());
   }

   public void enableBorder(UUID playerUuid, int centerX, int centerZ) {
      if (playerUuid != null) {
         this.borderCenterByPlayer.put(playerUuid, centerKey(centerX, centerZ));
      }
   }

   public void disableBorder(UUID playerUuid) {
      if (playerUuid != null) {
         this.borderCenterByPlayer.remove(playerUuid);
         this.lastBorderSpawnMsByPlayer.remove(playerUuid);
      }
   }

   public void clearBorderForClaim(int centerX, int centerZ) {
      long key = centerKey(centerX, centerZ);
      Iterator var5 = this.borderCenterByPlayer.entrySet().iterator();

      while (var5.hasNext()) {
         Entry<UUID, Long> e = (Entry) var5.next();
         if (e != null) {
            UUID u = (UUID) e.getKey();
            Long v = (Long) e.getValue();
            if (u != null && v != null && v == key) {
               this.disableBorder(u);
            }
         }
      }

   }

   public boolean isBorderEnabled(UUID playerUuid, long centerKey) {
      if (playerUuid == null) {
         return false;
      } else {
         Long v = (Long) this.borderCenterByPlayer.get(playerUuid);
         return v != null && v == centerKey;
      }
   }

   public Long getBorderCenterKey(UUID playerUuid) {
      return playerUuid == null ? null : (Long) this.borderCenterByPlayer.get(playerUuid);
   }

   public boolean shouldSpawnBorderNow(UUID playerUuid, long nowMs, long cooldownMs) {
      if (playerUuid == null) {
         return false;
      } else {
         Long last = (Long) this.lastBorderSpawnMsByPlayer.get(playerUuid);
         if (last != null && nowMs - last < cooldownMs) {
            return false;
         } else {
            this.lastBorderSpawnMsByPlayer.put(playerUuid, nowMs);
            return true;
         }
      }
   }

   public boolean shouldIgnoreCenterBreak(int x, int z) {
      long key = centerKey(x, z);
      Long ts = (Long) this.recentClaimPlacements.get(key);
      if (ts == null) {
         return false;
      } else {
         long age = System.currentTimeMillis() - ts;
         if (age >= 0L && age < 2000L) {
            return true;
         } else {
            this.recentClaimPlacements.remove(key, ts);
            return false;
         }
      }
   }

   public void sendPlayerMessage(PlayerRef player, String text) {
      if (player != null && text != null && !text.isEmpty()) {
         UUID uuid = player.getUuid();
         long now = System.currentTimeMillis();
         Long last = (Long) this.lastPlayerMessageMs.get(uuid);
         if (last == null || now - last >= 1200L) {
            this.lastPlayerMessageMs.put(uuid, now);

            try {
               player.sendMessage(Message.raw(text));
            } catch (Exception var8) {
            }

         }
      }
   }

   public void sendPlayerMessageImmediate(PlayerRef player, String text) {
      if (player != null && text != null && !text.isEmpty()) {
         try {
            player.sendMessage(Message.raw(text));
         } catch (Exception var4) {
         }

      }
   }

   public Long getLastZoneKey(UUID playerUuid) {
      return playerUuid == null ? null : (Long) this.lastZoneKeyByPlayer.get(playerUuid);
   }

   public void setLastZoneKey(UUID playerUuid, Long zoneKey) {
      if (playerUuid != null) {
         if (zoneKey == null) {
            this.lastZoneKeyByPlayer.remove(playerUuid);
         } else {
            this.lastZoneKeyByPlayer.put(playerUuid, zoneKey);
         }
      }
   }

   public void rememberUsername(UUID playerUuid, String username) {
      if (playerUuid != null && username != null) {
         String u = username.trim();
         if (!u.isEmpty()) {
            this.knownUsernameByUuid.put(playerUuid, u);
            this.knownUuidByUsername.put(u.toLowerCase(), playerUuid);
         }
      }
   }

   public String getKnownUsername(UUID playerUuid) {
      return playerUuid == null ? null : (String) this.knownUsernameByUuid.get(playerUuid);
   }

   public UUID getKnownUuidForUsername(String username) {
      if (username == null) {
         return null;
      } else {
         String u = username.trim().toLowerCase();
         return u.isEmpty() ? null : (UUID) this.knownUuidByUsername.get(u);
      }
   }

   public Map<UUID, String> getPlayersInClaim(int centerX, int centerZ) {
      long key = centerKey(centerX, centerZ);
      Map<UUID, String> m = (Map) this.playersInClaim.get(key);
      return m != null && !m.isEmpty() ? Collections.unmodifiableMap(m) : Collections.emptyMap();
   }

   public void updatePlayerClaimMembership(UUID playerUuid, Long prevCenterKey, Long nowCenterKey, String username) {
      if (playerUuid != null) {
         if (prevCenterKey != null) {
            ConcurrentHashMap<UUID, String> prev = (ConcurrentHashMap) this.playersInClaim.get(prevCenterKey);
            if (prev != null) {
               prev.remove(playerUuid);
               if (prev.isEmpty()) {
                  this.playersInClaim.remove(prevCenterKey, prev);
               }
            }
         }

         if (nowCenterKey != null) {
            String u = username == null ? null : username.trim();
            if (u == null || u.isEmpty()) {
               u = (String) this.knownUsernameByUuid.get(playerUuid);
            }

            if (u == null || u.isEmpty()) {
               u = playerUuid.toString();
            }

            ((ConcurrentHashMap) this.playersInClaim.computeIfAbsent(nowCenterKey, (k) -> {
               return new ConcurrentHashMap();
            })).put(playerUuid, u);
         }

      }
   }

   public static long centerKey(int x, int z) {
      return (long) x << 32 ^ (long) z & 4294967295L;
   }

   public void setPendingProtectionItem(UUID playerUuid, long remainingMs) {
      if (playerUuid != null) {
         long normalized = Math.max(0L, remainingMs);
         if (normalized > 0L) {
            this.pendingProtectionByPlayer.put(playerUuid,
                  new PendingProtection(normalized, System.currentTimeMillis()));
         } else {
            this.pendingProtectionByPlayer.remove(playerUuid);
         }
      }
   }

   public PendingProtection consumePendingProtectionItem(UUID playerUuid, long nowMs) {
      if (playerUuid == null) {
         return null;
      } else {
         PendingProtection pending = (PendingProtection) this.pendingProtectionByPlayer.remove(playerUuid);
         if (pending == null) {
            return null;
         } else if (nowMs - pending.createdMs > PENDING_ITEM_TIMEOUT_MS) {
            return null;
         } else {
            return pending.remainingMs > 0L ? pending : null;
         }
      }
   }

   public static long readProtectionRemainingMs(ItemStack stack) {
      if (stack == null) {
         return 0L;
      } else {
         BsonDocument meta = stack.getMetadata();
         if (meta == null) {
            return 0L;
         } else {
            BsonValue value = meta.get(META_PROTECTION_MS);
            if (value instanceof BsonInt64) {
               return ((BsonInt64) value).getValue();
            } else {
               return 0L;
            }
         }
      }
   }

   public static boolean readProtectionPaused(ItemStack stack) {
      if (stack == null) {
         return false;
      } else {
         BsonDocument meta = stack.getMetadata();
         if (meta == null) {
            return false;
         } else {
            BsonValue value = meta.get(META_PROTECTION_PAUSED);
            if (value instanceof BsonBoolean) {
               return ((BsonBoolean) value).getValue();
            } else {
               return false;
            }
         }
      }
   }

   public static ItemStack applyProtectionMetadata(ItemStack stack, long remainingMs, boolean paused) {
      if (stack == null) {
         return null;
      } else {
         BsonDocument meta = stack.getMetadata();
         if (meta == null) {
            meta = new BsonDocument();
         }

         meta.put(META_PROTECTION_MS, new BsonInt64(Math.max(0L, remainingMs)));
         meta.put(META_PROTECTION_PAUSED, new BsonBoolean(paused));
         return stack.withMetadata(meta);
      }
   }

   public static final class PendingProtection {
      private final long remainingMs;
      private final long createdMs;

      private PendingProtection(long remainingMs, long createdMs) {
         this.remainingMs = remainingMs;
         this.createdMs = createdMs;
      }

      public long getRemainingMs() {
         return this.remainingMs;
      }
   }

   public static final class RechargeItemConfig {
      private final String itemId;
      private final int durationMinutes;

      private RechargeItemConfig(String itemId, int durationMinutes) {
         this.itemId = itemId == null ? "" : itemId.trim();
         this.durationMinutes = Math.max(1, durationMinutes);
      }

      public String getItemId() {
         return this.itemId;
      }

      public int getDurationMinutes() {
         return this.durationMinutes;
      }

      public long getDurationMs() {
         return (long) this.durationMinutes * MS_PER_MINUTE;
      }
   }

   public static String formatDuration(long ms) {
      if (ms <= 0L) {
         return "0m";
      } else {
         long totalSeconds = ms / 1000L;
         long hours = totalSeconds / 3600L;
         long minutes = totalSeconds % 3600L / 60L;
         long seconds = totalSeconds % 60L;
         if (hours > 0L) {
            return hours + "h " + minutes + "m";
         } else {
            return minutes > 0L ? minutes + "m " + seconds + "s" : seconds + "s";
         }
      }
   }

   private void ensureAssetPackManifest(Path dataDir) {
      try {
         Files.createDirectories(dataDir);
         Path manifest = dataDir.resolve("manifest.json");
         if (Files.exists(manifest, new LinkOption[0])) {
            return;
         }

         Files.writeString(manifest, "{\"Group\":\"" + MOD_GROUP_NAME + "\",\"Name\":\"" + MOD_PACK_NAME + "\"}",
               StandardCharsets.UTF_8,
               new OpenOption[0]);
      } catch (Exception var3) {
      }

   }

   private void ensureCustomTotemProtecaoItem(Path dataDir) {
      try {
         Path item = dataDir.resolve("Server/Item/Items/TotemProtecao/TotemProtecao_Block.json");
         Path itemAlt = dataDir.resolve("Assets/Server/Item/Items/TotemProtecao/TotemProtecao_Block.json");
         String json = "{\"TranslationProperties\":{\"Name\":\"Totem de Protecao\",\"Description\":\"Gera uma area protegida temporaria. Pode ser recarregado com itens definidos pelo servidor.\"},\"MaxStack\":1,\"Icon\":\"Icons/ItemsGenerated/Furniture_Kweebec_Statue.png\",\"Categories\":[\"Blocks.Deco\"],\"Recipe\":{\"TimeSeconds\":3,\"KnowledgeRequired\":false,\"Input\":[{\"ItemId\":\"Ingredient_Bar_Thorium\",\"Quantity\":10},{\"ItemId\":\"Ingredient_Bar_Copper\",\"Quantity\":5},{\"ItemId\":\"Ingredient_Voidheart\",\"Quantity\":2},{\"ItemId\":\"Ingredient_Void_Essence\",\"Quantity\":10}],\"BenchRequirement\":[{\"Id\":\"Workbench\",\"Type\":\"Crafting\",\"Categories\":[\"Workbench_Crafting\"]}],\"OutputQuantity\":1},\"Interactions\":{\"Primary\":\"Block_Primary\",\"Secondary\":\"Block_Secondary\"},\"BlockType\":{\"Material\":\"Solid\",\"DrawType\":\"Model\",\"Opacity\":\"Transparent\",\"CustomModel\":\"Blocks/Decorative_Sets/Kweebec/Statue.blockymodel\",\"CustomModelTexture\":[{\"Texture\":\"Blocks/Decorative_Sets/Kweebec/Statue_Texture.png\",\"Weight\":1}],\"CustomModelScale\":0.85,\"Group\":\"Wood\",\"HitboxType\":\"Beam\",\"VariantRotation\":\"NESW\",\"Flags\":{},\"Gathering\":{\"Breaking\":{\"GatherType\":\"Woods\"}},\"BlockSoundSetId\":\"Wood\",\"Interactions\":{\"Use\":{\"Interactions\":[{\"Type\":\"Simple\",\"Effects\":{\"WorldSoundEventId\":\"SFX_Wood_Hit\"}}]}},\"InteractionHint\":\"server.interactionHints.turnoff\",\"Particles\":[]},\"PlayerAnimationsId\":\"Block\",\"Model\":\"Blocks/Decorative_Sets/Kweebec/Statue.blockymodel\",\"Texture\":\"Blocks/Decorative_Sets/Kweebec/Statue_Texture.png\",\"IconProperties\":{\"Scale\":0.68,\"Rotation\":[22.5,45,22.5],\"Translation\":[0,-15]},\"Tags\":{\"Type\":[\"Deco\"],\"Family\":[\"Kweebec\"]},\"ItemSoundSetId\":\"ISS_Blocks_Wood\",\"Quality\":\"Legendary\"}";
         boolean wrote = false;
         boolean needMain = !Files.exists(item, new LinkOption[0]);
         if (!needMain) {
            try {
               String existing = Files.readString(item, StandardCharsets.UTF_8);
               needMain = !existing.contains("\"Recipe\"")
                     || !existing.contains("Pode ser recarregado com itens definidos pelo servidor.")
                     || !existing.contains("SFX_Wood_Hit");
            } catch (IOException var10) {
               needMain = true;
            }
         }

         if (needMain) {
            Files.createDirectories(item.getParent());
            Files.writeString(item, json, StandardCharsets.UTF_8, new OpenOption[0]);
            wrote = true;
         }

         boolean needAlt = !Files.exists(itemAlt, new LinkOption[0]);
         if (!needAlt) {
            try {
               String existingAlt = Files.readString(itemAlt, StandardCharsets.UTF_8);
               needAlt = !existingAlt.contains("\"Recipe\"")
                     || !existingAlt.contains("Pode ser recarregado com itens definidos pelo servidor.")
                     || !existingAlt.contains("SFX_Wood_Hit");
            } catch (IOException var9) {
               needAlt = true;
            }
         }

         if (needAlt) {
            Files.createDirectories(itemAlt.getParent());
            Files.writeString(itemAlt, json, StandardCharsets.UTF_8, new OpenOption[0]);
            wrote = true;
         }

         if (wrote) {
            Api var10000 = this.getLogger().at(Level.INFO);
            String var10001 = String.valueOf(item);
            var10000.log("TotemProtecao wrote item asset to " + var10001 + " and/or " + String.valueOf(itemAlt));
         }
      } catch (IOException var11) {
         ((Api) this.getLogger().at(Level.WARNING).withCause(var11))
               .log("TotemProtecao failed to write custom item asset");
      }

   }

   private void ensureZoneConfigPageUi(Path dataDir) {
      try {
         Path ui = dataDir.resolve("Common/UI/Custom/Pages/TotemProtecaoZoneConfigPage.ui");
         Path uiAlt = dataDir.resolve("Assets/Common/UI/Custom/Pages/TotemProtecaoZoneConfigPage.ui");
         String content = "$C = \"../Common.ui\";\n\n"
               + "$C.@PageOverlay {}\n\n"
               + "$C.@DecoratedContainer {\n"
               + "  Anchor: (Width: 600, Height: 400);\n\n"
               + "  #Title {\n"
               + "    Group {\n"
               + "      $C.@Title {\n"
               + "        @Text = \"Configuracao da Zona\";\n"
               + "      }\n"
               + "    }\n"
               + "  }\n\n"
               + "  #Content {\n"
               + "    LayoutMode: Top;\n"
               + "    Padding: (Right: 15, Bottom: 5);\n\n"
               + "    Group {\n"
               + "      LayoutMode: Left;\n\n"
               + "      Label {\n"
               + "        FlexWeight: 1;\n"
               + "        Text: %server.customUI.itemRepairPage.item;\n"
               + "        Style: (RenderBold: true);\n"
               + "      }\n\n"
               + "      Label {\n"
               + "        Text: %server.customUI.itemRepairPage.durability;\n"
               + "        Style: (RenderBold: true);\n"
               + "      }\n"
               + "    }\n\n"
               + "    Group #ElementList {\n"
               + "      FlexWeight: 1;\n"
               + "      LayoutMode: TopScrolling;\n"
               + "      ScrollbarStyle: $C.@DefaultScrollbarStyle;\n"
               + "    }\n\n"
               + "    Group #Footer {\n"
               + "      Anchor: (Height: 100);\n"
               + "      LayoutMode: Top;\n"
               + "      Padding: (Horizontal: -2);\n\n"
               + "      Group #FooterDivider {\n"
               + "        Anchor: (Height: 2);\n"
               + "        Background: #19252F;\n"
               + "      }\n\n"
               + "      Group #FooterContent {\n"
               + "        FlexWeight: 1;\n"
               + "        LayoutMode: Top;\n"
               + "        Padding: (Top: 4, Bottom: 2);\n\n"
               + "        Label #FooterLine1 {\n"
               + "          Style: (\n"
               + "            FontSize: 12,\n"
               + "            TextColor: #b4c8c9,\n"
               + "            HorizontalAlignment: Center,\n"
               + "            VerticalAlignment: Center,\n"
               + "          );\n"
               + "          Text: \"Coloque o item na mao e use no totem para recarrega-lo. Carga perdida se quebrado.\";\n"
               + "        }\n\n"
               + "        Group #FooterLine2 {\n"
               + "          LayoutMode: Center;\n"
               + "          Padding: (Top: 2);\n\n"
               + "          ItemIcon #FooterItem1Icon {\n"
               + "            Anchor: (Width: 16, Height: 16);\n"
               + "            ItemId: \"Ingredient_Voidheart\";\n"
               + "          }\n\n"
               + "          Label #FooterItem1Text {\n"
               + "            Padding: (Left: 6);\n"
               + "            Style: (FontSize: 12, TextColor: #b4c8c9);\n"
               + "            Text: \"Coracao do vazio = 12h\";\n"
               + "          }\n"
               + "        }\n\n"
               + "        Group #FooterLine3 {\n"
               + "          LayoutMode: Center;\n"
               + "          Padding: (Top: 2);\n\n"
               + "          ItemIcon #FooterItem2Icon {\n"
               + "            Anchor: (Width: 16, Height: 16);\n"
               + "            ItemId: \"Ingredient_Void_Essence\";\n"
               + "          }\n\n"
               + "          Label #FooterItem2Text {\n"
               + "            Padding: (Left: 6);\n"
               + "            Style: (FontSize: 12, TextColor: #b4c8c9);\n"
               + "            Text: \"Essencia do Vazio = 4min\";\n"
               + "          }\n"
               + "        }\n\n"
               + "        Label #FooterLine4 {\n"
               + "          Style: (\n"
               + "            FontSize: 11,\n"
               + "            TextColor: #7caacc,\n"
               + "            HorizontalAlignment: Center,\n"
               + "            VerticalAlignment: Center,\n"
               + "          );\n"
               + "          Text: \"Feito com carinho por Ferreira_ para o Jogando Bem: jogandobem.com.br\";\n"
               + "        }\n"
               + "      }\n"
               + "    }\n"
               + "  }\n"
               + "}\n\n"
               + "$C.@BackButton {}\n";
         boolean wrote = false;
         boolean needMain = !Files.exists(ui, new LinkOption[0]);
         if (!needMain) {
            try {
               needMain = !Files.readString(ui, StandardCharsets.UTF_8).contains("Feito com carinho por Ferreira_");
            } catch (IOException var9) {
               needMain = true;
            }
         }

         if (needMain) {
            Files.createDirectories(ui.getParent());
            Files.writeString(ui, content, StandardCharsets.UTF_8, new OpenOption[0]);
            wrote = true;
         }

         boolean needAlt = !Files.exists(uiAlt, new LinkOption[0]);
         if (!needAlt) {
            try {
               needAlt = !Files.readString(uiAlt, StandardCharsets.UTF_8).contains("Feito com carinho por Ferreira_");
            } catch (IOException var8) {
               needAlt = true;
            }
         }

         if (needAlt) {
            Files.createDirectories(uiAlt.getParent());
            Files.writeString(uiAlt, content, StandardCharsets.UTF_8, new OpenOption[0]);
            wrote = true;
         }

         if (wrote) {
            Api var10000 = this.getLogger().at(Level.INFO);
            String var10001 = String.valueOf(ui);
            var10000.log("TotemProtecao wrote UI page to " + var10001 + " and/or " + String.valueOf(uiAlt));
         }
      } catch (IOException var6) {
         ((Api) this.getLogger().at(Level.WARNING).withCause(var6)).log("TotemProtecao failed to write zone config UI");
      }

   }

   private void ensureTotemProtecaoLang(Path dataDir) {
      try {
         Path lang = dataDir.resolve("Server/Languages/en-US/server.lang");
         Path langAlt = dataDir.resolve("Assets/Server/Languages/en-US/server.lang");
         String content = "# === totemprotecao ===\nserver.items.TotemProtecao_Block.name = Gerador de Protecao\nserver.items.TotemProtecao_Block.description = Gera uma area protegida temporaria. Pode ser recarregado com itens definidos pelo servidor.\n";
         boolean wrote = false;
         boolean needMain = !Files.exists(lang, new LinkOption[0]);
         if (!needMain) {
            try {
               needMain = !Files.readString(lang, StandardCharsets.UTF_8)
                     .contains("Pode ser recarregado com itens definidos pelo servidor.");
            } catch (IOException var9) {
               needMain = true;
            }
         }

         if (needMain) {
            Files.createDirectories(lang.getParent());
            Files.writeString(lang, content, StandardCharsets.UTF_8, new OpenOption[0]);
            wrote = true;
         }

         boolean needAlt = !Files.exists(langAlt, new LinkOption[0]);
         if (!needAlt) {
            try {
               needAlt = !Files.readString(langAlt, StandardCharsets.UTF_8)
                     .contains("Pode ser recarregado com itens definidos pelo servidor.");
            } catch (IOException var8) {
               needAlt = true;
            }
         }

         if (needAlt) {
            Files.createDirectories(langAlt.getParent());
            Files.writeString(langAlt, content, StandardCharsets.UTF_8, new OpenOption[0]);
            wrote = true;
         }

         if (wrote) {
            Api var10000 = this.getLogger().at(Level.INFO);
            String var10001 = String.valueOf(lang);
            var10000.log("TotemProtecao wrote language file to " + var10001 + " and/or " + String.valueOf(langAlt));
         }
      } catch (IOException var7) {
         ((Api) this.getLogger().at(Level.WARNING).withCause(var7)).log("TotemProtecao failed to write language file");
      }
   }

}
