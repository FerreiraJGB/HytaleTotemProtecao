package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Map;
import java.util.UUID;

public final class TotemProtecaoTrustElement extends ChoiceElement {
   private static final String ELEMENT_LAYOUT = "Pages/ItemRepairElement.ui";
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;
   private final UUID target;
   private final int perms;
   private final boolean header;

   public TotemProtecaoTrustElement(TotemProtecaoPlugin plugin, int centerX, int centerZ, UUID target, int perms,
         boolean header) {
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.target = target;
      this.perms = perms;
      this.header = header;
      if (!header && target != null) {
         this.interactions = new ChoiceInteraction[] {
               new TotemProtecaoTrustCycleInteraction(plugin, centerX, centerZ, target) };
      } else {
         this.interactions = new ChoiceInteraction[0];
      }

   }

   public void addButton(UICommandBuilder commands, UIEventBuilder events, String selector, PlayerRef playerRef) {
      if (commands != null && selector != null) {
         commands.append("#ElementList", "Pages/ItemRepairElement.ui");
         String iconId = this.plugin == null ? null : this.plugin.getTotemProtecaoItemId();
         if (iconId != null && !iconId.isEmpty()) {
            commands.set(selector + " #Icon.ItemId", iconId);
         }

         if (this.header) {
            commands.set(selector + " #Name.TextSpans", Message.raw("FRIENDS / PERMISSIONS"));
            commands.set(selector + " #Durability.Text", "Select a player to change permissions.");
         } else {
            String who = "<none>";
            if (this.target != null) {
               String cached = this.plugin == null ? null : this.plugin.getKnownUsername(this.target);
               if (cached != null && !cached.isEmpty()) {
                  who = cached;
               } else if (this.plugin != null) {
                  Map<UUID, String> inClaim = this.plugin.getPlayersInClaim(this.centerX, this.centerZ);
                  String present = (String) inClaim.get(this.target);
                  if (present != null && !present.isEmpty()) {
                     who = present;
                  } else {
                     who = this.target.toString();
                  }
               } else {
                  who = this.target.toString();
               }
            }

            commands.set(selector + " #Name.TextSpans", Message.raw(who));
            commands.set(selector + " #Durability.Text", permsToText(this.perms));
         }
      }
   }

   private static String permsToText(int perms) {
      boolean place = (perms & 1) != 0;
      boolean breakPerm = (perms & 2) != 0;
      boolean use = (perms & 4) != 0;
      StringBuilder sb = new StringBuilder();
      sb.append("P:").append(place ? "Y" : "N");
      sb.append(" B:").append(breakPerm ? "Y" : "N");
      sb.append(" U:").append(use ? "Y" : "N");
      return sb.toString();
   }
}
