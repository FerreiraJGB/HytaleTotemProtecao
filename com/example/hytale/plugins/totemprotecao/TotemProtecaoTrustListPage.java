package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

public final class TotemProtecaoTrustListPage extends ChoiceBasePage {
   private static final String PAGE_LAYOUT = "Pages/TotemProtecaoZoneConfigPage.ui";
   private final TotemProtecaoPlugin plugin;
   private final int centerX;
   private final int centerZ;

   public TotemProtecaoTrustListPage(TotemProtecaoPlugin plugin, PlayerRef playerRef, int centerX, int centerZ) {
      super(playerRef, buildElements(plugin, centerX, centerZ), "Pages/TotemProtecaoZoneConfigPage.ui");
      this.plugin = plugin;
      this.centerX = centerX;
      this.centerZ = centerZ;
   }

   private static ChoiceElement[] buildElements(TotemProtecaoPlugin plugin, int centerX, int centerZ) {
      if (plugin == null) {
         return new ChoiceElement[0];
      } else {
         Claim claim = plugin.getClaimStore().findClaimByCenter(centerX, centerZ);
         if (claim == null) {
            return new ChoiceElement[0];
         } else {
            Map<UUID, Integer> trusted = claim.getTrusted();
            String iconId = plugin.getTotemProtecaoItemId();
            List<ChoiceElement> els = new ArrayList();
            els.add(new TotemProtecaoBorderToggleElement(plugin, centerX, centerZ));
            els.add(new TotemProtecaoProtectionStatusElement(plugin, centerX, centerZ));
            els.add(new TotemProtecaoProtectionToggleElement(plugin, centerX, centerZ));
            els.add(new TotemProtecaoTrustElement(plugin, centerX, centerZ, (UUID) null, 0, true));
            if (!trusted.isEmpty()) {
               List<Entry<UUID, Integer>> entries = new ArrayList(trusted.entrySet());
               Collections.sort(entries, Comparator.comparing((ex) -> {
                  return ((UUID) ex.getKey()).toString();
               }));
               Iterator var7 = entries.iterator();

               while (var7.hasNext()) {
                  Entry<UUID, Integer> e = (Entry) var7.next();
                  UUID u = (UUID) e.getKey();
                  Integer value = (Integer) e.getValue();
                  int perms = value == null ? 0 : value;
                  els.add(new TotemProtecaoTrustElement(plugin, centerX, centerZ, u, perms, false));
               }
            }

            els.add(new TotemProtecaoHeaderElement("PLAYERS IN AREA", "Click to add (ALL)", iconId));
            Map<UUID, String> inClaim = plugin.getPlayersInClaim(centerX, centerZ);
            if (!inClaim.isEmpty()) {
               List<Entry<UUID, String>> entries = new ArrayList(inClaim.entrySet());
               Collections.sort(entries, Comparator.comparing((ex) -> {
                  return ex.getValue() == null ? "" : ((String) ex.getValue()).toLowerCase();
               }));
               Iterator var14 = entries.iterator();

               while (var14.hasNext()) {
                  Entry<UUID, String> e = (Entry) var14.next();
                  UUID u = (UUID) e.getKey();
                  if (u != null && !u.equals(claim.getOwner()) && !trusted.containsKey(u)) {
                     String name = (String) e.getValue();
                     els.add(new TotemProtecaoPlayerCandidateElement(plugin, centerX, centerZ, u, name));
                  }
               }
            }

            return (ChoiceElement[]) els.toArray((x$0) -> {
               return new ChoiceElement[x$0];
            });
         }
      }
   }

   public TotemProtecaoPlugin getPlugin() {
      return this.plugin;
   }

   public int getCenterX() {
      return this.centerX;
   }

   public int getCenterZ() {
      return this.centerZ;
   }
}
