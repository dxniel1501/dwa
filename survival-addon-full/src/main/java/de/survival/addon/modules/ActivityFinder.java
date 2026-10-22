package de.survival.addon.modules;

import de.survival.addon.SurvivalAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sucht Chunks, die auf Spieleraktivitaet hindeuten:
 *  - voll ausgewachsene Amethyst-Cluster (Chunk war lange geladen -> Wachstum / Aktivitaet)
 *  - voll ausgewachsener Kelp (Alter 25)
 *  - rotierter Deepslate (Achse X/Z; natuerlich generiert ist er immer Y -> vom Spieler platziert)
 *
 * Das sind Heuristiken: Treffer sind Hinweise, keine Beweise.
 */
public class ActivityFinder extends Module {
    private final SettingGroup sgCriteria = settings.createGroup("Kriterien");
    private final SettingGroup sgRender = settings.createGroup("Anzeige");

    // --- Kriterien ---
    private final Setting<Boolean> amethyst = sgCriteria.add(new BoolSetting.Builder()
        .name("amethyst-cluster")
        .description("Voll ausgewachsene Amethyst-Cluster zaehlen.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minAmethyst = sgCriteria.add(new IntSetting.Builder()
        .name("min-amethyst")
        .description("Mindestanzahl Cluster pro Chunk.")
        .defaultValue(6)
        .min(1)
        .sliderMax(64)
        .visible(amethyst::get)
        .build());

    private final Setting<Boolean> kelp = sgCriteria.add(new BoolSetting.Builder()
        .name("kelp")
        .description("Voll ausgewachsenen Kelp (Alter 25) zaehlen.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minKelp = sgCriteria.add(new IntSetting.Builder()
        .name("min-kelp")
        .description("Mindestanzahl voll gewachsener Kelp-Spitzen pro Chunk.")
        .defaultValue(8)
        .min(1)
        .sliderMax(128)
        .visible(kelp::get)
        .build());

    private final Setting<Boolean> deepslate = sgCriteria.add(new BoolSetting.Builder()
        .name("rotierter-deepslate")
        .description("Deepslate mit X/Z-Achse zaehlen (nicht natuerlich).")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minDeepslate = sgCriteria.add(new IntSetting.Builder()
        .name("min-deepslate")
        .description("Mindestanzahl rotierter Deepslate-Bloecke pro Chunk.")
        .defaultValue(3)
        .min(1)
        .sliderMax(64)
        .visible(deepslate::get)
        .build());

    private final Setting<Boolean> notify = sgCriteria.add(new BoolSetting.Builder()
        .name("chat-nachricht")
        .description("Schreibt eine Nachricht in den Chat, wenn ein neuer Chunk auffaellt.")
        .defaultValue(true)
        .build());

    // --- Anzeige ---
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("form")
        .description("Wie der Chunk gezeichnet wird.")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("flaechen-farbe")
        .defaultValue(new SettingColor(255, 80, 80, 40))
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("linien-farbe")
        .defaultValue(new SettingColor(255, 80, 80, 255))
        .build());

    private final Setting<Integer> renderDistance = sgRender.add(new IntSetting.Builder()
        .name("anzeige-distanz")
        .description("Nur Chunks in dieser Entfernung (in Chunks) zeichnen. 0 = alle.")
        .defaultValue(0)
        .min(0)
        .sliderMax(64)
        .build());

    private final Map<ChunkPos, Result> results = new ConcurrentHashMap<>();

    public ActivityFinder() {
        super(SurvivalAddon.CATEGORY, "activity-finder", "Findet Chunks mit Spieleraktivitaet (Amethyst, Kelp, rotierter Deepslate).");
    }

    @Override
    public void onActivate() {
        results.clear();
        if (mc.world == null || mc.player == null) return;

        // Bereits geladene Chunks einmal scannen.
        int r = mc.options.getViewDistance().getValue();
        int cx = mc.player.getChunkPos().x;
        int cz = mc.player.getChunkPos().z;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(x, z);
                if (chunk != null) scan(chunk, false);
            }
        }
    }

    @Override
    public void onDeactivate() {
        results.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        scan(event.chunk(), true);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null) return;
        int max = renderDistance.get();
        ChunkPos me = mc.player.getChunkPos();

        for (Result res : results.values()) {
            if (max > 0 && Math.max(Math.abs(res.pos.x - me.x), Math.abs(res.pos.z - me.z)) > max) continue;

            double x1 = res.pos.getStartX();
            double z1 = res.pos.getStartZ();
            event.renderer.box(
                x1, res.minY, z1,
                x1 + 16, res.maxY + 1, z1 + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private void scan(WorldChunk chunk, boolean announce) {
        int amethystCount = 0, kelpCount = 0, deepslateCount = 0;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!section.hasAny(this::isInteresting)) continue;

            int baseY = chunk.getBottomY() + i * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        boolean hit = false;

                        if (amethyst.get() && state.isOf(Blocks.AMETHYST_CLUSTER)) {
                            amethystCount++;
                            hit = true;
                        } else if (kelp.get() && state.isOf(Blocks.KELP)
                            && state.get(Properties.AGE_25) == 25) {
                            kelpCount++;
                            hit = true;
                        } else if (deepslate.get() && isRotatedDeepslate(state)) {
                            deepslateCount++;
                            hit = true;
                        }

                        if (hit) {
                            int wy = baseY + y;
                            if (wy < minY) minY = wy;
                            if (wy > maxY) maxY = wy;
                        }
                    }
                }
            }
        }

        boolean flagged =
            (amethyst.get() && amethystCount >= minAmethyst.get())
                || (kelp.get() && kelpCount >= minKelp.get())
                || (deepslate.get() && deepslateCount >= minDeepslate.get());

        ChunkPos pos = chunk.getPos();
        if (!flagged) {
            results.remove(pos);
            return;
        }

        boolean isNew = results.put(pos, new Result(pos, minY, maxY)) == null;
        if (announce && isNew && notify.get()) {
            info("Aktivitaet in Chunk (%d, %d) bei ca. X=%d Z=%d: Amethyst %d, Kelp %d, rot. Deepslate %d",
                pos.x, pos.z, pos.getStartX() + 8, pos.getStartZ() + 8,
                amethystCount, kelpCount, deepslateCount);
        }
    }

    /** Schneller Vorfilter pro Chunk-Section. */
    private boolean isInteresting(BlockState state) {
        return (amethyst.get() && state.isOf(Blocks.AMETHYST_CLUSTER))
            || (kelp.get() && state.isOf(Blocks.KELP))
            || (deepslate.get() && state.isOf(Blocks.DEEPSLATE));
    }

    private boolean isRotatedDeepslate(BlockState state) {
        return state.isOf(Blocks.DEEPSLATE) && state.get(PillarBlock.AXIS) != Direction.Axis.Y;
    }

    private record Result(ChunkPos pos, int minY, int maxY) {}
}
