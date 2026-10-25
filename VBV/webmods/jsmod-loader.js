/*
 * WebMC JS-mod compatibility layer for Eaglercraft 26.2.
 *
 * The converted Fabric mods come from AmphibianDev/webmc-jsmod. They are loaded
 * only after the native VBV feature layer is present, so a failed converted mod
 * can never replace the working native fallback.
 *
 * The build embeds webmc.js and the selected converted modules directly into the
 * standalone HTML. Large mods (JEI, Litematica, Meteor) remain lazy-loadable.
 */
(function () {
  "use strict";

  const root = window.VBVClientMods = window.VBVClientMods || {};
  const JS = root.jsmods = root.jsmods || {
    version: "1.0.0",
    source: "AmphibianDev/webmc-jsmod",
    mods: new Map(),
    loaded: new Set(),
    failed: new Map(),
    register(info) {
      if (!info || !info.id) return;
      this.mods.set(info.id, info);
    },
    isLoaded(id) { return this.loaded.has(id); },
    isFailed(id) { return this.failed.has(id); },
    status(id) {
      return this.isLoaded(id) ? "loaded" : this.isFailed(id) ? "failed" : "available";
    }
  };

  const MODS = [
    { id: "appleskin", name: "AppleSkin", file: "appleskin.js", size: 314300, native: "appleskin", auto: true },
    { id: "gammautils", name: "Gamma Utils", file: "gammautils.js", size: 419993, native: "gamma-utils", auto: true },
    { id: "jade", name: "Jade", file: "jade.js", size: 2157904, native: "jade", auto: true },
    { id: "zoomify", name: "Zoomify", file: "zoomify.js", size: 946715, native: "zoomify", auto: true },
    { id: "shulkerboxtooltip", name: "Shulker Box Tooltip", file: "shulkerboxtooltip.js", size: 823673, native: "shulkerboxtooltip", auto: true },
    { id: "jei", name: "JEI", file: "jei.js", size: 19304750, native: "jei", auto: false, large: true },
    { id: "litematica", name: "Litematica", file: "litematica.js", size: 10914173, native: "litematica", auto: false, large: true },
    { id: "meteor-client", name: "Meteor Client", file: "meteor-client.js", size: 21655584, native: "meteor-client", auto: false, large: true }
  ];

  const BASE = "https://raw.githubusercontent.com/AmphibianDev/webmc-jsmod/main/mods/converted/";
  for (const mod of MODS) JS.register(mod);

  function runInline(id) {
    const marker = document.querySelector('script[data-vbv-jsmod="' + CSS.escape(id) + '"]');
    if (!marker) return false;
    try {
      (0, eval)(marker.textContent || "");
      JS.loaded.add(id);
      return true;
    } catch (error) {
      JS.failed.set(id, error);
      console.error("[VBV JSMod] " + id, error);
      return false;
    }
  }

  async function load(id) {
    if (JS.loaded.has(id)) return true;
    if (JS.failed.has(id)) return false;
    const mod = JS.mods.get(id);
    if (!mod) return false;

    if (runInline(id)) return true;

    try {
      const response = await fetch(BASE + encodeURIComponent(mod.file), { cache: "force-cache" });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const source = await response.text();
      const script = document.createElement("script");
      script.dataset.vbvJsmod = id;
      script.textContent = source;
      document.head.appendChild(script);
      JS.loaded.add(id);
      return true;
    } catch (error) {
      JS.failed.set(id, error);
      console.error("[VBV JSMod] Could not load " + id, error);
      return false;
    }
  }

  JS.load = load;
  JS.loadAuto = async function () {
    for (const mod of MODS) {
      if (!mod.auto) continue;
      // Yield between large parser/evaluation jobs so a Chromebook does not get a long task.
      await load(mod.id);
      await new Promise(requestAnimationFrame);
    }
  };

  root.waitForGameStart?.(function () {
    // The native ports remain the authoritative fallback. JS conversions are enhancements.
    JS.loadAuto().catch(error => console.error("[VBV JSMod] auto-loader", error));
  });

  window.VBVJSMods = JS;
})();
