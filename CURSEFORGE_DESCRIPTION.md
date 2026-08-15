# Just Enough Calculation EX

**A quality-of-life addon for [Just Enough Calculation](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) in GregTech CEu packs.**

Minecraft **1.12.2** | Forge | Version **0.2.2** | MIT License

> **Transparency note:** this project was developed with AI assistance (design, coding, and debugging).

---

## What this mod does

Just Enough Calculation EX (JEC EX) fixes GregTech CEu recipe importing into JEC and adds a full-screen, Bill-of-Materials style crafting tree. It is designed for heavy GregTech packs such as Nomifactory CEu.

### 1. GregTech CEu recipe import fixes

When you click the **+** button in JEI/HEI to import a GregTech CEu machine recipe into JEC:

- Machines no longer appear as **"Unnamed"** blank blocks caused by fuzzy-meta folding.
- **Non-consumed inputs** (circuits, molds, lenses, tools, and similar items) are routed into the **catalyst slot** instead of the ingredient slot.
- The correct machine is selected by **voltage tier** - for example, an MV recipe shows the MV machine, and steam machines are skipped.

### 2. Crafting Tree view

Click the **Crafting Tree** button in the JEC crafting calculator to open a full-screen BoM-style recipe tree.

- Compact, top-down layout: parent recipe inputs point directly to their child recipes, with no redundant intermediate output boxes.
- Color-coded nodes:
  - **Blue** - target item
  - **Purple** - catalysts
  - **Green** - craftable inputs
  - **Red** - raw materials
- A fixed bottom bar summarizes **total raw materials** (items and fluids).
- **Pan** by dragging, **zoom** with the mouse wheel.
- **Shift + left-click** a recipe card to open the JEC recipe editor pre-filled with that recipe, then save to add it to your calculator.
- Toggle catalysts on and off with the button in the top-right corner; the layout reflows automatically.
- Large trees are rendered with caching and viewport culling for better performance.

---

## Requirements

| Mod | Status | Notes |
|---|---|---|
| Minecraft Forge 1.12.2 | Required | |
| [Just Enough Calculation](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) | Required | Version 3.2.7 is tested |
| [Just Enough Items (JEI)](https://www.curseforge.com/minecraft/mc-mods/jei) | Required | HEI is a supported runtime drop-in replacement |
| [MixinBooter](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) | Required | Provides the late mixin loader used by this mod |
| [GregTech CEu](https://www.curseforge.com/minecraft/mc-mods/gregtech-ceu-unofficial) | Required in practice | All features target GTCEu recipes; GTCEu is accessed via reflection |

---

## Installation

1. Install Forge for Minecraft 1.12.2.
2. Install the required mods listed above. If you use HEI, do **not** install JEI at the same time.
3. Download `jecaex-<version>.jar` from the Files tab.
4. Put the jar into your `mods` folder.
5. Launch the game and open the JEC crafting calculator to see the **Crafting Tree** button.

---

## Usage

- **Import a recipe:** open a GregTech CEu recipe in JEI/HEI and click **+**.
- **Open the crafting tree:** open the JEC crafting calculator and click **Crafting Tree**.
- **Navigate:** drag to pan, scroll to zoom.
- **Add a recipe to the calculator:** Shift + left-click a recipe card, review the pre-filled recipe editor, then save.
- **Show/hide catalysts:** use the button in the top-right corner of the crafting tree.

---

## Compatibility

Tested environment:

- Nomifactory CEu
- JEC 3.2.7
- HEI (Had Enough Items) 4.30.3
- GTCEu 2.8.10-beta
- MixinBooter 10.7

---

## Technical notes

- Uses an **ILateMixinLoader** because the mixin targets JEC GUI classes.
- All GregTech CEu access is done through **reflection**, so there is no compile-time dependency on a specific GTCEu build.
- The crafting tree design is inspired by EMI's `BoMScreen`.

---

## Source and license

- GitHub: [LeafHui/JustEnoughCalculationEX](https://github.com/LeafHui/JustEnoughCalculationEX)
- License: [MIT](https://github.com/LeafHui/JustEnoughCalculationEX/blob/main/LICENSE)
