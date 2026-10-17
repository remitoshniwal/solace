package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. Esc > Mods > Solace > Config opens Solace's setup screen. */
@Mod(value = Solace.MODID, dist = Dist.CLIENT)
public final class SolaceClientMod {
    public SolaceClientMod(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> new SolaceSetupScreen(parent, mod));
    }
}
