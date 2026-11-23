"""Merge resource-pack font and atlas definitions into the game's native ones.

Resource packs add custom glyphs (Enchant Icons, the Boss Bar pack) through
``assets/<namespace>/font/<name>.json``. The pack importer used to copy that file over the
native definition, which deleted every native provider and turned all text into tofu, so the
build then skipped pack font files entirely, and the custom characters never rendered.

``vbv_merge_font_definition`` does what a real resource-pack stack does: it keeps the native
providers and puts the pack's own providers in front of them. Every pack provider is checked
first, so one bad entry can never take the default font down with it.

Texture atlas definitions (``assets/<namespace>/atlases/<name>.json``) get the same treatment.
A pack such as visual_armor_trims ships an atlas file with only its own sources; copied over the
native ``items.json`` it removed the ``directory item/`` source and every item texture went
missing. Minecraft combines the ``sources`` of every pack's atlas file, so that is what
``vbv_merge_atlas_definition`` does.

apply_vbv_patch.py pastes this file's source into the pinned upstream pack importer, so it
may only use the standard library and the names it defines itself.
"""


def vbv_font_texture_ref(file_id):
    """'minecraft:misc/helmet.png' -> ('minecraft', 'textures/misc/helmet.png')."""
    namespace, _, path = file_id.rpartition(":")
    namespace = namespace or "minecraft"
    return namespace, "textures/" + path


def vbv_valid_font_provider(provider, texture_exists):
    """Return (ok, reason). Only the provider shapes Eagler's font loader is known to handle."""
    if not isinstance(provider, dict):
        return False, "not an object"
    kind = provider.get("type")
    if "filter" in provider and not isinstance(provider["filter"], dict):
        return False, "bad filter"
    if kind == "bitmap":
        file_id = provider.get("file")
        if not isinstance(file_id, str) or not file_id.lower().endswith(".png"):
            return False, "bitmap file is not a .png id"
        if file_id.count(":") > 1 or ".." in file_id or file_id.startswith("/"):
            return False, "unsafe bitmap file id"
        height = provider.get("height", 8)
        ascent = provider.get("ascent")
        if isinstance(height, bool) or not isinstance(height, int) or not 1 <= height <= 256:
            return False, "bitmap height out of range"
        if isinstance(ascent, bool) or not isinstance(ascent, int) or ascent > height:
            return False, "bitmap ascent missing or above height"
        rows = provider.get("chars")
        if not isinstance(rows, list) or not rows or not all(isinstance(r, str) and r for r in rows):
            return False, "bitmap chars must be a non-empty list of strings"
        if len({len(r) for r in rows}) != 1:
            return False, "bitmap rows differ in length"
        if any(0xD800 <= ord(c) <= 0xDFFF for r in rows for c in r):
            return False, "bitmap chars contain a lone surrogate"
        namespace, path = vbv_font_texture_ref(file_id)
        if not texture_exists(namespace, path):
            return False, "texture " + namespace + ":" + path + " is not in the pack or the game"
        return True, ""
    if kind == "space":
        advances = provider.get("advances")
        if not isinstance(advances, dict) or not advances:
            return False, "space advances missing"
        for key, value in advances.items():
            if len(key) != 1 or isinstance(value, bool) or not isinstance(value, (int, float)):
                return False, "bad space advance"
        return True, ""
    # "reference" is refused on purpose: pointing at a font that does not exist would leave the
    # whole default font unresolved. ttf/unihex/legacy_unicode need libraries Eagler does not ship.
    return False, "provider type " + repr(kind) + " is not supported in Eagler"


def vbv_merge_font_definition(incoming_bytes, target, source_label, texture_exists):
    """Merge one pack font JSON into ``target`` (a pathlib.Path). Returns a status line.

    The native file is never replaced and never left unreadable: an unreadable native file is
    left alone, and the result is only written when at least one pack provider is valid.
    """
    import json

    try:
        incoming = json.loads(incoming_bytes.decode("utf-8-sig"))
    except (UnicodeDecodeError, ValueError) as exc:
        return "[VBV font merge] skipped " + source_label + ": not valid JSON (" + str(exc) + ")"
    providers = incoming.get("providers") if isinstance(incoming, dict) else None
    if not isinstance(providers, list):
        return "[VBV font merge] skipped " + source_label + ": no providers list"

    accepted = []
    notes = []
    for index, provider in enumerate(providers):
        ok, reason = vbv_valid_font_provider(provider, texture_exists)
        if ok:
            accepted.append(provider)
        else:
            notes.append("provider " + str(index) + " dropped (" + reason + ")")
    if not accepted:
        return "[VBV font merge] skipped " + source_label + ": no usable providers" + (
            " - " + "; ".join(notes) if notes else "")

    if target.is_file():
        try:
            native = json.loads(target.read_text(encoding="utf-8-sig"))
        except (UnicodeDecodeError, ValueError):
            return "[VBV font merge] left the native " + target.name + " alone: it could not be read"
        if not isinstance(native, dict) or not isinstance(native.get("providers"), list):
            return "[VBV font merge] left the native " + target.name + " alone: unexpected layout"
    else:
        native = {"providers": []}

    seen = {json.dumps(p, sort_keys=True) for p in native["providers"]}
    fresh = [p for p in accepted if json.dumps(p, sort_keys=True) not in seen]
    # Pack glyphs go first so they win over native ones for the same character, like a pack stack.
    native["providers"] = fresh + native["providers"]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(native, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    message = "[VBV font merge] " + source_label + ": added " + str(len(fresh)) + " glyph provider(s) to " + target.name
    if len(fresh) != len(accepted):
        message += " (" + str(len(accepted) - len(fresh)) + " already present)"
    if notes:
        message += "; " + "; ".join(notes)
    return message


def vbv_clean_model_definition(incoming_bytes, source_label):
    """Fix a pack model that cannot load in this version, or return None to copy it unchanged.

    1.21.4 replaced the "builtin/entity" item models with ``minecraft:special``. A pack written for
    1.20 such as Vanilla Tweaks still ships ``models/item/shield.json`` with
    ``"parent": "builtin/entity"``: that parent is now looked up as a model id, does not exist, and
    the shield's base model falls back to the missing model (the checkerboard shield).

    The file also cannot simply be kept with the parent removed: its ``display`` transforms were
    written for the 1.20 entity-rendered shield, and applied on top of the 1.21.4 special renderer
    they put the shield's handle on the wrong side of the hand. Vanilla owns the shield's shape and
    transforms now, so the correct repair is to leave the file out entirely and let the game's own
    ``models/item/shield.json`` and ``minecraft:special`` renderer draw it with the pack's textures.

    Returns the replacement bytes, ``b""`` to mean "skip this file", or None to copy it unchanged.
    """
    import json

    try:
        model = json.loads(incoming_bytes.decode("utf-8-sig"))
    except (UnicodeDecodeError, ValueError):
        return None
    if not isinstance(model, dict):
        return None
    parent = model.get("parent")
    if not isinstance(parent, str) or not parent.startswith("builtin/"):
        return None
    if parent == "builtin/generated":
        # Still supported: it is the generated item model this game itself uses.
        return None
    print(
        "[VBV model fix] "
        + source_label
        + ": skipped, its parent "
        + repr(parent)
        + " was removed in 1.21.4 and the game's special item renderer owns this item now"
    )
    return b""


def vbv_orient_shield_item_definition(incoming_bytes, source_label):
    """Turn a pack's shield 180 degrees back to the orientation the game draws it in.

    The shield's orientation lives in its item definition now: the game's
    ``items/shield.json`` wraps the ``minecraft:special`` shield model in
    ``"transformation": {"scale": [1, -1, -1]}``. That is a 180 degree rotation about X, which
    turns the model-space shield round so it faces out of the hand instead of back at the player.

    A pack that replaces the item definition (the Enchantment Outlines pack does, to add its glowing
    overlay) ships it without that transformation, so the shield renders backwards. The rotation is
    put back on each shield special model; every other entry, including the pack's own overlay
    models, is left exactly as the pack wrote it.

    Returns the replacement bytes, or None to copy the file unchanged.
    """
    import json

    try:
        definition = json.loads(incoming_bytes.decode("utf-8-sig"))
    except (UnicodeDecodeError, ValueError):
        return None
    if not isinstance(definition, dict):
        return None
    if vbv_orient_special_shields(definition) == 0:
        return None
    print(
        "[VBV model fix] "
        + source_label
        + ": rotated the shield 180 degrees into the orientation the game's own item definition uses"
    )
    return (json.dumps(definition, ensure_ascii=True, indent=2) + "\n").encode("utf-8")


def vbv_orient_special_shields(node):
    """Add the game's 180 degree shield rotation to every shield special model in ``node``.

    Returns how many models were oriented, so the caller only rewrites a file that changed. A model
    that already has a ``transformation`` is left alone: the pack has positioned it itself.
    """
    if isinstance(node, list):
        return sum(vbv_orient_special_shields(entry) for entry in node)
    if not isinstance(node, dict):
        return 0
    count = sum(vbv_orient_special_shields(value) for value in node.values())
    special_model = node.get("model")
    if (
        node.get("type") == "minecraft:special"
        and isinstance(special_model, dict)
        and special_model.get("type") == "minecraft:shield"
        and "transformation" not in node
    ):
        # x, y, z scaled by 1, -1, -1 is a 180 degree rotation about X, the game's own shield flip.
        node["transformation"] = {
            "left_rotation": [0.0, 0.0, 0.0, 1.0],
            "right_rotation": [0.0, 0.0, 0.0, 1.0],
            "scale": [1.0, -1.0, -1.0],
            "translation": [0.0, 0.0, 0.0],
        }
        count += 1
    return count


def vbv_valid_atlas_source(source):
    """Return (ok, reason). Mirrors what Minecraft's atlas source codecs require, because one
    malformed source makes the whole atlas fail to load."""

    def is_str(value):
        return isinstance(value, str) and value != ""

    def is_num(value):
        return not isinstance(value, bool) and isinstance(value, (int, float))

    if not isinstance(source, dict) or not isinstance(source.get("type"), str):
        return False, "source has no type"
    kind = source["type"]
    if ":" not in kind:
        kind = "minecraft:" + kind
    if kind == "minecraft:directory":
        if is_str(source.get("source")) and isinstance(source.get("prefix"), str):
            return True, ""
        return False, "directory needs source and prefix"
    if kind == "minecraft:single":
        if is_str(source.get("resource")) and ("sprite" not in source or is_str(source["sprite"])):
            return True, ""
        return False, "single needs resource"
    if kind == "minecraft:filter":
        return (isinstance(source.get("pattern"), dict), "filter needs a pattern")
    if kind == "minecraft:unstitch":
        regions = source.get("regions")
        if (
            is_str(source.get("resource"))
            and isinstance(regions, list)
            and is_num(source.get("divisor_x"))
            and is_num(source.get("divisor_y"))
        ):
            return True, ""
        return False, "unstitch needs resource, regions and divisors"
    if kind == "minecraft:paletted_permutations":
        textures = source.get("textures")
        permutations = source.get("permutations")
        if (
            isinstance(textures, list)
            and all(is_str(x) for x in textures)
            and is_str(source.get("palette_key"))
            and isinstance(permutations, dict)
            and all(is_str(k) and is_str(v) for k, v in permutations.items())
            and ("separator" not in source or isinstance(source["separator"], str))
        ):
            return True, ""
        return False, "paletted_permutations needs textures, palette_key and permutations"
    return False, "source type " + repr(source["type"]) + " is not supported"


def vbv_merge_atlas_definition(incoming_bytes, target, source_label):
    """Merge one pack atlas JSON into ``target`` (a pathlib.Path). Returns a status line.

    Pack sources are appended after the native ones, which is how Minecraft orders them (a later
    source wins when two produce the same sprite). The native file is never replaced.
    """
    import json

    try:
        incoming = json.loads(incoming_bytes.decode("utf-8-sig"))
    except (UnicodeDecodeError, ValueError) as exc:
        return "[VBV atlas merge] skipped " + source_label + ": not valid JSON (" + str(exc) + ")"
    sources = incoming.get("sources") if isinstance(incoming, dict) else None
    if not isinstance(sources, list):
        return "[VBV atlas merge] skipped " + source_label + ": no sources list"

    accepted = []
    notes = []
    for index, source in enumerate(sources):
        ok, reason = vbv_valid_atlas_source(source)
        if ok:
            accepted.append(source)
        else:
            notes.append("source " + str(index) + " dropped (" + reason + ")")
    if not accepted:
        return "[VBV atlas merge] skipped " + source_label + ": no usable sources" + (
            " - " + "; ".join(notes) if notes else "")

    if target.is_file():
        try:
            native = json.loads(target.read_text(encoding="utf-8-sig"))
        except (UnicodeDecodeError, ValueError):
            return "[VBV atlas merge] left the native " + target.name + " alone: it could not be read"
        if not isinstance(native, dict) or not isinstance(native.get("sources"), list):
            return "[VBV atlas merge] left the native " + target.name + " alone: unexpected layout"
    else:
        native = {"sources": []}

    seen = {json.dumps(s, sort_keys=True) for s in native["sources"]}
    fresh = [s for s in accepted if json.dumps(s, sort_keys=True) not in seen]
    native["sources"] = native["sources"] + fresh
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(native, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    message = "[VBV atlas merge] " + source_label + ": added " + str(len(fresh)) + " source(s) to " + target.name
    if len(fresh) != len(accepted):
        message += " (" + str(len(accepted) - len(fresh)) + " already present)"
    if notes:
        message += "; " + "; ".join(notes)
    return message
