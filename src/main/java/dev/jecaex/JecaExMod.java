package dev.jecaex;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Just Enough Calculation EX for Minecraft 1.20.1.
 *
 * <p>Adds a full-screen Bill-of-Materials style crafting tree to JEC.</p>
 */
@Mod(JecaExMod.MODID)
public class JecaExMod {

    public static final String MODID = "jecaex";
    public static final String NAME = "Just Enough Calculation EX";
    public static final String VERSION = "0.3.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public JecaExMod() {
        LOGGER.info("Just Enough Calculation EX loaded.");
    }
}
