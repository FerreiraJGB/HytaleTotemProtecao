package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;

public final class TotemProtecaoPlayerCandidateElement extends ChoiceElement {
   private static final String ELEMENT_LAYOUT = "Pages/ItemRepairElement.ui";
   private final TotemProtecaoPlugin plugin;
   private final UUID target;
   private final String username;

   public TotemProtecaoPlayerCandidateElement(TotemProtecaoPlugin plugin, int centerX, int centerZ, UUID target,
         String username) {
      this.plugin = plugin;
      this.target = target;
      this.username = username;
      this.interactions = target == null ? new ChoiceInteraction[0]
            : new ChoiceInteraction[] {
                  new TotemProtecaoTrustAddInteraction(plugin, centerX, centerZ, target, username) };
   }

   public void addButton(UICommandBuilder commands, UIEventBuilder events, String selector, PlayerRef playerRef) {
      if (commands != null && selector != null) {
         commands.append("#ElementList", "Pages/ItemRepairElement.ui");
         String who;
         if (this.plugin != null) {
            who = this.plugin.getTotemProtecaoItemId();
            if (who != null && !who.isEmpty()) {
               commands.set(selector + " #Icon.ItemId", who);
            }
         }

         who = this.username;
         if (who == null || who.isEmpty()) {
            who = this.target == null ? "<desconocido>" : this.target.toString();
         }

         commands.set(selector + " #Name.TextSpans", Message.raw(who));
         commands.set(selector + " #Durability.Text", "Haz clic para agregar (TODO)");
      }
   }
}
