package de.survival.addon;

import de.survival.addon.modules.ActivityFinder;
import de.survival.addon.modules.InteractFreecam;
import de.survival.addon.modules.NametagPlus;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;

public class SurvivalAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("Survival", Items.AMETHYST_CLUSTER.getDefaultStack());

    @Override
    public void onInitialize() {
        Modules.get().add(new InteractFreecam());
        Modules.get().add(new ActivityFinder());
        Modules.get().add(new NametagPlus());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "de.survival.addon";
    }
}
