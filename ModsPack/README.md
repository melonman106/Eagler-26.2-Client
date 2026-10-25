# Eagler 26.2 ModsPack

This directory is the build-time input for ported Eagler client mods and resource-pack mods.

## Current package format

`apply_vbv_patch.py` currently imports `.zip` files as resource-pack client mods. Each ZIP may contain `pack.mcmeta`, `pack.png`, `assets/`, and `data/`.

## Resource-pack icon placement

For a resource-pack mod's icon, put a file named exactly `pack.png` in the **root of that pack ZIP**, beside `pack.mcmeta` (not inside `assets/`, and not in the repository's `ModsPack/` folder by itself). For example:

```
MyPack.zip
├── pack.mcmeta
├── pack.png
└── assets/
    └── ...
```

The build copies that icon into `assets/vbvclient/mods/<pack-id>/pack.png` and the Mods screen reads it from there. If you have a loose image file, add it to the ZIP root before running the compile workflow. If a resource-pack ZIP has no icon, an optional shared fallback icon can be placed at `VBV/pack.png`. Built-in native VBV mods currently use Minecraft's `unknown_pack.png` icon; their individual icon paths are configured in `VBV/src/main/java/com/melonman106/vbvclient/VBVClient.java`.

## Planned native-mod package format

Ported Java mods will use an Eagler-specific package rather than being loaded as arbitrary Fabric JARs. The package will carry metadata such as:

- `id`
- `name`
- `version`
- `minecraft`
- `eagler`
- `dependencies`
- `authors`
- `description`
- `icon`
- `type` (`native` or `resource-pack`)

The native package will contain code that has already been compiled for the Eagler/TeaVM runtime. Fabric Loader and the full Fabric runtime are not required in the browser client.

## Website distribution

A future GitHub Pages mod site can publish a signed/hashed registry of these packages. The Eagler client can then consume that registry through a normal web request and offer an in-game mod browser/downloader, subject to browser/CORS and package-size constraints.

The existing native Mods screen and generated `VBVGeneratedPackMods` registry are the starting point for that system.
