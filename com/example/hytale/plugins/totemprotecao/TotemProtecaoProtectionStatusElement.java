package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class TotemProtecaoProtectionStatusElement extends ChoiceElement {
   private static final String ELEMENT_LAYOUT = "Pages/ItemRepairElement.ui";
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;

   public TotemProtecaoProtectionStatusElement(TotemProtecaoPlugin plugin, int centerX, int centerZ) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.interactions = new ChoiceInteraction[0];
   }

   public void addButton(UICommandBuilder commands, UIEventBuilder events, String selector, PlayerRef playerRef) {
      if (commands != null && selector != null) {
         commands.append("#ElementList", "Pages/ItemRepairElement.ui");
         if (this.plugin != null) {
            String iconId = this.plugin.getTotemProtecaoItemId();
            if (iconId != null && !iconId.isEmpty()) {
               commands.set(selector + " #Icon.ItemId", iconId);
            }
         }
         Claim claim = this.plugin == null ? null
               : this.plugin.getClaimStore().findClaimByCenter(this.centerX, this.centerZ);
         String statusText = "SEM TEMPO";
         if (claim != null) {
            long nowMs = System.currentTimeMillis();
            long remaining = claim.getProtectionRemainingMs(nowMs);
            if (claim.isProtectionPaused()) {
               statusText = "PAUSADA: " + TotemProtecaoPlugin.formatDuration(remaining);
            } else if (remaining > 0L) {
               statusText = "ATIVA: " + TotemProtecaoPlugin.formatDuration(remaining);
            }
         }

         commands.set(selector + " #Name.TextSpans", Message.raw("PROTEÇÃO"));
         commands.set(selector + " #Durability.Text", statusText);
      }
   }
}
