package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class TotemProtecaoHeaderElement extends ChoiceElement {
   private static final String ELEMENT_LAYOUT = "Pages/ItemRepairElement.ui";
   private final String title;
   private final String subtitle;
   private final String iconId;

   public TotemProtecaoHeaderElement(String title, String subtitle) {
      this.title = title;
      this.subtitle = subtitle;
      this.iconId = null;
      this.interactions = new ChoiceInteraction[0];
   }

   public TotemProtecaoHeaderElement(String title, String subtitle, String iconId) {
      this.title = title;
      this.subtitle = subtitle;
      this.iconId = iconId;
      this.interactions = new ChoiceInteraction[0];
   }

   public void addButton(UICommandBuilder commands, UIEventBuilder events, String selector, PlayerRef playerRef) {
      if (commands != null && selector != null) {
         commands.append("#ElementList", "Pages/ItemRepairElement.ui");
         if (this.iconId != null && !this.iconId.isEmpty()) {
            commands.set(selector + " #Icon.ItemId", this.iconId);
         }
         commands.set(selector + " #Name.TextSpans", Message.raw(this.title == null ? "" : this.title));
         commands.set(selector + " #Durability.Text", this.subtitle == null ? "" : this.subtitle);
      }
   }
}
