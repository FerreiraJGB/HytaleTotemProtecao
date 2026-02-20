package com.example.hytale.plugins.totemprotecao;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
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

   public void build(Ref<EntityStore> ref, UICommandBuilder commands, UIEventBuilder events, Store<EntityStore> store) {
      super.build(ref, commands, events, store);
      this.applyRechargeFooter(commands);
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

            els.add(new TotemProtecaoHeaderElement("PESSOAS NA ÁREA", "Clique para adicionar (TODAS)", iconId));
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

   private void applyRechargeFooter(UICommandBuilder commands) {
      if (commands != null) {
         List<TotemProtecaoPlugin.RechargeItemConfig> items = this.plugin == null ? Collections.emptyList()
               : this.plugin.getRechargeItemConfigs();
         TotemProtecaoPlugin.RechargeItemConfig first = items.size() > 0 ? (TotemProtecaoPlugin.RechargeItemConfig) items.get(0) : null;
         TotemProtecaoPlugin.RechargeItemConfig second = items.size() > 1 ? (TotemProtecaoPlugin.RechargeItemConfig) items.get(1) : null;
         this.applyRechargeFooterLine(commands, "#FooterItem1Icon", "#FooterItem1Text", first);
         this.applyRechargeFooterLine(commands, "#FooterItem2Icon", "#FooterItem2Text", second);
      }
   }

   private void applyRechargeFooterLine(UICommandBuilder commands, String iconSelector, String textSelector,
         TotemProtecaoPlugin.RechargeItemConfig item) {
      if (commands != null && iconSelector != null && textSelector != null) {
         if (item == null || item.getItemId() == null || item.getItemId().isEmpty()) {
            commands.setNull(iconSelector + ".ItemId");
            commands.set(textSelector + ".TextSpans", Message.raw(""));
         } else {
            commands.set(iconSelector + ".ItemId", item.getItemId());
            String translationItemId = resolveTranslationItemId(item.getItemId());
            String durationText = TotemProtecaoPlugin.formatDuration(item.getDurationMs());
            if (translationItemId == null || translationItemId.isEmpty()) {
               commands.set(textSelector + ".TextSpans", Message.raw(item.getItemId() + " = " + durationText));
            } else {
               Message name = Message.translation("server.items." + translationItemId + ".name");
               commands.set(textSelector + ".TextSpans", Message.join(name, Message.raw(" = " + durationText)));
            }
         }
      }
   }

   private static String resolveTranslationItemId(String rawItemId) {
      if (rawItemId == null) {
         return null;
      } else {
         String normalized = rawItemId.trim();
         if (normalized.isEmpty()) {
            return null;
         } else {
            int namespaceSep = normalized.lastIndexOf(58);
            return namespaceSep > -1 && namespaceSep + 1 < normalized.length()
                  ? normalized.substring(namespaceSep + 1)
                  : normalized;
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
