package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class TotemProtecaoLeafToggleElement extends ChoiceElement {
   private static final String ELEMENT_LAYOUT = "Pages/ItemRepairElement.ui";
   private final TotemProtecaoPlugin plugin;

   public TotemProtecaoLeafToggleElement(TotemProtecaoPlugin plugin, int centerX, int centerZ) {
      this.plugin = plugin;
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
         commands.set(selector + " #Name.TextSpans", Message.raw("Efeito de folhas"));
         commands.set(selector + " #Durability.Text", "DESATIVADO");
      }
   }
}
