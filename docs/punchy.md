# Punchy resource packs (first-person hand animations)

VBV can play the first-person animations of a **Punchy** resource pack (for example HyperPunchy): swinging a sword,
mining, eating, drawing a bow, blocking with a shield, opening your hand when a map is held, and so on. While it is
switched on and a pack is loaded, it **replaces** the client's own first-person hand animations.

> **Status: written without being able to run the game.** The format reading, the item/action rules and the arm
> maths are covered by plain-JVM tests (`VBV/test/run-punchy-core-tests.sh`, also run by workflow 07), and the game
> side compiles against the 26.2 sources. It has **not been seen in the game**. How the arm and the held item are
> placed was worked out from the pack's data, so expect to use **Hand Position** to nudge the item, and expect some
> things to look off until it has been checked in the game. Please report what looks wrong.

## Using it

1. Add a Punchy resource pack the normal way (**Options > Resource Packs**), for example `HyperPunchy-v2.8.zip`.
2. Open **Options > Modded Settings > Hand Position...** and switch **Punchy resource-pack hands** to **ON**.
   The line under the buttons says what was loaded (for example `<n> animations, <m> item rules`) or why nothing was
   (`No Punchy pack loaded`).
3. The X / Y / Z rows nudge the held item (1/100 block) for the Punchy hands while Punchy is active, and for the
   client's own animation layer otherwise. They are saved separately (`punchy.x/y/z`).

The setting is **off by default**, is saved with the other VBV settings (`punchy.enabled`), and the pack is re-read
every time resources reload.

If the pack has no Punchy files, or drawing ever throws, Punchy stays out of the way and the normal hands are drawn;
after an error it switches itself off (the status line says so). The off state is saved with your other settings the
next time they are saved, so switch it on again from **Hand Position...** once you have a pack that works.

## What it reads from the pack

| File | Used for |
| --- | --- |
| `assets/minecraft/punchy/animations/*.animation.json` | The clips (Blockbench / Bedrock animation format): bone rotation, position and scale keyframes, `loop` (`true`, `hold_on_last_frame`, play once), `animation_length`, `timeline` commands and `sound_effects`. |
| `assets/minecraft/punchy/compat/**.json` | Which clip plays for which action on which item: `default_animations`, per-category rules (`SWORD`, `PICKAXE`, `BOW`, `EMPTY_HAND`, ...), `customGroups` and per-item `itemSpecific` rules, each layer replacing the rules of the same type beneath it. `disable_mining_animation` is honoured. |
| `assets/minecraft/punchy/geo/player.geo.json` (optional) | The arm rig. When the pack does not ship one, built-in numbers are used. |
| `assets/punchy/sounds.json` and `assets/punchy/sounds/animationssounds/*.ogg` | Sound effects named by a clip, played as `punchy:animationssounds/<effect>`. A name the pack has no sound for is skipped. |

Timeline commands understood: `anim_speed_<n>` (playback speed), `hide_item`; other commands are ignored.

## What triggers what

`attack` (left click), `hand_in` (equipping / switching item), `mining` / `mining_v2` (attack held on a block in
survival), `useItem`, `drinking`, `use_bow` / `use_bow_shot`, `use_crossbow`, `use_shield`, `spear_charge`, and the
empty-hand rules. Attacking while using an item is ignored. Clips blend from the previous pose
instead of jumping.

## Known limits (version 1)

- **Mining is a stand-in.** The game does not say whether you are breaking a block, so "attack held while aiming at a
  block, in survival, not using an item" counts as mining. Creative-mode block breaking does not trigger it.
- **Not handled yet:** the `interaction` and `inspect` rules, fishing-rod specifics, and the camera bones (the pack's
  camera shake moves the viewpoint in the original mod; here only the arm and item animation is used).
- **Maps** are still drawn by the game itself, and nothing is drawn for an invisible player.
- **The item frame is calibrated by reasoning, not by looking.** The arm uses the game's own arm model placed on the
  pack's arm bone, and the item uses the game's own first-person item model in the pack's item grip frame. If the
  item sits at the wrong angle or distance, **Hand Position** moves it; tell me what you see so the maths can be fixed.
- The game's own `0.1` arm tilt is not applied to the Punchy arm.

## Where the code is

- `VBV/src/main/java/com/melonman106/vbvclient/punchy/` — no game classes: `PunchyClip` (animation file),
  `PunchyLibrary` (finds clips), `PunchyRules` (compat files), `PunchyRig` (arm and item maths),
  `PunchyController` (which clip plays now, blending, sounds).
- `VBV/src/main/java/com/melonman106/vbvclient/VBVPunchy.java` — reads the pack, collects the player's state and draws.
- `VBV/patch/patch_punchy_hooks.py` — two small hooks: `ItemInHandRenderer.submitArmWithItem` (offers each hand to
  VBVPunchy first) and `EntityRenderDispatcher.onResourceManagerReload` (re-reads the pack).
- `VBV/test/run-punchy-core-tests.sh` — the plain-JVM checks. Set `PUNCHY_PACK=<unzipped pack folder>` to also check
  the real pack's files.

## Credit and licence

The Punchy mod and the HyperPunchy pack are other people's work. Nothing from the Punchy mod's code is copied here: the
format and the rig numbers were read from the pack's own data files. The animations and sounds belong to the pack and
are only read at runtime from the pack the player adds.
