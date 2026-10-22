package de.survival.addon.modules;

import de.survival.addon.SurvivalAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Eigene Spieler-Nametags mit frei einstellbarem Format.
 * Platzhalter: {name} {alias} {health} {ping} {dist} {gm} {item}
 * Tipp: Meteors eingebaute "Nametags"-Module deaktivieren, sonst sieht man doppelt.
 */
public class NametagPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLook = settings.createGroup("Aussehen");

    private final Setting<String> format = sgGeneral.add(new StringSetting.Builder()
        .name("format")
        .description("Platzhalter: {name} {alias} {health} {ping} {dist} {gm} {item}")
        .defaultValue("{alias} [{health}] {dist}m {ping}ms")
        .build());

    private final Setting<List<String>> aliases = sgGeneral.add(new StringListSetting.Builder()
        .name("aliase")
        .description("Eigene Namen im Format Spielername=Anzeigename (z.B. Steve=Boss).")
        .defaultValue()
        .build());

    private final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("selbst-anzeigen")
        .description("Auch deinen eigenen Nametag zeichnen (nur in Freecam sinnvoll).")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> hideSneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("schleichende-verstecken")
        .defaultValue(false)
        .build());

    private final Setting<Double> maxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-distanz")
        .description("0 = unbegrenzt.")
        .defaultValue(0)
        .min(0)
        .sliderMax(256)
        .build());

    private final Setting<Double> scale = sgLook.add(new DoubleSetting.Builder()
        .name("groesse")
        .defaultValue(1.2)
        .min(0.3)
        .sliderMax(3)
        .build());

    private final Setting<Boolean> shadow = sgLook.add(new BoolSetting.Builder()
        .name("schatten")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> healthColor = sgLook.add(new BoolSetting.Builder()
        .name("farbe-nach-leben")
        .description("Text faerbt sich von gruen (voll) nach rot (wenig Leben).")
        .defaultValue(true)
        .build());

    private final Setting<SettingColor> nameColor = sgLook.add(new ColorSetting.Builder()
        .name("text-farbe")
        .description("Wird genutzt, wenn Farbe-nach-Leben aus ist.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build());

    private final Setting<SettingColor> friendColor = sgLook.add(new ColorSetting.Builder()
        .name("freunde-farbe")
        .defaultValue(new SettingColor(80, 220, 255))
        .build());

    private final Setting<Integer> yOffset = sgLook.add(new IntSetting.Builder()
        .name("y-versatz")
        .description("Pixel nach oben.")
        .defaultValue(0)
        .min(-50)
        .sliderMax(50)
        .build());

    private final Vector3d pos = new Vector3d();
    private final Color tmp = new Color(255, 255, 255);

    public NametagPlus() {
        super(SurvivalAddon.CATEGORY, "nametag-plus", "Spieler-Nametags mit eigenem Format, Aliasen, Ping, Distanz und mehr.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        Map<String, String> aliasMap = parseAliases();

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !showSelf.get()) continue;
            if (hideSneaking.get() && p.isSneaking()) continue;

            double dist = mc.gameRenderer.getCamera().getPos().distanceTo(p.getPos());
            if (maxDistance.get() > 0 && dist > maxDistance.get()) continue;

            pos.set(
                MathHelper.lerp(event.tickDelta, p.lastRenderX, p.getX()),
                MathHelper.lerp(event.tickDelta, p.lastRenderY, p.getY()) + p.getHeight() + 0.4,
                MathHelper.lerp(event.tickDelta, p.lastRenderZ, p.getZ()));

            double s = scale.get();
            if (!NametagUtils.to2D(pos, s)) continue;

            String label = buildLabel(p, aliasMap, dist);
            Color color = pickColor(p);

            NametagUtils.begin(pos, event.drawContext);
            TextRenderer text = TextRenderer.get();
            text.beginBig();
            double w = text.getWidth(label, shadow.get());
            double h = text.getHeight(shadow.get());
            text.render(label, -w / 2, -h - yOffset.get(), color, shadow.get());
            text.end();
            NametagUtils.end(event.drawContext);
        }
    }

    private String buildLabel(PlayerEntity p, Map<String, String> aliasMap, double dist) {
        String name = p.getGameProfile().getName();
        String alias = aliasMap.getOrDefault(name.toLowerCase(), name);

        PlayerListEntry entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        String ping = entry == null ? "?" : String.valueOf(entry.getLatency());
        String gm = entry == null || entry.getGameMode() == null ? "?" : entry.getGameMode().getName();

        float hp = p.getHealth() + p.getAbsorptionAmount();
        String health = String.valueOf(Math.round(hp * 10f) / 10f);
        String item = p.getMainHandStack().isEmpty() ? "-" : p.getMainHandStack().getName().getString();

        return format.get()
            .replace("{name}", name)
            .replace("{alias}", alias)
            .replace("{health}", health)
            .replace("{ping}", ping)
            .replace("{dist}", String.valueOf(Math.round(dist)))
            .replace("{gm}", gm)
            .replace("{item}", item);
    }

    private Color pickColor(PlayerEntity p) {
        if (Friends.get().isFriend(p)) return friendColor.get();
        if (!healthColor.get()) return nameColor.get();

        float frac = MathHelper.clamp((p.getHealth() + p.getAbsorptionAmount()) / p.getMaxHealth(), 0f, 1f);
        tmp.set((int) (255 * (1f - frac)), (int) (255 * frac), 40, 255);
        return tmp;
    }

    private Map<String, String> parseAliases() {
        Map<String, String> map = new HashMap<>();
        for (String entry : aliases.get()) {
            int i = entry.indexOf('=');
            if (i <= 0 || i == entry.length() - 1) continue;
            map.put(entry.substring(0, i).trim().toLowerCase(), entry.substring(i + 1).trim());
        }
        return map;
    }
}
