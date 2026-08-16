# Local dependency jars

This directory contains a local copy of the JEC 1.20.1 Forge production jar used as a compile
dependency because JEC does not publish a Maven artifact for the 1.20.1 branch.

- `jecalculation-4.0.4.jar` - built from https://github.com/Towdium/JustEnoughCalculation branch `1.20.1`
- `jecalculation-4.0.4-sources.jar` - corresponding sources jar

These jars are used with `compileOnly` / `runtimeOnly` and are **not bundled** into the
`jecaex` mod jar. JEC is a required external mod at runtime and is installed by the user.

JEC is licensed under LGPL-3.0. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
