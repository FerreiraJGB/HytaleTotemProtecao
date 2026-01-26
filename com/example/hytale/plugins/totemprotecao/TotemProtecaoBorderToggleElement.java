package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class TotemProtecaoBorderToggleElement extends ChoiceElement {
   private static final String ELEMENT_LAYOUT = "Pages/ItemRepairElement.ui";
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;

   public TotemProtecaoBorderToggleElement(TotemProtecaoPlugin plugin, int centerX, int centerZ) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.interactions = new ChoiceInteraction[] {
            new TotemProtecaoBorderToggleInteraction(plugin, centerX, centerZ) };
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

         boolean on = false;
         if (this.plugin != null && playerRef != null && playerRef.getUuid() != null) {
            long key = TotemProtecaoPlugin.centerKey(this.centerX, this.centerZ);
            on = this.plugin.isBorderEnabled(playerRef.getUuid(), key);
         }

         commands.set(selector + " #Name.TextSpans", Message.raw("SHOW BORDER"));
         commands.set(selector + " #Durability.Text", on ? "ENABLED" : "DISABLED");
      }
   }
}
