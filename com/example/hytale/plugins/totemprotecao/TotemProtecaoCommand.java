package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

public final class TotemProtecaoCommand extends AbstractAsyncCommand {
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoCommand(TotemProtecaoPlugin plugin) {
      super("totemprotecao", "Comandos admin do TotemProtecao");
      this.plugin = plugin;
      this.setAllowsExtraArguments(true);
   }

   protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
      String input = ctx.getInputString();
      String[] parts = input == null ? new String[0] : input.trim().split("\\s+");
      if (!this.isAllowed(ctx)) {
         ctx.sendMessage(Message.raw("Sem permissao"));
         return CompletableFuture.completedFuture((Void) null);
      } else if (parts.length <= 1) {
         this.sendUsage(ctx);
         ctx.sendMessage(Message.raw("Raio atual: " + this.plugin.getClaimRadius()));
         ctx.sendMessage(Message.raw("Maximo de protecoes por jogador: " + this.plugin.getMaxClaimsPerPlayer()));
         ctx.sendMessage(Message.raw("Craft permitido: " + this.plugin.isAllowCrafting()));
         return CompletableFuture.completedFuture((Void) null);
      } else {
         String sub = parts[1].toLowerCase();
         if (sub.equals("trust")) {
            return this.handleTrust(ctx, parts);
         } else {
            int max;
            if (sub.equals("radius")) {
               if (parts.length == 2) {
                  ctx.sendMessage(Message.raw("Raio atual: " + this.plugin.getClaimRadius()));
                  return CompletableFuture.completedFuture((Void) null);
               } else {
                  try {
                     max = Integer.parseInt(parts[2]);
                  } catch (Exception var7) {
                     ctx.sendMessage(Message.raw("Raio invalido. Permitidos: 16, 32, 64, 128"));
                     return CompletableFuture.completedFuture((Void) null);
                  }

                  boolean ok = this.plugin.setClaimRadius(max);
                  if (!ok) {
                     ctx.sendMessage(Message.raw("Raio invalido. Permitidos: 16, 32, 64, 128"));
                     return CompletableFuture.completedFuture((Void) null);
                  } else {
                     ctx.sendMessage(
                           Message.raw("Raio do TotemProtecao definido para " + this.plugin.getClaimRadius()));
                     return CompletableFuture.completedFuture((Void) null);
                  }
               }
            } else if (sub.equals("maxclaims")) {
               if (parts.length == 2) {
                  ctx.sendMessage(
                        Message.raw("Maximo de protecoes por jogador: " + this.plugin.getMaxClaimsPerPlayer()));
                  return CompletableFuture.completedFuture((Void) null);
               } else {
                  try {
                     max = Integer.parseInt(parts[2]);
                  } catch (Exception var8) {
                     ctx.sendMessage(Message.raw("Maximo invalido. Permitido: 1-5"));
                     return CompletableFuture.completedFuture((Void) null);
                  }

                  this.plugin.setMaxClaimsPerPlayer(max);
                  ctx.sendMessage(Message
                        .raw("Maximo de protecoes por jogador definido para " + this.plugin.getMaxClaimsPerPlayer()));
                  return CompletableFuture.completedFuture((Void) null);
               }
            } else if (sub.equals("crafting")) {
               if (parts.length == 2) {
                  ctx.sendMessage(Message.raw("Craft permitido: " + this.plugin.isAllowCrafting()));
                  return CompletableFuture.completedFuture((Void) null);
               } else {
                  String arg = parts[2].toLowerCase();
                  if (!arg.equals("on") && !arg.equals("true") && !arg.equals("yes")) {
                     if (!arg.equals("off") && !arg.equals("false") && !arg.equals("no")) {
                        ctx.sendMessage(Message.raw("Uso: /totemprotecao crafting <on|off>"));
                        return CompletableFuture.completedFuture((Void) null);
                     }

                     this.plugin.setAllowCrafting(false);
                  } else {
                     this.plugin.setAllowCrafting(true);
                  }

                  ctx.sendMessage(Message.raw("Craft permitido: " + this.plugin.isAllowCrafting()));
                  return CompletableFuture.completedFuture((Void) null);
               }
            } else {
               this.sendUsage(ctx);
               return CompletableFuture.completedFuture((Void) null);
            }
         }
      }
   }

   private CompletableFuture<Void> handleTrust(CommandContext ctx, String[] parts) {
      if (parts.length < 4) {
         this.sendTrustUsage(ctx);
         return CompletableFuture.completedFuture((Void) null);
      } else {
         ClaimStore store = this.plugin.getClaimStore();
         byte actionIndex;
         Claim claim;
         if (parts[2].equalsIgnoreCase("here")) {
            actionIndex = 3;
            Claim at = this.resolveClaimAtSender(ctx);
            if (at == null) {
               ctx.sendMessage(Message.raw("Nenhuma protecao encontrada na sua posicao"));
               return CompletableFuture.completedFuture((Void) null);
            }

            claim = at;
         } else {
            if (parts.length < 5) {
               this.sendTrustUsage(ctx);
               return CompletableFuture.completedFuture((Void) null);
            }

            actionIndex = 4;

            int centerZ;
            int centerX;
            try {
               centerX = Integer.parseInt(parts[2]);
               centerZ = Integer.parseInt(parts[3]);
            } catch (Exception var10) {
               ctx.sendMessage(Message.raw("Coordenadas do centro invalidas"));
               return CompletableFuture.completedFuture((Void) null);
            }

            claim = store.findClaimByCenter(centerX, centerZ);
            if (claim == null) {
               ctx.sendMessage(Message.raw("Nenhuma protecao encontrada no centro " + centerX + "," + centerZ));
               return CompletableFuture.completedFuture((Void) null);
            }
         }

         String action = parts[actionIndex].toLowerCase();
         String var10001;
         if (!action.equals("list")) {
            UUID target;
            if (action.equals("remove")) {
               if (parts.length <= actionIndex + 1) {
                  ctx.sendMessage(
                        Message.raw("Uso: /totemprotecao trust <centerX> <centerZ> remove <playerName|playerUuid>"));
                  ctx.sendMessage(Message.raw("Uso: /totemprotecao trust here remove <playerName|playerUuid>"));
                  return CompletableFuture.completedFuture((Void) null);
               } else {
                  target = this.resolvePlayerUuid(parts[actionIndex + 1]);
                  if (target == null) {
                     ctx.sendMessage(Message.raw("Jogador desconhecido: " + parts[actionIndex + 1]
                           + " (precisa ter entrado ao menos uma vez)"));
                     return CompletableFuture.completedFuture((Void) null);
                  } else {
                     claim.removeTrusted(target);
                     store.markDirty();
                     ctx.sendMessage(Message.raw("Permissao removida de " + String.valueOf(target)));
                     return CompletableFuture.completedFuture((Void) null);
                  }
               }
            } else if (!action.equals("add") && !action.equals("set")) {
               this.sendTrustUsage(ctx);
               return CompletableFuture.completedFuture((Void) null);
            } else if (parts.length <= actionIndex + 1) {
               ctx.sendMessage(Message.raw("Uso: /totemprotecao trust <centerX> <centerZ> " + action
                     + " <playerName|playerUuid> [place|break|use|all|none]"));
               ctx.sendMessage(Message.raw("Uso: /totemprotecao trust here " + action
                     + " <playerName|playerUuid> [place|break|use|all|none]"));
               return CompletableFuture.completedFuture((Void) null);
            } else {
               target = this.resolvePlayerUuid(parts[actionIndex + 1]);
               if (target == null) {
                  ctx.sendMessage(Message.raw(
                        "Jogador desconhecido: " + parts[actionIndex + 1] + " (precisa ter entrado ao menos uma vez)"));
                  return CompletableFuture.completedFuture((Void) null);
               } else {
                  int perms;
                  if (parts.length > actionIndex + 2) {
                     perms = parsePerms(parts[actionIndex + 2]);
                  } else {
                     perms = 7;
                  }

                  claim.setTrusted(target, perms);
                  store.markDirty();
                  var10001 = String.valueOf(target);
                  ctx.sendMessage(Message
                        .raw("Permissoes atualizadas para " + var10001 + " perms=" + claim.getPermissionsFor(target)));
                  return CompletableFuture.completedFuture((Void) null);
               }
            }
         } else {
            Map<UUID, Integer> trusted = claim.getTrusted();
            if (trusted.isEmpty()) {
               ctx.sendMessage(Message.raw("Nenhum jogador confiavel nesta protecao"));
               return CompletableFuture.completedFuture((Void) null);
            } else {
               ctx.sendMessage(Message.raw("Jogadores confiaveis:"));
               Iterator var8 = trusted.entrySet().iterator();

               while (var8.hasNext()) {
                  Entry<UUID, Integer> e = (Entry) var8.next();
                  var10001 = String.valueOf(e.getKey());
                  ctx.sendMessage(Message.raw("- " + var10001 + " = " + String.valueOf(e.getValue())));
               }

               return CompletableFuture.completedFuture((Void) null);
            }
         }
      }
   }

   private void sendUsage(CommandContext ctx) {
      ctx.sendMessage(Message.raw("Uso: /totemprotecao radius <16|32|64|128>"));
      ctx.sendMessage(Message.raw("Uso: /totemprotecao maxclaims <1-5>"));
      ctx.sendMessage(Message.raw("Uso: /totemprotecao crafting <on|off>"));
      ctx.sendMessage(Message.raw("Uso: /totemprotecao trust <centerX> <centerZ> <list|add|remove|set> ..."));
      ctx.sendMessage(Message.raw("Uso: /totemprotecao trust here <list|add|remove|set> ..."));
   }

   private void sendTrustUsage(CommandContext ctx) {
      ctx.sendMessage(Message.raw("Uso: /totemprotecao trust <centerX> <centerZ> list"));
      ctx.sendMessage(Message.raw(
            "Uso: /totemprotecao trust <centerX> <centerZ> add <playerName|playerUuid> [place|break|use|all|none]"));
      ctx.sendMessage(Message.raw(
            "Uso: /totemprotecao trust <centerX> <centerZ> set <playerName|playerUuid> [place|break|use|all|none]"));
      ctx.sendMessage(Message.raw("Uso: /totemprotecao trust <centerX> <centerZ> remove <playerName|playerUuid>"));
      ctx.sendMessage(Message.raw("Uso: /totemprotecao trust here <list|add|remove|set> ..."));
   }

   private UUID resolvePlayerUuid(String raw) {
      if (raw == null) {
         return null;
      } else {
         String s = raw.trim();
         if (s.isEmpty()) {
            return null;
         } else {
            try {
               return UUID.fromString(s);
            } catch (Exception var4) {
               return this.plugin != null ? this.plugin.getKnownUuidForUsername(s) : null;
            }
         }
      }
   }

   private Claim resolveClaimAtSender(CommandContext ctx) {
      if (this.plugin != null && ctx != null && ctx.isPlayer()) {
         CommandSender sender;
         try {
            sender = ctx.sender();
         } catch (Exception var9) {
            sender = null;
         }

         if (sender == null) {
            return null;
         } else {
            Vector3d pos = null;

            try {
               Method m = sender.getClass().getMethod("getTransform");
               Object t = m.invoke(sender);
               if (t != null) {
                  Method pm = t.getClass().getMethod("getPosition");
                  Object p = pm.invoke(t);
                  if (p instanceof Vector3d) {
                     pos = (Vector3d) p;
                  }
               }
            } catch (Throwable var8) {
            }

            if (pos == null) {
               return null;
            } else {
               int x = (int) Math.floor(pos.x);
               int z = (int) Math.floor(pos.z);
               return this.plugin.getClaimStore().findClaimAt(x, z);
            }
         }
      } else {
         return null;
      }
   }

   private static int parsePerms(String raw) {
      if (raw == null) {
         return 0;
      } else {
         String r = raw.trim().toLowerCase();
         if (r.isEmpty()) {
            return 0;
         } else if (r.equals("none")) {
            return 0;
         } else if (r.equals("all")) {
            return 7;
         } else if (r.equals("place")) {
            return 1;
         } else if (r.equals("break")) {
            return 2;
         } else {
            return r.equals("use") ? 4 : 0;
         }
      }
   }

   private boolean isAllowed(CommandContext ctx) {
      if (!ctx.isPlayer()) {
         return true;
      } else {
         try {
            return this.plugin.isOpBypass(ctx.sender().getUuid());
         } catch (Exception var3) {
            return false;
         }
      }
   }
}
