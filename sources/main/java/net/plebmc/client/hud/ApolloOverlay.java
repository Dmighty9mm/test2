package net.plebmc.client.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.opengl.WorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.plebmc.client.mods.Mods;

import net.plebmc.client.apollo.Apollo;
import net.plebmc.client.apollo.ApolloText;
import net.plebmc.client.apollo.module.BeamModule;
import net.plebmc.client.apollo.module.BorderModule;
import net.plebmc.client.apollo.module.ChatModule;
import net.plebmc.client.apollo.module.CooldownModule;
import net.plebmc.client.apollo.module.HologramModule;
import net.plebmc.client.apollo.module.NotificationModule;
import net.plebmc.client.apollo.module.StopwatchModule;
import net.plebmc.client.apollo.module.TeamModule;
import net.plebmc.client.apollo.module.TitleModule;
import net.plebmc.client.apollo.module.VignetteModule;
import net.plebmc.client.apollo.module.WaypointModule;

/**
 * Draws the Apollo modules' state on the HUD with the existing GUI quad
 * batch only (Draw.fill / drawText): no new shaders, buffers
 * or GL state. World-anchored things (waypoints, beams, holograms, team
 * markers, borders) are projected with the world camera and drawn as HUD
 * markers, the way Apollo waypoints show through walls.
 */
final class ApolloOverlay {

        private final Projector cam = new Projector();

        private ApolloOverlay() {
        }

        static final ApolloOverlay INSTANCE = new ApolloOverlay();

        // modules are created once at start-up: look them up once, not every frame
        private static VignetteModule vignetteM;
        private static StopwatchModule stopwatchM;
        private static CooldownModule cooldownM;
        private static ChatModule chatM;
        private static TitleModule titleM;
        private static NotificationModule notificationM;
        private static BeamModule beamM;
        private static WaypointModule waypointM;
        private static HologramModule hologramM;
        private static TeamModule teamM;
        private static BorderModule borderM;

        private static void lookup() {
                if (teamM == null) {
                        vignetteM = Apollo.module(VignetteModule.class);
                        stopwatchM = Apollo.module(StopwatchModule.class);
                        cooldownM = Apollo.module(CooldownModule.class);
                        chatM = Apollo.module(ChatModule.class);
                        titleM = Apollo.module(TitleModule.class);
                        notificationM = Apollo.module(NotificationModule.class);
                        beamM = Apollo.module(BeamModule.class);
                        waypointM = Apollo.module(WaypointModule.class);
                        hologramM = Apollo.module(HologramModule.class);
                        teamM = Apollo.module(TeamModule.class);
                        borderM = Apollo.module(BorderModule.class);
                }
        }

        private final List<Mods.Compass.Marker> compassMarkers = new ArrayList<>();
        private final Map<String, EntityPlayer> loadedPlayers = new HashMap<>();
        private float partialTicks;

        void render(int w, int h, long now, boolean haveCamera, double eyeX, double eyeY, double eyeZ, float yaw, float pitch,
                        float fov, int pxW, int pxH, float partialTicks) {
                lookup();
                this.partialTicks = partialTicks;
                VignetteModule vignette = vignetteM;
                if (vignette != null && vignette.isEnabled() && vignette.isActive()) {
                        vignette(w, h, vignette.resource(), vignette.opacity());
                }
                compassMarkers.clear();
                Minecraft mc = Minecraft.getMinecraft();
                cam.setup(eyeX, eyeY, eyeZ, yaw, pitch, fov, pxW, pxH, w, h);
                indexPlayers(mc);
                if (haveCamera) {
                        if (mc.gameSettings.shaders) {
                                beams(); // no world pass hook with shaders on: fall back to HUD columns
                        }
                        waypoints(mc.gameSettings.shaders);
                        holograms();
                        team(mc, true);
                        border(w, h);
                } else {
                        waypointMarkers();
                        team(mc, false);
                }
                PlebClient.mods().compass.setMarkers(new ArrayList<Mods.Compass.Marker>(compassMarkers));
                stopwatch(w, h, now);
                cooldowns(mc, w, h, now);
                liveChat(h);
                titles(w, h, now);
                notifications(w, now);
        }

        /** Loaded players by UUID, so markers can follow the live entity instead of the last server update. */
        private void indexPlayers(Minecraft mc) {
                loadedPlayers.clear();
                if (mc.theWorld == null || teamM == null || !teamM.isEnabled() || teamM.size() == 0) {
                        return;
                }
                List<EntityPlayer> ps = mc.theWorld.playerEntities;
                for (int i = 0; i < ps.size(); i++) {
                        EntityPlayer p = ps.get(i);
                        loadedPlayers.put(PlebEntities.uuid(p), p);
                }
        }

        /** Vanilla yaw that faces the point (0 = +Z south, 90 = -X west). */
        private float bearing(double x, double z) {
                return (float) Math.toDegrees(Math.atan2(-(x - cam.eyeX), z - cam.eyeZ));
        }

        // ==================== screen-space ====================

        /**
         * The server's vignette: screen edges darkened at the server's opacity
         * (stronger while knocked, lighter at low health on PlebNite). The HUD is
         * drawn into Eaglercraft's overlay buffer, which is laid over the world
         * with premultiplied alpha, so the darkening is drawn as black with a
         * smooth alpha falloff from the center instead of the vanilla
         * multiply-blended texture (that would turn the whole overlay opaque).
         */
        private static void vignette(int w, int h, String resource, float opacity) {
                float o = Math.max(0f, Math.min(1f, opacity));
                if (o <= 0f) {
                        return;
                }
                GlStateManager.disableTexture2D();
                GlStateManager.disableAlpha();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.shadeModel(7425); // smooth
                Tessellator t = Tessellator.getInstance();
                WorldRenderer wr = t.getWorldRenderer();
                wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
                int n = 12;
                for (int iy = 0; iy < n; iy++) {
                        for (int ix = 0; ix < n; ix++) {
                                vig(wr, w, h, n, ix, iy + 1, o);
                                vig(wr, w, h, n, ix + 1, iy + 1, o);
                                vig(wr, w, h, n, ix + 1, iy, o);
                                vig(wr, w, h, n, ix, iy, o);
                        }
                }
                t.draw();
                GlStateManager.shadeModel(7424);
                GlStateManager.enableAlpha();
                GlStateManager.enableTexture2D();
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        private static void vig(WorldRenderer wr, int w, int h, int n, int ix, int iy, float o) {
                double nx = ix * 2.0 / n - 1.0;
                double ny = iy * 2.0 / n - 1.0;
                // elliptical distance from the center: 0 at the middle, 1 at the edge midpoints
                double d = Math.sqrt(nx * nx + ny * ny);
                double k = Math.max(0.0, Math.min(1.0, (d - 0.45) / 0.85));
                float a = (float) (o * k * (0.6 + 0.4 * k) * 1.15);
                wr.pos(w * ix / (double) n, h * iy / (double) n, -90.0D).color(0f, 0f, 0f, Math.min(0.95f, a)).endVertex();
        }

        /** Match timer: top center, under the compass when it's there. */
        private static void stopwatch(int w, int h, long now) {
                StopwatchModule sw = stopwatchM;
                if (sw == null || !sw.isEnabled() || !sw.isShown()) {
                        return;
                }
                String s = StopwatchModule.format(sw.elapsedMs(now));
                int tw = Draw.width(s);
                Mods.Compass compass = PlebClient.mods().compass;
                float y = 22;
                if (compass.isActive() && compass.fy() < 0.25f) {
                        int[] cp = net.plebmc.client.mods.ModRegistry.place(compass, Mods.Compass.WIDTH, Mods.Compass.HEIGHT,
                                        w, h);
                        y = cp[1] + Mods.Compass.HEIGHT + 3;
                }
                float x = w / 2f - (tw + 14) / 2f;
                Draw.fill(x, y, tw + 14, 13, 0x80000000);
                // a little stopwatch glyph
                int c = sw.isRunning() ? 0xFFFFFFFF : 0xFFFFFF55;
                Draw.fill(x + 3, y + 4, 6, 6, 0xFF555555);
                Draw.fill(x + 4, y + 5, 4, 4, c);
                Draw.fill(x + 5, y + 2, 2, 2, 0xFF555555);
                Draw.fill(x + 6, y + 6, 1, 2, 0xFF000000);
                Draw.text(s, x + 12, y + 3, c, true);
        }

        /**
         * Cooldown icons above the hotbar: the item, a dark sweep that unwinds
         * clockwise as the cooldown runs out, and the seconds left.
         */
        private static void cooldowns(Minecraft mc, int w, int h, long now) {
                CooldownModule cd = cooldownM;
                if (cd == null || !cd.isEnabled() || cd.size() == 0) {
                        return;
                }
                List<CooldownModule.Cooldown> list = cd.active();
                int size = 20;
                int gap = 3;
                int total = list.size() * (size + gap) - gap;
                int x = w / 2 - total / 2;
                int y = h - 22 - 18 - size - 6;
                if (mc.thePlayer != null && !mc.playerController.shouldDrawHUD()) {
                        y += 18;
                }
                for (int i = 0; i < list.size(); i++) {
                        CooldownModule.Cooldown c = list.get(i);
                        float f = c.fraction(now);
                        Draw.fill(x, y, size, size, 0x90000000);
                        ItemStack icon = ItemIcons.forCooldown(c.icon, c.name);
                        if (icon != null) {
                                try {
                                        GlStateManager.enableRescaleNormal();
                                        RenderHelper.enableGUIStandardItemLighting();
                                        mc.getRenderItem().renderItemAndEffectIntoGUI(icon, x + 2, y + 2);
                                        RenderHelper.disableStandardItemLighting();
                                        GlStateManager.disableRescaleNormal();
                                        GlStateManager.disableLighting();
                                        GlStateManager.enableBlend();
                                } catch (Throwable t) {
                                        icon = null;
                                }
                        }
                        if (icon == null) {
                                String label = c.name.isEmpty() ? "?" : c.name.substring(0, Math.min(3, c.name.length()));
                                Draw.text(label, x + size / 2f - Draw.width(label) / 2f, y + 3, 0xFFAAAAAA, true);
                        }
                        sweep(x + size / 2f, y + size / 2f, size / 2f, f, 0x9A000000);
                        long ms = c.remaining(now);
                        String t = ms >= 10000 ? (ms / 1000) + "s" : (ms / 1000) + "." + (ms % 1000 / 100);
                        int tw = Draw.width(t);
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(0f, 0f, 200f);
                        Draw.text(t, x + size - tw - 1 + 0.5f, y + size - 8, ms < 1000 ? 0xFF55FF55 : 0xFFFFFFFF, true);
                        GlStateManager.popMatrix();
                        x += size + gap;
                }
        }

        /** A filled pie of {@code fraction} of a square, starting at 12 o'clock going clockwise. */
        private static void sweep(float cx, float cy, float r, float fraction, int argb) {
                if (fraction <= 0f) {
                        return;
                }
                float a = ((argb >>> 24) & 0xFF) / 255f;
                float rr = ((argb >> 16) & 0xFF) / 255f;
                float gg = ((argb >> 8) & 0xFF) / 255f;
                float bb = (argb & 0xFF) / 255f;
                GlStateManager.pushMatrix();
                GlStateManager.translate(0f, 0f, 150f);
                GlStateManager.disableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.disableAlpha();
                GlStateManager.tryBlendFuncSeparate(770, 771, 0, 1);
                Tessellator t = Tessellator.getInstance();
                WorldRenderer wr = t.getWorldRenderer();
                wr.begin(7, DefaultVertexFormats.POSITION_COLOR);
                int steps = Math.max(1, Math.round(48 * Math.min(1f, fraction)));
                double end = Math.PI * 2 * Math.min(1f, fraction);
                double rad = r * 1.4142;
                for (int i = 0; i < steps; i++) {
                        double a0 = end * i / steps - Math.PI / 2;
                        double a1 = end * (i + 1) / steps - Math.PI / 2;
                        float x0 = clampSq(cx + Math.cos(a0) * rad, cx, r);
                        float y0 = clampSq(cy + Math.sin(a0) * rad, cy, r);
                        float x1 = clampSq(cx + Math.cos(a1) * rad, cx, r);
                        float y1 = clampSq(cy + Math.sin(a1) * rad, cy, r);
                        wr.pos(cx, cy, 0).color(rr, gg, bb, a).endVertex();
                        wr.pos(x0, y0, 0).color(rr, gg, bb, a).endVertex();
                        wr.pos(x1, y1, 0).color(rr, gg, bb, a).endVertex();
                        wr.pos(x1, y1, 0).color(rr, gg, bb, a).endVertex();
                }
                t.draw();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.enableAlpha();
                GlStateManager.enableTexture2D();
                GlStateManager.popMatrix();
        }

        private static float clampSq(double v, float c, float r) {
                return (float) Math.max(c - r, Math.min(c + r, v));
        }

        private static void liveChat(int h) {
                ChatModule chat = chatM;
                if (chat == null || !chat.isEnabled()) {
                        return;
                }
                List<List<ApolloText.Run>> lines = chat.lines();
                int y = h - 48 - lines.size() * 10;
                for (List<ApolloText.Run> l : lines) {
                        int lw = Draw.width(l);
                        Draw.fill(2, y - 1, lw + 4, 10, 0x80000000);
                        Draw.runs(l, 4, y, 1f, true);
                        y += 10;
                }
        }

        private static void titles(int w, int h, long now) {
                TitleModule tm = titleM;
                if (tm == null || !tm.isEnabled()) {
                        return;
                }
                for (TitleModule.Title t : tm.active()) {
                        float a = t.alpha(now);
                        if (a <= 0.02f) {
                                continue;
                        }
                        float s = t.scaleAt(now) * (t.type == TitleModule.TYPE_TITLE ? 4f : 2f);
                        float y = t.type == TitleModule.TYPE_TITLE ? h / 2f - 10f - 8f * s / 2f - 8f : h / 2f + 5f;
                        Draw.runsCentered(t.text, w / 2f, y, s, a);
                }
        }

        /** Pop-ups, top right: slide in, accent bar in the title's color, a bar that runs down with the time left. */
        private static void notifications(int w, long now) {
                NotificationModule nm = notificationM;
                if (nm == null || !nm.isEnabled() || nm.size() == 0) {
                        return;
                }
                int y = 4;
                int bw = 172;
                for (NotificationModule.Notification n : nm.active()) {
                        float v = n.visibility(now);
                        if (v <= 0f) {
                                continue;
                        }
                        List<String> desc = Draw.wrap(ApolloText.plain(n.description), bw - 12);
                        if (desc.size() > 4) {
                                desc = desc.subList(0, 4);
                        }
                        int bh = 17 + desc.size() * 10 + 3;
                        float ease = 1f - (1f - v) * (1f - v);
                        float x = w - 4 - bw * ease;
                        int accent = n.title.isEmpty() ? 0xFF55FFFF : (n.title.get(0).argb | 0xFF000000);
                        if (accent == 0xFFFFFFFF) {
                                accent = 0xFF55FFFF;
                        }
                        Draw.fill(x, y, bw, bh, Draw.alpha(0xE0101018, v));
                        Draw.fill(x, y, 2, bh, Draw.alpha(accent, v));
                        Draw.runs(n.title, x + 7, y + 5, v, true);
                        int ly = y + 17;
                        for (String line : desc) {
                                Draw.text(line, x + 7, ly, Draw.alpha(0xFFCCCCCC, v), false);
                                ly += 10;
                        }
                        float left = n.durationMs <= 0 ? 0f : Math.max(0f, Math.min(1f, (n.start + n.durationMs - now) / (float) n.durationMs));
                        Draw.fill(x + 2, y + bh - 1, (bw - 2) * left, 1, Draw.alpha(accent, v * 0.8f));
                        y += bh + 4;
                }
        }

        // ==================== world-anchored ====================

        private void beams() {
                BeamModule bm = beamM;
                if (bm == null || !bm.isEnabled()) {
                        return;
                }
                for (BeamModule.Beam b : bm.all()) {
                        beam(b.pos.x + 0.5, b.pos.y, b.pos.z + 0.5, b.argb);
                }
        }

        /** HUD fallback beam (shaders on): a projected column from the block up. */
        private void beam(double x, double y, double z, int argb) {
                if (!cam.project(x, y, z)) {
                        return;
                }
                float bx = cam.sx;
                float by = cam.sy;
                float depth = cam.depth;
                if (!cam.project(x, Math.max(y + 1, 256), z)) {
                        return;
                }
                float width = Math.max(1f, Math.min(6f, 30f / Math.max(1f, depth)));
                float top = Math.min(by, cam.sy);
                float bottom = Math.max(by, cam.sy);
                Draw.fill(bx - width / 2f, top, width, bottom - top, Draw.alpha(argb, 0.45f));
        }

        /**
         * Waypoint labels (name + distance + a diamond) at the waypoint; the sky
         * beam itself is drawn in the world. Every waypoint also goes on the compass.
         */
        private void waypoints(boolean hudBeams) {
                WaypointModule wm = waypointM;
                if (wm == null || !wm.isEnabled()) {
                        return;
                }
                List<WaypointModule.Waypoint> list = wm.visible("");
                for (int i = 0; i < list.size(); i++) {
                        WaypointModule.Waypoint wp = list.get(i);
                        double px = wp.pos.x + 0.5;
                        double pz = wp.pos.z + 0.5;
                        int dist = (int) Math.round(cam.distance(px, wp.pos.y, pz));
                        compassMarkers.add(new Mods.Compass.Marker(bearing(px, pz), wp.argb, wp.name, dist, true));
                        if (hudBeams) {
                                beam(px, wp.pos.y, pz, wp.argb);
                        }
                        if (!cam.project(px, wp.pos.y + 1.2, pz)) {
                                continue;
                        }
                        String label = wp.name + " \u00a77" + dist + "m";
                        int lw = Draw.width(label);
                        float lx = cam.sx - lw / 2f;
                        float ly = cam.sy - 16;
                        Draw.fill(lx - 3, ly - 2, lw + 6, 11, 0x90000000);
                        Draw.fill(lx - 3, ly + 9, lw + 6, 1, wp.argb | 0xFF000000);
                        Draw.text(label, lx, ly, 0xFFFFFFFF, true);
                        diamond(cam.sx, cam.sy - 2, 3, wp.argb | 0xFF000000);
                }
        }

        /** Compass entries for waypoints when the HUD camera is unavailable (third person). */
        private void waypointMarkers() {
                WaypointModule wm = waypointM;
                if (wm == null || !wm.isEnabled()) {
                        return;
                }
                List<WaypointModule.Waypoint> list = wm.visible("");
                for (int i = 0; i < list.size(); i++) {
                        WaypointModule.Waypoint wp = list.get(i);
                        double px = wp.pos.x + 0.5;
                        double pz = wp.pos.z + 0.5;
                        compassMarkers.add(new Mods.Compass.Marker(bearing(px, pz), wp.argb, wp.name,
                                        (int) Math.round(cam.distance(px, wp.pos.y, pz)), true));
                }
        }

        private static void diamond(float cx, float cy, int r, int argb) {
                for (int i = -r; i <= r; i++) {
                        int half = r - Math.abs(i);
                        Draw.fill(cx - half, cy + i, half * 2 + 1, 1, argb);
                }
        }

        private void holograms() {
                HologramModule hm = hologramM;
                if (hm == null || !hm.isEnabled()) {
                        return;
                }
                for (HologramModule.Hologram ho : hm.all()) {
                        if (cam.distance(ho.pos.x, ho.pos.y, ho.pos.z) > 64 || !cam.project(ho.pos.x, ho.pos.y, ho.pos.z)) {
                                continue;
                        }
                        float y = cam.sy - ho.lines.size() * 10;
                        for (List<ApolloText.Run> line : ho.lines) {
                                int lw = Draw.width(line);
                                if (ho.background) {
                                        Draw.fill(cam.sx - lw / 2f - 2, y - 1, lw + 4, 10, 0x40000000);
                                }
                                Draw.runs(line, cam.sx - lw / 2f, y, 1f, ho.shadow);
                                y += 10;
                        }
                }
        }

        /**
         * Teammate markers: a chevron in the teammate's marker color above them
         * (through walls), with their name and distance when they're out of
         * render range; every teammate also goes on the compass.
         */
        private void team(Minecraft mc, boolean draw) {
                TeamModule tm = teamM;
                if (tm == null || !tm.isEnabled() || tm.size() == 0) {
                        return;
                }
                String self = mc.thePlayer == null ? "" : PlebEntities.uuid(mc.thePlayer);
                for (TeamModule.Member m : tm.members()) {
                        if (m.uuid.equals(self)) {
                                continue;
                        }
                        EntityPlayer e = loadedPlayers.get(m.uuid);
                        double x;
                        double y;
                        double z;
                        if (e != null && !e.isDead) {
                                float pt = partialTicks;
                                x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
                                y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt + e.height + 0.75;
                                z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
                        } else if (m.hasPos) {
                                x = m.pos.x;
                                y = m.pos.y + 2.55;
                                z = m.pos.z;
                        } else {
                                continue;
                        }
                        int dist = (int) Math.round(cam.distance(x, y, z));
                        compassMarkers.add(new Mods.Compass.Marker(bearing(x, z), m.argb, ApolloText.plain(m.name), dist, false));
                        if (!draw || !cam.project(x, y, z) || cam.sx < -8 || cam.sy < -8 || cam.sx > cam.guiW() + 8
                                        || cam.sy > cam.guiH() + 8) {
                                continue;
                        }
                        int c = m.argb | 0xFF000000;
                        // chevron with a dark rim
                        for (int i = 0; i < 5; i++) {
                                Draw.fill(cam.sx - 6 + i, cam.sy - 7 + i, 12 - 2 * i + 1, 1, 0xA0000000);
                        }
                        for (int i = 0; i < 4; i++) {
                                Draw.fill(cam.sx - 4 + i, cam.sy - 6 + i, 8 - 2 * i + 1, 1, c);
                        }
                        if (e == null) {
                                String d = dist + "m";
                                int nw = Draw.width(m.name);
                                int dw = Draw.width(d);
                                Draw.runs(m.name, cam.sx - nw / 2f, cam.sy - 18, 1f, true);
                                Draw.text(d, cam.sx - dw / 2f, cam.sy - 27, 0xFFAAAAAA, true);
                        }
                }
        }

        private void border(int w, int h) {
                BorderModule bm = borderM;
                if (bm == null || !bm.isEnabled()) {
                        return;
                }
                for (BorderModule.Border b : bm.all()) {
                        double d = b.distanceToEdge(cam.eyeX, cam.eyeZ);
                        if (d > 24) {
                                continue;
                        }
                        // the wall's four bottom/top edges near the player, as projected segments
                        double y0 = Math.floor(cam.eyeY) - 2;
                        double y1 = y0 + 6;
                        double[][] corners = { { b.minX, b.minZ }, { b.maxX, b.minZ }, { b.maxX, b.maxZ }, { b.minX, b.maxZ } };
                        for (int i = 0; i < 4; i++) {
                                double[] p = corners[i];
                                double[] q = corners[(i + 1) % 4];
                                edge(p[0], y0, p[1], q[0], y0, q[1], b.argb);
                                edge(p[0], y1, p[1], q[0], y1, q[1], b.argb);
                        }
                        if (d < 6) {
                                String s = "Border " + ((int) (d * 10)) / 10.0 + "m";
                                int sw = Draw.width(s);
                                Draw.text(s, w / 2f - sw / 2f, h / 2f + 20, b.argb, true);
                        }
                }
        }

        /** A world-space segment, sampled so only the visible part is drawn (dotted line). */
        private void edge(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
                double len = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
                int steps = (int) Math.min(400, Math.max(2, len * 2));
                int c = Draw.alpha(argb, 0.8f);
                for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        double x = x0 + (x1 - x0) * t;
                        double z = z0 + (z1 - z0) * t;
                        if (cam.distance(x, y0, z) < 32 && cam.project(x, y0 + (y1 - y0) * t, z)) {
                                Draw.fill(cam.sx - 1, cam.sy - 1, 2, 2, c);
                        }
                }
        }
}
