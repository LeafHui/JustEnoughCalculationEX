package dev.jecaex.mixin;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * Registers our mixin configuration with MixinBooter during the <b>late</b> phase (after mod
 * loading). Our mixin targets Just Enough Calculation's {@code GuiRecipe}, which is a mod class,
 * so it must be queued late — queuing it early makes the Mixin transformer fail when the target
 * class is first loaded.
 */
public class JecaExMixinPlugin implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.jecaex.json");
    }
}
