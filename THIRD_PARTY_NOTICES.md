# Third-Party Notices

## Just Enough Calculation (JEC)

- Copyright: (c) Towdium
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)
- Source: https://github.com/Towdium/JustEnoughCalculation
- CurseForge: https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation

Use in this project:

- JEC is a required dependency and is installed separately by the user; this mod does not bundle JEC classes or the JEC jar.
- This mod calls JEC APIs and uses mixins that patch JEC GUI classes at runtime.
- `src/main/java/dev/jecaex/gregtech/GtRecipeTransferHelper.java` adapts recipe transfer, merge, and sort logic from JEC's `GuiRecipe.java`, which is distributed under LGPL-3.0. As a result, this project as a whole is distributed under LGPL-3.0. See [LICENSE](LICENSE).

## Other projects

- HEI (Had Enough Items) and GregTech CEu: runtime integrations accessed through reflection; not bundled.
- EMI `BoMScreen`: design inspiration only; no EMI code is copied or bundled.
