package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class TotemProtecaoPreloadCommand extends AbstractAsyncCommand {
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoPreloadCommand(TotemProtecaoPlugin plugin) {
      super("preload", "Recarga items de recarga del TotemProtecao");
      this.plugin = plugin;
   }

   protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
      if (!this.isAllowed(ctx)) {
         ctx.sendMessage(Message.raw("Sin permiso"));
         return CompletableFuture.completedFuture((Void) null);
      } else if (this.plugin == null) {
         ctx.sendMessage(Message.raw("Plugin no disponible"));
         return CompletableFuture.completedFuture((Void) null);
      } else {
         boolean ok = this.plugin.reloadRechargeItemsFromDisk(true);
         if (!ok) {
            ctx.sendMessage(Message.raw("Fallo al recargar " + this.plugin.getRechargeConfigRelativePath()));
            return CompletableFuture.completedFuture((Void) null);
         } else {
            List<TotemProtecaoPlugin.RechargeItemConfig> items = this.plugin.getRechargeItemConfigs();
            ctx.sendMessage(Message.raw("Items de recarga recargados: " + items.size() + "/2"));

            for (TotemProtecaoPlugin.RechargeItemConfig item : items) {
               if (item != null) {
                  ctx.sendMessage(Message.raw("- " + item.getItemId() + " = " + item.getDurationMinutes() + " min"));
               }
            }

            return CompletableFuture.completedFuture((Void) null);
         }
      }
   }

   private boolean isAllowed(CommandContext ctx) {
      if (!ctx.isPlayer()) {
         return true;
      } else {
         try {
            return this.plugin != null && this.plugin.isOpBypass(ctx.sender().getUuid());
         } catch (Exception var3) {
            return false;
         }
      }
   }
}
