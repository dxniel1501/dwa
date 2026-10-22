package de.survival.addon.modules;

import de.survival.addon.SurvivalAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Funktioniert zusammen mit Meteors eingebauter Freecam:
 * Mit der Kamera zielen, aber nur Bloecke innerhalb der Reichweite des SPIELERS
 * (nicht der Kamera) abbauen / benutzen.
 */
public class InteractFreecam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reichweite")
        .description("Maximale Entfernung vom Spieler (Augen) zum Block.")
        .defaultValue(4.5)
        .min(1)
        .sliderMax(6)
        .build());

    private final Setting<Double> aimDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("ziel-distanz")
        .description("Wie weit der Strahl von der Kamera aus geschossen wird.")
        .defaultValue(32)
        .min(4)
        .sliderMax(128)
        .build());

    private final Setting<Boolean> breakBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("abbauen")
        .description("Linksklick baut den anvisierten Block ab.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> useBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("benutzen")
        .description("Rechtsklick benutzt den Block / platziert Bloecke.")
        .defaultValue(false)
        .build());

    private final Setting<Integer> useDelay = sgGeneral.add(new IntSetting.Builder()
        .name("benutzen-delay")
        .description("Ticks zwischen zwei Rechtsklicks.")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .visible(useBlocks::get)
        .build());

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("schwingen")
        .description("Arm-Animation beim Abbauen/Benutzen.")
        .defaultValue(true)
        .build());

    private int useTimer;

    public InteractFreecam() {
        super(SurvivalAddon.CATEGORY, "interact-freecam", "Mit Freecam Bloecke in Spielerreichweite abbauen/benutzen. Zusammen mit der normalen Freecam aktivieren.");
    }

    @Override
    public void onActivate() {
        useTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (!Modules.get().isActive(Freecam.class)) return;

        boolean attack = breakBlocks.get() && mc.options.attackKey.isPressed();
        boolean use = useBlocks.get() && mc.options.useKey.isPressed();
        if (useTimer > 0) useTimer--;
        if (!attack && !use) return;

        BlockHitResult hit = rayFromCamera();
        if (hit == null) return;

        BlockPos pos = hit.getBlockPos();
        double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
        if (dist > reach.get()) return;

        if (attack) {
            mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
        } else if (useTimer == 0) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
            useTimer = useDelay.get();
        }
    }

    private BlockHitResult rayFromCamera() {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d start = cam.getPos();
        Vec3d dir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d end = start.add(dir.multiply(aimDistance.get()));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            start, end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player));

        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }
}
