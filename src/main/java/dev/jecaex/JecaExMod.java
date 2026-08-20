package dev.jecaex;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Just Enough Calculation EX — a GregTech-oriented addon for Just Enough Calculation.
 *
 * <p>The actual enhancement is applied by a Mixin ({@code GuiRecipeMixin}) which patches
 * JEC's recipe-transfer logic so that GregTech machine recipes are imported with the correct
 * catalyst layout (real machine as catalyst + non-consumable inputs such as circuits routed
 * to the catalyst slot instead of the ingredient slot).</p>
 */
@Mod(
        modid = JecaExMod.MODID,
        name = JecaExMod.NAME,
        version = JecaExMod.VERSION,
        dependencies = "required-after:jecalculation;required-after:jei;required-after:mixinbooter;after:gregtech"
)
public class JecaExMod {

    public static final String MODID = "jecaex";
    public static final String NAME = "Just Enough Calculation EX";
    public static final String VERSION = "0.3.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Just Enough Calculation EX loaded.");
    }
}
