# Just Enough Calculation EX (1.20.1)

A Forge 1.20.1 addon for [Just Enough Calculation](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) that adds a full-screen, Bill-of-Materials style crafting tree.

- **modid**: `jecaex`
- **Version**: `0.3.0-mc1.20.1`
- **Loader**: Forge 1.20.1 (47.x)
- **License**: LGPL-3.0

> **AI-assisted development notice:** this project was designed, coded and debugged with AI (LLM) assistance.

---

## Current features (1.20.1 branch)

### Crafting Tree

Open the JEC crafting calculator and click the **Crafting Tree** button.

- Compact top-down recipe tree with the target at the top.
- Color coding: blue target, purple catalysts, green craftable inputs, red raw materials.
- Every node shows required amounts; a bottom bar summarizes total raw materials (items and fluids).
- Drag to pan, mouse wheel to zoom.
- Shift + left-click a recipe card to open the JEC recipe editor pre-filled with that recipe.
- Catalyst show/hide toggle.
- Works without GregTech.

> The optional GTCEu recipe-import enhancements from the 1.12.2 version are not yet ported to this branch.

---

## Requirements

- Minecraft 1.20.1 with Forge
- [Just Enough Calculation](https://www.curseforge.com/minecraft/mc-mods/just-enough-calculation) 4.0.4
- [Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api) 9.1.12
- [Just Enough Items](https://www.curseforge.com/minecraft/mc-mods/jei) 15.2.0.27

---

## Building

- JDK 17
- Gradle 8.4 (wrapper included)

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:/path/to/jdk-17"
.\gradlew.bat build --no-daemon
```

The output jar is:

```text
build/libs/Just Enough Calculation EX-0.3.0-mc1.20.1.jar
```

---

## Installation

1. Install JEC, Architectury and JEI in your 1.20.1 mods folder.
2. Put `Just Enough Calculation EX-0.3.0-mc1.20.1.jar` in the mods folder.
3. Open the JEC crafting calculator and click **Crafting Tree**.

---

## License

LGPL-3.0. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
