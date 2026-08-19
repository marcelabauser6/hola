package com.fantasticchameleon.client;

import com.fantasticchameleon.game.EditorActions;
import com.fantasticchameleon.item.CreatorSets;
import com.fantasticchameleon.network.ArenaListPayload;
import com.fantasticchameleon.network.RoomMember;
import com.fantasticchameleon.network.RoomSummary;
import com.fantasticchameleon.network.RoomsPayload;
import com.fantasticchameleon.pose.PropShapes;
import com.fantasticchameleon.prophunt.PropHunt;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button.Builder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FantasticEditorScreen extends Screen {
   private static final int MAX_W = 540;
   private static final int BASE_H = 320;
   private static final int TAB_H = 18;
   private static final int TAB_PITCH = 20;
   private static final int TAB_GAP = 2;
   private static final int ROW = 20;
   private static final int FIELD_H = 16;
   private FantasticEditorScreen.Tab tab;
   private int leftPos;
   private int topPos;
   private int panelWidth;
   private int panelHeight;
   private int headerRowCount = 1;
   private String helpLine = "";
   private final List<FantasticEditorScreen.Label> labels = new ArrayList<>();
   private final List<FantasticEditorScreen.TooltipZone> zones = new ArrayList<>();
   private int scroll;
   private int contentHeight;
   private int cursor;
   private String roomDraft = "";
   private String passDraft = "";
   /** Botones reactivos; su estado cambia sin reconstruir ni perder foco/cursor. */
   private Button createButton;
   private Button inviteButton;
   private Button renameButton;
   private String renameTarget = "";
   private RoomsPayload lastRooms;
   private boolean lastInRoom;
   private String inviteDraft = "";
   private String arenaDraft = "";
   private String arenaDraftFor = "";
   private String armedDelete = "";

   public FantasticEditorScreen() {
      this(FantasticEditorScreen.Tab.SALAS);
   }

   public FantasticEditorScreen(FantasticEditorScreen.Tab tab) {
      super(Component.m_237115_("fantastic.editor.title"));
      this.tab = tab;
   }

   public static void openTab(int ordinal) {
      FantasticEditorScreen.Tab[] tabs = FantasticEditorScreen.Tab.values();
      FantasticEditorScreen.Tab target = tabs[Math.max(0, Math.min(tabs.length - 1, ordinal))];
      Minecraft mc = Minecraft.m_91087_();
      if (ClientShims.screen(mc) instanceof FantasticEditorScreen open) {
         open.show(target);
      } else {
         RoomMenu.request();
         ArenaMenu.refresh();
         mc.m_91152_(new FantasticEditorScreen(target));
      }
   }

   public void rebuild() {
      this.m_232761_();
   }

   private void show(FantasticEditorScreen.Tab next) {
      if (this.tab != next) {
         this.tab = next;
         this.scroll = 0;
         FantasticSound.tap();
      }

      if (next == FantasticEditorScreen.Tab.ARENA) {
         ArenaMenu.refresh();
      }

      RoomMenu.request();
      this.m_232761_();
   }

   public boolean m_7043_() {
      return false;
   }

   private int bodyX() {
      return this.leftPos + 8;
   }

   private int bodyY() {
      return this.topPos + 62 + (this.headerRowCount - 1) * 20;
   }

   private int bodyW() {
      return this.panelWidth - 16;
   }

   private int bodyH() {
      return this.panelHeight - 62 - 28 - (this.headerRowCount - 1) * 20;
   }

   private int separatorY() {
      return this.topPos + 24 + this.headerRowCount * 20 + 2;
   }

   private List<List<FantasticEditorScreen.Tab>> packTabs() {
      FantasticEditorScreen.Tab[] tabs = FantasticEditorScreen.Tab.values();
      int available = this.panelWidth - 16;
      int widest = 0;

      for (FantasticEditorScreen.Tab t : tabs) {
         widest = Math.max(widest, this.f_96547_.m_92895_(t.label()));
      }

      int minTab = Math.min(Math.max(38, widest + 10), Math.max(38, available / 4));
      int perRow = Math.max(1, Math.min(tabs.length, (available + 2) / (minTab + 2)));
      int rowCount = (tabs.length + perRow - 1) / perRow;
      perRow = (tabs.length + rowCount - 1) / rowCount;
      List<List<FantasticEditorScreen.Tab>> rows = new ArrayList<>();

      for (int i = 0; i < tabs.length; i += perRow) {
         rows.add(new ArrayList<>(List.of(tabs).subList(i, Math.min(tabs.length, i + perRow))));
      }

      return rows;
   }

   protected void m_7856_() {
      this.panelWidth = Math.min(this.f_96543_ - 16, 540);
      this.headerRowCount = Math.max(1, this.packTabs().size());
      this.panelHeight = Math.min(this.f_96544_ - 16, 320 + (this.headerRowCount - 1) * 20);
      this.leftPos = (this.f_96543_ - this.panelWidth) / 2;
      this.topPos = (this.f_96544_ - this.panelHeight) / 2;
      this.labels.clear();
      this.zones.clear();
      this.createButton = null;
      this.inviteButton = null;
      this.renameButton = null;
      RoomsPayload currentRooms = RoomMenu.current;
      this.lastRooms = currentRooms;
      this.lastInRoom = currentRooms != null && !currentRooms.yourRoom().isBlank();
      this.initHeader();
      this.initFooter();
      this.cursor = 0;
      int x = this.bodyX();
      int w = this.bodyW();
      switch (this.tab) {
         case SALAS:
            this.initRooms(x, w);
            break;
         case PARTIDA:
            this.initMatch(x, w);
            break;
         case REGLAS:
            this.initRules(x, w);
            break;
         case ARENA:
            this.initArena(x, w);
            break;
         case SERVIDOR:
            this.initServer(x, w);
            break;
         case ASPECTO:
            this.initLook(x, w);
      }

      this.contentHeight = this.cursor;
      int maxScroll = Math.max(0, this.contentHeight - this.bodyH());
      if (this.scroll > maxScroll) {
         this.scroll = maxScroll;
      }
   }

   private void initHeader() {
      List<List<FantasticEditorScreen.Tab>> rows = this.packTabs();
      int available = this.panelWidth - 16;

      for (int r = 0; r < rows.size(); r++) {
         List<FantasticEditorScreen.Tab> row = rows.get(r);
         int count = row.size();
         int w = (available - 2 * (count - 1)) / count;
         int y = this.topPos + 24 + r * 20;

         for (int i = 0; i < count; i++) {
            FantasticEditorScreen.Tab t = row.get(i);
            int x = this.leftPos + 8 + i * (w + 2);
            boolean active = t == this.tab;
            String label = t.label();
            int room = w - 6;
            if (this.f_96547_.m_92895_(label) > room) {
               label = this.f_96547_.m_92834_(label, Math.max(4, room));
            }

            this.m_142416_(
               Button.m_253074_(Component.m_237113_((active ? "§f§l" : "§7") + label), b -> this.show(t))
                  .m_252987_(x, y, w, 18)
                  .m_257505_(Tooltip.m_257550_(Component.m_237115_(t.desc)))
                  .m_253136_()
            );
         }
      }
   }

   private void initFooter() {
      int y = this.topPos + this.panelHeight - 24;
      this.m_142416_(
         Button.m_253074_(Component.m_237115_("fantastic.editor.close"), b -> this.m_7379_())
            .m_252987_(this.leftPos + 8, y, 80, 18)
            .m_257505_(Tooltip.m_257550_(Component.m_237115_("fantastic.editor.close.tip")))
            .m_253136_()
      );
      this.m_142416_(Button.m_253074_(Component.m_237115_("fantastic.ui.save"), b -> {
         EditorNet.send("room.save");
         ArenaMenu.refresh();
         FantasticSound.tap();
      }).m_252987_(this.leftPos + this.panelWidth - 158, y, 150, 18).m_257505_(Tooltip.m_257550_(Component.m_237115_("fantastic.ui.save.tip"))).m_253136_());
   }

   /** Actualiza estados simples sin mutar la lista de widgets durante render. */
   private void refreshLiveState() {
      RoomsPayload data = RoomMenu.current;
      boolean inRoom = data != null && !data.yourRoom().isBlank();
      this.lastRooms = data;
      this.lastInRoom = inRoom;
      if (this.createButton != null) {
         this.createButton.f_93623_ = !this.roomDraft.isBlank() && !inRoom;
      }
      if (this.inviteButton != null) {
         this.inviteButton.f_93623_ = !this.inviteDraft.isBlank() && inRoom;
      }
      if (this.renameButton != null) {
         this.renameButton.f_93623_ = !this.arenaDraft.isBlank() && !this.arenaDraft.equalsIgnoreCase(this.renameTarget);
      }
   }

   public void m_88315_(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.refreshLiveState();
      this.m_280273_(g);
      FsGui.panel(g, this.leftPos, this.topPos, this.panelWidth, this.panelHeight);
      g.m_280509_(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + 20, -14408646);
      g.m_280509_(this.leftPos, this.topPos + this.panelHeight - 1, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -12961206);
      int sepY = this.separatorY();
      g.m_280509_(this.leftPos + 6, sepY, this.leftPos + this.panelWidth - 6, sepY + 1, -12961206);
      RoomsPayload data = RoomMenu.current;
      String room = data == null ? "" : data.yourRoom();
      String title = "§a✦ §fFantastic Chameleon §a✦" + (room.isBlank() ? "" : " §7- " + room);
      g.m_280056_(this.f_96547_, title, this.leftPos + 8, this.topPos + 6, 16777215, false);
      if (!this.helpLine.isEmpty()) {
         g.m_280056_(this.f_96547_, this.f_96547_.m_92834_("§7" + this.helpLine, this.panelWidth - 16), this.leftPos + 8, sepY + 4, 10133680, false);
      }

      FsGui.inset(g, this.bodyX() - 2, this.bodyY() - 3, this.bodyW() + 4, this.bodyH() + 6);
      super.m_88315_(g, mouseX, mouseY, partial);

      for (FantasticEditorScreen.Label l : this.labels) {
         g.m_280056_(this.f_96547_, l.text(), l.x(), l.y(), 14737632, false);
      }

      if (this.contentHeight > this.bodyH()) {
         int trackH = this.bodyH();
         int thumb = Math.max(12, trackH * trackH / this.contentHeight);
         int maxScroll = Math.max(1, this.contentHeight - trackH);
         FsGui.scrollbar(
            g, this.leftPos + this.panelWidth - 6, this.bodyY(), 4, trackH, this.bodyY() + (trackH - thumb) * this.scroll / maxScroll, thumb, false
         );
      }

      for (FantasticEditorScreen.TooltipZone z : this.zones) {
         if (mouseX >= z.x() && mouseX < z.x() + z.w() && mouseY >= z.y() && mouseY < z.y() + z.h()) {
            g.m_280666_(this.f_96547_, z.lines(), mouseX, mouseY);
            break;
         }
      }
   }

   public boolean m_6050_(double mx, double my, double delta) {
      int maxScroll = Math.max(0, this.contentHeight - this.bodyH());
      if (maxScroll > 0
         && mx >= (double)this.leftPos
         && mx < (double)(this.leftPos + this.panelWidth)
         && my >= (double)this.bodyY()
         && my < (double)(this.bodyY() + this.bodyH())) {
         int next = Math.max(0, Math.min(maxScroll, this.scroll - (int)(delta * 20.0)));
         if (next != this.scroll) {
            this.scroll = next;
            this.m_232761_();
         }

         return true;
      } else {
         return super.m_6050_(mx, my, delta);
      }
   }

   private int y() {
      int y = this.bodyY() + this.cursor - this.scroll;
      this.cursor += 20;
      return y;
   }

   private void gap(int px) {
      this.cursor += px;
   }

   private <T extends AbstractWidget> T add(T widget) {
      if (widget.m_252907_() < this.bodyY() - 1 || widget.m_252907_() + widget.m_93694_() > this.bodyY() + this.bodyH() + 1) {
         widget.f_93624_ = false;
         widget.f_93623_ = false;
      }

      return (T)this.m_142416_(widget);
   }

   private void label(String text, int x, int y, String tipKey) {
      this.labels.add(new FantasticEditorScreen.Label(text, x, y));
      if (tipKey != null) {
         this.zones.add(new FantasticEditorScreen.TooltipZone(x, y - 2, Math.max(120, this.f_96547_.m_92895_(text) + 8), 12, wrap(tipKey)));
      }
   }

   private static List<Component> wrap(String key) {
      List<Component> out = new ArrayList<>();

      for (String line : Component.m_237115_(key).getString().split("\n")) {
         out.add(Component.m_237113_(line));
      }

      return out;
   }

   private Button btn(int x, int y, int w, Component label, Runnable onPress, String tipKey) {
      Builder b = Button.m_253074_(label, p -> onPress.run()).m_252987_(x, y, w, 18);
      if (tipKey != null) {
         b.m_257505_(Tooltip.m_257550_(Component.m_237115_(tipKey)));
      }

      return b.m_253136_();
   }

   private void toggle(int x, int y, int w, String labelKey, boolean on, Runnable onPress, String tipKey) {
      Component label = Component.m_237115_(labelKey)
         .m_7220_(Component.m_237113_(": "))
         .m_7220_(Component.m_237115_(on ? "fantastic.global.on" : "fantastic.global.off").m_130940_(on ? ChatFormatting.GREEN : ChatFormatting.RED));
      this.add(this.btn(x, y, w, label, onPress, tipKey));
   }

   private void slider(int x, int y, int w, String field, String labelKey, int min, int max, int step, int current, IntFunction<String> fmt, boolean editable) {
      FantasticSlider s = new FantasticSlider(x, y, w, 18, field, labelKey, min, max, step, current, fmt);
      s.f_93623_ = editable;
      this.add(s);
   }

   private void slider(int x, int y, int w, String labelKey, int min, int max, int step, int current, IntFunction<String> fmt, IntConsumer sink) {
      this.add(new FantasticSlider(x, y, w, 18, labelKey, labelKey, min, max, step, current, fmt).onCommit(sink));
   }

   private EditBox field(int x, int y, int w, String hintKey, String value, Consumer<String> onChange, int maxLen) {
      EditBox box = new EditBox(this.f_96547_, x, y + 1, w, 16, Component.m_237115_(hintKey));
      box.m_257771_(Component.m_237115_(hintKey));
      box.m_94199_(maxLen);
      box.m_94144_(value);
      box.m_94151_(onChange);
      box.m_257544_(Tooltip.m_257550_(Component.m_237115_(hintKey + ".tip")));
      return this.add(box);
   }

   private void pickBtn(int x, int y, int w, String labelKey, String kind, String arg, String tipKey, boolean enabled, String lockedTip) {
      Button b = Button.m_253074_(Component.m_237115_(labelKey), p -> EditorNet.pick(kind, arg, this.tab))
         .m_252987_(x, y, w, 18)
         .m_257505_(
            Tooltip.m_257550_(
               enabled
                  ? Component.m_237115_(tipKey).m_7220_(Component.m_237113_("\n")).m_7220_(Component.m_237115_("fantastic.pick.how"))
                  : Component.m_237115_(lockedTip)
            )
         )
         .m_253136_();
      b.f_93623_ = enabled;
      this.add(b);
   }

   private void pickBtn(int x, int y, int w, String labelKey, String kind, String arg, String tipKey) {
      this.pickBtn(x, y, w, labelKey, kind, arg, tipKey, true, tipKey);
   }

   private static int at(int[] a, int i, int fallback) {
      return a != null && a.length > i ? a[i] : fallback;
   }

   static String clockLabel(int seconds) {
      if (seconds >= 3600) {
         int hours = seconds / 3600;
         int mins = seconds % 3600 / 60;
         return mins == 0
            ? Component.m_237110_("fantastic.ui.cfg_hours", new Object[]{hours}).getString()
            : Component.m_237110_("fantastic.ui.cfg_hours_mins", new Object[]{hours, mins}).getString();
      } else if (seconds >= 60) {
         int mins = seconds / 60;
         int secs = seconds % 60;
         return secs == 0
            ? Component.m_237110_("fantastic.ui.cfg_mins", new Object[]{mins}).getString()
            : Component.m_237110_("fantastic.ui.cfg_mins_secs", new Object[]{mins, secs}).getString();
      } else {
         return Component.m_237110_("fantastic.ui.cfg_secs", new Object[]{seconds}).getString();
      }
   }

   private void initRooms(int x, int w) {
      this.helpLine = Component.m_237115_("fantastic.help.rooms").getString();
      RoomsPayload data = RoomMenu.current;
      boolean inRoom = data != null && !data.yourRoom().isBlank();
      int half = (w - 6) / 2;
      int createX;
      int createY;
      if (w < 260) {
         int nameY = this.y();
         this.field(x, nameY, w, "fantastic.editor.room_name", this.roomDraft, s -> this.roomDraft = s, 24);
         createY = this.y();
         this.field(x, createY, Math.max(48, w - 74), "fantastic.editor.room_pass", this.passDraft, s -> this.passDraft = s, 24);
         createX = x + w - 68;
      } else {
         createY = this.y();
         this.field(x, createY, Math.max(48, half - 74), "fantastic.editor.room_name", this.roomDraft, s -> this.roomDraft = s, 24);
         this.field(x + half - 68, createY, Math.max(48, half - 68), "fantastic.editor.room_pass", this.passDraft, s -> this.passDraft = s, 24);
         createX = x + w - 68;
      }
      Button create = this.btn(createX, createY, 68, Component.m_237115_("fantastic.editor.create"), () -> {
         EditorNet.send("room.create", this.roomDraft, this.passDraft);
         this.roomDraft = "";
         this.passDraft = "";
         this.show(FantasticEditorScreen.Tab.PARTIDA);
      }, "fantastic.editor.create.tip");
      create.f_93623_ = !this.roomDraft.isBlank() && !inRoom;
      // Se guarda para poder reevaluarlo cada frame: el estado se calculaba solo al construir la
      // pantalla, así que escribir el nombre no habilitaba el botón y parecía que no se podía crear.
      this.createButton = create;
      this.add(create);
      this.gap(6);
      if (inRoom) {
         int y2 = this.y();
         this.add(
            this.btn(
               x, y2, half, Component.m_237115_("fantastic.editor.manage"), () -> this.show(FantasticEditorScreen.Tab.PARTIDA), "fantastic.tab.match.desc"
            )
         );
         this.add(
            this.btn(x + half + 6, y2, w - half - 6, Component.m_237115_("fantastic.ui.leave"), () -> EditorNet.send("room.leave"), "fantastic.ui.leave.tip")
         );
         this.gap(6);
      }

      List<RoomSummary> rooms = data == null ? List.of() : data.rooms();
      if (rooms.isEmpty()) {
         this.label(Component.m_237115_("fantastic.room.list_empty").getString(), x, this.y() + 5, null);
      }

      for (RoomSummary r : rooms) {
         int ry = this.y();
         String name = r.name();
         boolean full = r.size() >= r.cap();
         boolean locked = r.locked();
         int act = 62;
         int labelW = w - act * 2 - 12;
         this.label((r.featured() ? "§6★ §r" : "") + name + "  §7" + r.size() + "/" + r.cap(), x + 2, ry + 5, phaseKey(r.phase()));
         if (r.member()) {
            this.add(
               this.btn(
                  x + labelW + 6,
                  ry,
                  act,
                  Component.m_237115_("fantastic.editor.manage"),
                  () -> this.show(FantasticEditorScreen.Tab.PARTIDA),
                  "fantastic.ui.manage.tip"
               )
            );
         } else {
            Button join = this.btn(
               x + labelW + 6,
               ry,
               act,
               Component.m_237115_(full ? "fantastic.ui.full" : (r.phase() == 0 ? "fantastic.ui.join" : "fantastic.ui.spectate")),
               () -> {
                  RoomMenu.openPanelAfterJoin();
                  EditorNet.send("room.join", name, locked ? this.passDraft : "");
               },
               full ? "fantastic.ui.full.tip" : (locked ? "fantastic.ui.join_locked.tip" : "fantastic.ui.join.tip")
            );
            join.f_93623_ = !full;
            this.add(join);
         }

         boolean armed = name.equalsIgnoreCase(this.armedDelete);
         this.add(this.btn(x + labelW + act + 12, ry, act, Component.m_237115_(armed ? "fantastic.editor.confirm" : "fantastic.editor.delete"), () -> {
            if (armed) {
               this.armedDelete = "";
               EditorNet.send("room.delete", name, "", 1);
            } else {
               this.armedDelete = name;
               this.m_232761_();
            }
         }, armed ? "fantastic.editor.confirm.tip" : "fantastic.ui.room_delete.tip"));
      }
   }

   private static String phaseKey(int phase) {
      return switch (phase) {
         case 0 -> "fantastic.editor.phase_lobby";
         case 1 -> "fantastic.editor.phase_hiding";
         default -> "fantastic.editor.phase_seeking";
      };
   }

   private void initMatch(int x, int w) {
      this.helpLine = Component.m_237115_("fantastic.help.match").getString();
      RoomsPayload data = RoomMenu.current;
      if (data != null && !data.yourRoom().isBlank()) {
         int[] cfg = data.config();
         boolean manage = data.canManage();
         int third = (w - 12) / 3;
         RoomSummary mine = mineOf(data);
         int role = mine == null ? -1 : mine.role();
         int y = this.y();
         this.add(
            this.btn(
               x,
               y,
               third,
               Component.m_237110_("fantastic.ui.hiders", new Object[]{data.hiderCount(), at(cfg, 5, 0)}),
               () -> EditorNet.send("room.role", "hider"),
               "fantastic.ui.hiders.tip"
            )
         );
         this.add(
            this.btn(
               x + third + 6,
               y,
               third,
               Component.m_237110_("fantastic.ui.seekers", new Object[]{data.seekerCount(), at(cfg, 2, 0)}),
               () -> EditorNet.send("room.role", "seeker"),
               "fantastic.ui.seekers.tip"
            )
         );
         Button spec = this.btn(
            x + (third + 6) * 2,
            y,
            w - (third + 6) * 2,
            Component.m_237115_(role == 2 ? "fantastic.ui.spectating" : "fantastic.ui.spectate"),
            () -> EditorNet.send("room.spectate"),
            role != 2 && !ClientGlobalSettings.allowSpectate ? "fantastic.ui.spectate.locked" : "fantastic.ui.spectate.tip"
         );
         spec.f_93623_ = ClientGlobalSettings.allowSpectate || role == 2;
         this.add(spec);
         this.label("§7" + Component.m_237115_(roleKey(role)).getString(), x + 2, this.y() + 5, null);
         this.gap(4);
         int y2 = this.y();
         Button start = this.btn(x, y2, (w - 6) / 2, Component.m_237115_("fantastic.ui.start"), () -> EditorNet.send("room.start"), "fantastic.ui.start.tip");
         start.f_93623_ = manage;
         this.add(start);
         Button stop = this.btn(
            x + (w - 6) / 2 + 6,
            y2,
            w - (w - 6) / 2 - 6,
            Component.m_237115_("fantastic.editor.stop"),
            () -> EditorNet.send("room.stop"),
            "fantastic.editor.stop.tip"
         );
         stop.f_93623_ = manage;
         this.add(stop);
         this.gap(8);
         int y3 = this.y();
         this.add(this.btn(x, y3, third, Component.m_237115_("fantastic.ui.add_bot"), () -> EditorNet.send("dummy.spawn", "", 1), "fantastic.ui.add_bot.tip"));
         this.add(this.btn(x + third + 6, y3, third, Component.m_237113_("+5"), () -> EditorNet.send("dummy.spawn", "", 5), "fantastic.ui.add_bot.tip"));
         this.add(
            this.btn(
               x + (third + 6) * 2,
               y3,
               w - (third + 6) * 2,
               Component.m_237115_("fantastic.ui.clear_bots"),
               () -> EditorNet.send("dummy.clear"),
               "fantastic.ui.clear_bots.tip"
            )
         );
         this.gap(8);
         if (mine != null) {
            String self = Minecraft.m_91087_().f_91074_ == null ? "" : Minecraft.m_91087_().f_91074_.m_36316_().getName();

            for (RoomMember m : mine.members()) {
               int my = this.y();
               String who = m.name();
               boolean removable = manage && !who.equalsIgnoreCase(self) && !m.leader();
               int act = 44;
               this.label((m.leader() ? "§6✦ §r" : "") + who, x + 2, my + 5, m.leader() ? "fantastic.editor.leader" : "fantastic.editor.member");
               int bx = x + w - act * 4 - 18;
               Button toHider = this.btn(
                  bx,
                  my,
                  act,
                  Component.m_237115_("fantastic.editor.to_hider"),
                  () -> EditorNet.send("room.setrole", who, "hider"),
                  "fantastic.editor.to_hider.tip"
               );
               toHider.f_93623_ = manage;
               this.add(toHider);
               Button toSeeker = this.btn(
                  bx + act + 6,
                  my,
                  act,
                  Component.m_237115_("fantastic.editor.to_seeker"),
                  () -> EditorNet.send("room.setrole", who, "seeker"),
                  "fantastic.editor.to_seeker.tip"
               );
               toSeeker.f_93623_ = manage;
               this.add(toSeeker);
               Button kick = this.btn(
                  bx + (act + 6) * 2,
                  my,
                  act,
                  Component.m_237115_("fantastic.ui.kick"),
                  () -> EditorNet.send("room.kick", who),
                  removable ? "fantastic.ui.kick.tip" : "fantastic.editor.cannot_remove"
               );
               kick.f_93623_ = removable;
               this.add(kick);
               Button ban = this.btn(
                  bx + (act + 6) * 3,
                  my,
                  act,
                  Component.m_237115_("fantastic.ui.ban"),
                  () -> EditorNet.send("room.ban", who),
                  removable ? "fantastic.ui.ban.tip" : "fantastic.editor.cannot_remove"
               );
               ban.f_93623_ = removable;
               this.add(ban);
            }
         }

         this.gap(8);
         int iy = this.y();
         this.field(x, iy, w - 74, "fantastic.editor.invite_who", this.inviteDraft, v -> this.inviteDraft = v, 16);
         Button invite = this.btn(x + w - 68, iy, 68, Component.m_237115_("fantastic.ui.invite"), () -> {
            EditorNet.send("room.invite", this.inviteDraft);
            this.inviteDraft = "";
         }, "fantastic.ui.invite.tip");
         invite.f_93623_ = !this.inviteDraft.isBlank();
         this.inviteButton = invite;
         this.add(invite);
         int y4 = this.y();
         this.add(
            this.btn(
               x,
               y4,
               (w - 6) / 2,
               Component.m_237115_("fantastic.editor.bring_bots"),
               () -> EditorNet.send("room.forcejoin.all", data.yourRoom()),
               "fantastic.editor.bring_bots.tip"
            )
         );
         this.add(
            this.btn(
               x + (w - 6) / 2 + 6,
               y4,
               w - (w - 6) / 2 - 6,
               Component.m_237115_("fantastic.editor.my_stats"),
               () -> EditorNet.send("stats"),
               "fantastic.editor.my_stats.tip"
            )
         );
      } else {
         this.add(
            this.btn(
               x, this.y(), w, Component.m_237115_("fantastic.editor.no_room"), () -> this.show(FantasticEditorScreen.Tab.SALAS), "fantastic.tab.rooms.desc"
            )
         );
      }
   }

   private static String roleKey(int role) {
      return switch (role) {
         case 0 -> "fantastic.editor.you_hider";
         case 1 -> "fantastic.editor.you_seeker";
         case 2 -> "fantastic.editor.you_spectator";
         default -> "fantastic.editor.you_none";
      };
   }

   private static RoomSummary mineOf(RoomsPayload data) {
      for (RoomSummary r : data.rooms()) {
         if (r.name().equalsIgnoreCase(data.yourRoom())) {
            return r;
         }
      }

      return null;
   }

   private void initRules(int x, int w) {
      this.helpLine = Component.m_237115_("fantastic.help.rules").getString();
      RoomsPayload data = RoomMenu.current;
      if (data != null && !data.yourRoom().isBlank()) {
         int[] cfg = data.config();
         boolean manage = data.canManage();
         String off = Component.m_237115_("fantastic.global.off").getString();

         // Modo de juego: gobierna el resto de la partida, asi que va primero.
         int mode = at(cfg, PropHunt.CFG_INDEX, PropHunt.MODE_MECCHA);
         boolean propHunt = mode == PropHunt.MODE_PROP_HUNT;
         Component modeLabel = Component.m_237115_("fantastic.ui.gamemode")
            .m_7220_(Component.m_237113_(": "))
            .m_7220_(Component.m_237115_(PropHunt.nameKey(mode)).m_130940_(propHunt ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
         Button modeBtn = this.btn(
            x,
            this.y(),
            w,
            modeLabel,
            () -> EditorNet.send("room.config", "gamemode", propHunt ? PropHunt.MODE_MECCHA : PropHunt.MODE_PROP_HUNT),
            "fantastic.ui.gamemode.tip"
         );
         modeBtn.f_93623_ = manage;
         this.add(modeBtn);
         this.gap(6);

         this.slider(x, this.y(), w, "hide", "fantastic.ui.cfg_hide", 10, 3600, 10, at(cfg, 0, 30), FantasticEditorScreen::clockLabel, manage);
         this.slider(x, this.y(), w, "seek", "fantastic.ui.cfg_seek", 30, 7200, 30, at(cfg, 1, 180), FantasticEditorScreen::clockLabel, manage);
         this.gap(4);
         this.slider(x, this.y(), w, "maxseekers", "fantastic.ui.cfg_seekers", 1, 32, 1, at(cfg, 2, 1), String::valueOf, manage);
         this.slider(x, this.y(), w, "maxplayers", "fantastic.ui.cfg_players", 2, 64, 2, at(cfg, 6, 16), String::valueOf, manage);
         this.gap(4);
         this.slider(x, this.y(), w, "whistle", "fantastic.ui.cfg_whistle", 0, 1800, 15, at(cfg, 3, 15), v -> v == 0 ? off : clockLabel(v), manage);
         this.slider(x, this.y(), w, "whistlewindow", "fantastic.ui.cfg_whistle_window", 5, 100, 5, at(cfg, 15, 30), v -> v + "%", manage);
         this.slider(x, this.y(), w, "reveal", "fantastic.ui.cfg_reveal", 0, 60, 5, at(cfg, 4, 20), v -> v + "s", manage);
         this.slider(x, this.y(), w, "ammo", "fantastic.ui.ammo", 0, 32, 1, at(cfg, 8, 0), v -> v == 0 ? "∞" : String.valueOf(v), manage);
         this.slider(x, this.y(), w, "shotcd", "fantastic.ui.shot_cd", 5, 200, 5, at(cfg, 9, 30), v -> String.format("%.2fs", (double)v / 20.0), manage);
         this.gap(4);
         int pool = at(cfg, 19, 0);
         this.slider(x, this.y(), w, "pool", "fantastic.ui.cfg_pool", 0, 300, 10, pool, v -> v == 0 ? off : v + " pts", manage);
         this.slider(x, this.y(), w, "pooldecay", "fantastic.ui.cfg_pool_decay", 0, 10, 1, at(cfg, 20, 1), v -> v + "/s", manage && pool > 0);
         this.gap(6);
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.manual_roles",
            at(cfg, 13, 0) != 0,
            () -> EditorNet.send("room.config", "manualroles", at(cfg, 13, 0) != 0 ? 0 : 1),
            "fantastic.ui.manual_roles.tip"
         );
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.infection",
            at(cfg, 10, 0) != 0,
            () -> EditorNet.send("room.config", "infection", at(cfg, 10, 0) != 0 ? 0 : 1),
            "fantastic.ui.infection.tip"
         );
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.elim_dim",
            at(cfg, 11, 0) != 0,
            () -> EditorNet.send("room.config", "elimdimension", at(cfg, 11, 0) != 0 ? 0 : 1),
            "fantastic.ui.elim_dim.tip"
         );
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.texture_brush",
            at(cfg, 7, 0) != 0,
            () -> EditorNet.send("room.config", "texturebrush", at(cfg, 7, 0) != 0 ? 0 : 1),
            "fantastic.ui.texture_brush.tip"
         );
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.shot_penalty",
            at(cfg, 16, 0) != 0,
            () -> EditorNet.send("room.config", "shotpenalty", at(cfg, 16, 0) != 0 ? 0 : 1),
            "fantastic.ui.shot_penalty.tip"
         );
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.sight_slow",
            at(cfg, 17, 0) != 0,
            () -> EditorNet.send("room.config", "sightslow", at(cfg, 17, 0) != 0 ? 0 : 1),
            "fantastic.ui.sight_slow.tip"
         );
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.ui.whistle_arrow",
            at(cfg, 18, 0) != 0,
            () -> EditorNet.send("room.config", "whistlearrow", at(cfg, 18, 0) != 0 ? 0 : 1),
            "fantastic.ui.whistle_arrow.tip"
         );
      } else {
         this.add(
            this.btn(
               x, this.y(), w, Component.m_237115_("fantastic.editor.no_room"), () -> this.show(FantasticEditorScreen.Tab.SALAS), "fantastic.tab.rooms.desc"
            )
         );
      }
   }

   private void initArena(int x, int w) {
      this.helpLine = Component.m_237115_("fantastic.help.arena").getString();
      RoomsPayload data = RoomMenu.current;
      String target = ArenaMenu.target();
      int half = (w - 6) / 2;
      int third = (w - 12) / 3;
      boolean inRoom = data != null && !data.yourRoom().isBlank();
      boolean canMark = inRoom && ClientGlobalSettings.freeArena;
      String markLocked = !inRoom ? "fantastic.editor.no_room" : "fantastic.editor.free_area_off";
      int y = this.y();
      this.pickBtn(x, y, half, "fantastic.editor.room_corner_1", "ROOM_CORNER_1", "", "fantastic.editor.room_corner_1.tip", canMark, markLocked);
      this.pickBtn(
         x + half + 6, y, w - half - 6, "fantastic.editor.room_corner_2", "ROOM_CORNER_2", "", "fantastic.editor.room_corner_2.tip", canMark, markLocked
      );
      int y1 = this.y();
      Button clear = this.btn(
         x, y1, w, Component.m_237115_("fantastic.ui.clear_arena"), () -> EditorNet.send("room.arena.clear"), "fantastic.ui.clear_arena.tip"
      );
      clear.f_93623_ = inRoom;
      this.add(clear);
      this.gap(8);
      int y2 = this.y();
      this.add(this.btn(x, y2, half, Component.m_237115_("fantastic.ui.arena_new"), ArenaMenu::createArena, "fantastic.ui.arena_new.tip"));
      this.add(
         this.btn(x + half + 6, y2, w - half - 6, Component.m_237115_("fantastic.ui.get_wand"), () -> EditorNet.send("wand"), "fantastic.ui.get_wand.tip")
      );
      this.add(
         this.btn(
            x, this.y(), w, Component.m_237115_("fantastic.editor.schematics"), () -> EditorNet.send("arena.schematics"), "fantastic.editor.schematics.tip"
         )
      );

      for (String file : ArenaMenu.schematics) {
         int fy = this.y();
         this.label("§7" + file, x + 2, fy + 5, "fantastic.editor.schem_file");
         this.add(
            this.btn(
               x + w - 68,
               fy,
               68,
               Component.m_237115_("fantastic.editor.import"),
               () -> EditorNet.send("arena.import", file, ""),
               "fantastic.editor.import.tip"
            )
         );
      }

      this.gap(6);
      String selected = data != null && data.selectedArena() != null ? data.selectedArena() : "";

      for (ArenaListPayload.Entry e : ArenaMenu.entries()) {
         int ay = this.y();
         String name = e.name();
         int act = 46;
         this.label(
            (name.equalsIgnoreCase(selected) ? "§a● §r" : "") + name + "  §7" + e.sizeX() + "×" + e.sizeY() + "×" + e.sizeZ(),
            x + 2,
            ay + 5,
            e.instanced() ? "fantastic.ui.arena_kind_copy" : "fantastic.ui.arena_kind_place"
         );
         int bx = x + w - act * 3 - 12;
         Button use = this.btn(
            bx,
            ay,
            act,
            Component.m_237115_("fantastic.editor.use"),
            () -> EditorNet.send("room.arena.use", name),
            inRoom ? "fantastic.ui.arena_use.tip" : "fantastic.editor.no_room"
         );
         use.f_93623_ = inRoom && !e.busy();
         this.add(use);
         this.add(this.btn(bx + act + 6, ay, act, Component.m_237115_("fantastic.editor.open"), () -> {
            ArenaMenu.send("select", name);
            ArenaMenu.refresh();
         }, "fantastic.ui.arena_edit_tip"));
         this.add(
            this.btn(
               bx + (act + 6) * 2, ay, act, Component.m_237115_("fantastic.ui.arena_tp"), () -> EditorNet.send("arena.tp", name), "fantastic.ui.arena_tp.tip"
            )
         );
      }

      if (!target.isBlank()) {
         this.gap(8);
         int ey = this.y();
         if (!this.arenaDraftFor.equalsIgnoreCase(target)) {
            this.arenaDraft = target;
            this.arenaDraftFor = target;
         }

         this.field(x, ey, w - 74, "fantastic.ui.arena_name_hint", this.arenaDraft, s -> this.arenaDraft = s, 32);
         Button rename = this.btn(
            x + w - 68,
            ey,
            68,
            Component.m_237115_("fantastic.ui.arena_rename"),
            () -> EditorNet.send("arena.rename", target, this.arenaDraft),
            "fantastic.ui.arena_rename.tip"
         );
         rename.f_93623_ = !this.arenaDraft.isBlank() && !this.arenaDraft.equalsIgnoreCase(target);
         this.renameButton = rename;
         this.renameTarget = target;
         this.add(rename);
         int cy = this.y();
         this.pickBtn(x, cy, third, "fantastic.editor.corner_1", "ARENA_CORNER_1", target, "fantastic.editor.corner_1.tip");
         this.pickBtn(x + third + 6, cy, third, "fantastic.editor.corner_2", "ARENA_CORNER_2", target, "fantastic.editor.corner_2.tip");
         this.pickBtn(x + (third + 6) * 2, cy, w - (third + 6) * 2, "fantastic.editor.start_point", "ARENA_START", target, "fantastic.editor.start_point.tip");
         int gy = this.y();
         int groupW = (w - 16) / 3;
         this.adjustGroup(x, gy, groupW, target, "walls", -1, "fantastic.ui.arena_walls", "fantastic.ui.arena_walls.tip");
         this.adjustGroup(x + groupW + 8, gy, groupW, target, "floor", 1, "fantastic.ui.arena_floor", "fantastic.ui.arena_floor.tip");
         this.adjustGroup(x + (groupW + 8) * 2, gy, w - (groupW + 8) * 2, target, "ceiling", 1, "fantastic.ui.arena_ceiling", "fantastic.ui.arena_ceiling.tip");
         int sy = this.y();
         boolean staged = ArenaMenu.staged();
         ArenaListPayload.Entry entry = ArenaMenu.entry(target);
         boolean busy = entry != null && entry.frozen();
         int versions = entry == null ? 0 : entry.versions();
         Button save = this.btn(
            x,
            sy,
            third,
            Component.m_237115_("fantastic.ui.arena_save"),
            () -> ArenaMenu.send("apply", target),
            ArenaMenu.drawState() == 2 ? "fantastic.ui.arena_save.tip" : "fantastic.ui.arena_save.locked"
         );
         save.f_93623_ = staged && !busy && ArenaMenu.drawState() == 2;
         this.add(save);
         Button cancel = this.btn(
            x + third + 6, sy, third, Component.m_237115_("fantastic.ui.arena_cancel"), () -> ArenaMenu.send("revert", target), "fantastic.ui.arena_cancel.tip"
         );
         cancel.f_93623_ = staged;
         this.add(cancel);
         Button undo = this.btn(
            x + (third + 6) * 2,
            sy,
            w - (third + 6) * 2,
            Component.m_237110_("fantastic.ui.arena_undo", new Object[]{versions}),
            () -> ArenaMenu.send("undo", target),
            "fantastic.ui.arena_undo.tip"
         );
         undo.f_93623_ = versions > 0 && !busy;
         this.add(undo);
         int ry = this.y();
         this.add(this.btn(x, ry, third, Component.m_237115_("fantastic.ui.arena_shot"), () -> ArenaShot.begin(target), "fantastic.ui.arena_shot.tip"));
         this.add(
            this.btn(
               x + third + 6,
               ry,
               third,
               Component.m_237115_("fantastic.editor.reload"),
               () -> EditorNet.send("arena.reload", target),
               "fantastic.editor.reload.tip"
            )
         );
         this.add(
            this.btn(
               x + (third + 6) * 2,
               ry,
               w - (third + 6) * 2,
               Component.m_237115_("fantastic.editor.refit"),
               () -> EditorNet.send("arena.refit", target),
               "fantastic.editor.refit.tip"
            )
         );
         boolean armed = target.equalsIgnoreCase(this.armedDelete);
         this.add(this.btn(x, this.y(), w, Component.m_237115_(armed ? "fantastic.editor.confirm" : "fantastic.editor.delete_arena"), () -> {
            if (armed) {
               this.armedDelete = "";
               EditorNet.send("arena.delete", target, "", 1);
            } else {
               this.armedDelete = target;
               this.m_232761_();
            }
         }, armed ? "fantastic.editor.confirm.tip" : "fantastic.ui.arena_delete_tip"));
      }
   }

   private void adjustGroup(int x, int y, int w, String target, String side, int growSign, String labelKey, String tipKey) {
      int arrow = 16;
      int labelW = Math.max(18, w - arrow * 2 - 4);
      this.add(this.btn(x, y, arrow, Component.m_237113_("-"), () -> EditorNet.send("arena.adjust", target, side, -growSign), "fantastic.ui.arena_shrink"));
      this.label(Component.m_237115_(labelKey).getString(), x + arrow + 6, y + 5, tipKey);
      this.add(
         this.btn(
            x + arrow + labelW + 4, y, arrow, Component.m_237113_("+"), () -> EditorNet.send("arena.adjust", target, side, growSign), "fantastic.ui.arena_grow"
         )
      );
   }

   private void initServer(int x, int w) {
      this.helpLine = Component.m_237115_("fantastic.help.server").getString();
      int half = (w - 6) / 2;
      int third = (w - 12) / 3;
      int y = this.y();
      this.pickBtn(x, y, half, "fantastic.editor.set_lobby", "LOBBY_SPAWN", "", "fantastic.editor.set_lobby.tip");
      this.add(
         this.btn(
            x + half + 6, y, w - half - 6, Component.m_237115_("fantastic.editor.go_lobby"), () -> EditorNet.send("spawn.tp"), "fantastic.editor.go_lobby.tip"
         )
      );
      int by = this.y();
      this.pickBtn(x, by, third, "fantastic.editor.board_hider_wins", "BOARD", "hiderwins", "fantastic.editor.board.tip");
      this.pickBtn(x + third + 6, by, third, "fantastic.editor.board_seeker_wins", "BOARD", "seekerwins", "fantastic.editor.board.tip");
      this.pickBtn(x + (third + 6) * 2, by, w - (third + 6) * 2, "fantastic.editor.board_points", "BOARD", "hiderpoints", "fantastic.editor.board.tip");
      this.add(
         this.btn(x, this.y(), w, Component.m_237115_("fantastic.editor.board_clear"), () -> EditorNet.send("board.clear"), "fantastic.editor.board_clear.tip")
      );
      this.gap(8);
      int hy = this.y();
      this.add(this.btn(x, hy, third, Component.m_237115_("fantastic.editor.give_kit"), () -> EditorNet.send("kit"), "fantastic.editor.give_kit.tip"));
      this.add(
         this.btn(
            x + third + 6,
            hy,
            third,
            Component.m_237115_("fantastic.editor.give_crate"),
            () -> EditorNet.send("crate", "", 1),
            "fantastic.editor.give_crate.tip"
         )
      );
      this.add(
         this.btn(
            x + (third + 6) * 2,
            hy,
            w - (third + 6) * 2,
            Component.m_237115_("fantastic.editor.stats"),
            () -> EditorNet.send("top"),
            "fantastic.editor.stats.tip"
         )
      );
      this.slider(
         x,
         this.y(),
         w,
         "fantastic.editor.kit_cooldown",
         0,
         168,
         1,
         ClientGlobalSettings.kitCooldownHours,
         v -> v + "h",
         v -> EditorNet.send("kitcooldown", "", "", v)
      );
      this.gap(8);

      for (String name : EditorActions.GLOBAL_NAMES) {
         boolean on = ClientGlobalSettings.value(name);
         this.toggle(
            x,
            this.y(),
            w,
            "fantastic.global." + name + ".label",
            on,
            () -> EditorNet.send("global", name, "", on ? 0 : 1),
            "fantastic.global." + name + ".tip"
         );
      }
   }

   private void initLook(int x, int w) {
      this.helpLine = Component.m_237115_("fantastic.help.look").getString();
      int half = (w - 6) / 2;
      int third = (w - 12) / 3;
      int y = this.y();
      this.add(this.btn(x, y, half, Component.m_237115_("fantastic.editor.prop_off"), () -> EditorNet.send("prop", "off"), "fantastic.editor.prop_off.tip"));
      this.add(
         this.btn(
            x + half + 6,
            y,
            w - half - 6,
            Component.m_237115_("fantastic.editor.skin_off"),
            () -> EditorNet.send("skin", "off"),
            "fantastic.editor.skin_off.tip"
         )
      );
      this.gap(6);
      int col = 0;
      int rowY = this.y();

      for (PropShapes.Prop pr : PropShapes.PROPS) {
         String key = pr.key();
         if (col == 3) {
            col = 0;
            rowY = this.y();
         }

         int bw = col == 2 ? w - (third + 6) * 2 : third;
         this.add(
            this.btn(
               x + (third + 6) * col, rowY, bw, Component.m_237115_("fantastic.prop." + key), () -> EditorNet.send("prop", key), "fantastic.editor.prop.tip"
            )
         );
         col++;
      }

      this.gap(8);
      col = 0;
      rowY = this.y();

      for (CreatorSets.Set s : CreatorSets.POOL) {
         String id = s.id();
         if (col == 3) {
            col = 0;
            rowY = this.y();
         }

         int bw = col == 2 ? w - (third + 6) * 2 : third;
         this.add(
            this.btn(
               x + (third + 6) * col, rowY, bw, Component.m_237113_(CreatorSets.labelOf(id)), () -> EditorNet.send("skin", id), "fantastic.editor.skin.tip"
            )
         );
         col++;
      }
   }

   private static record Label(String text, int x, int y) {
   }

   public static enum Tab {
      SALAS("fantastic.tab.rooms", "fantastic.tab.rooms.desc"),
      PARTIDA("fantastic.tab.match", "fantastic.tab.match.desc"),
      REGLAS("fantastic.tab.rules", "fantastic.tab.rules.desc"),
      ARENA("fantastic.tab.arena", "fantastic.tab.arena.desc"),
      SERVIDOR("fantastic.tab.server", "fantastic.tab.server.desc"),
      ASPECTO("fantastic.tab.look", "fantastic.tab.look.desc");

      final String key;
      final String desc;

      private Tab(String key, String desc) {
         this.key = key;
         this.desc = desc;
      }

      String label() {
         return Component.m_237115_(this.key).getString();
      }
   }

   private static record TooltipZone(int x, int y, int w, int h, List<Component> lines) {
   }
}
