/*
 * WebMC JS-mod compatibility layer for Eaglercraft 26.2.
 *
 * Converted Fabric mods come from AmphibianDev/webmc-jsmod. Native VBV
 * implementations remain the fallback when a converted package fails.
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
    enabled: new Set(),
    register(info) {
      if (!info || !info.id) return;
      this.mods.set(info.id, info);
      if (info.defaultEnabled) this.enabled.add(info.id);
    },
    isLoaded(id) { return this.loaded.has(id); },
    isFailed(id) { return this.failed.has(id); },
    isEnabled(id) { return this.enabled.has(id); },
    status(id) {
      return this.isLoaded(id) ? "loaded" : this.isFailed(id) ? "failed" : this.isEnabled(id) ? "enabled" : "disabled";
    },
    setEnabled(id, value) {
      if (!this.mods.has(id)) return false;
      if (value) this.enabled.add(id); else this.enabled.delete(id);
      try { localStorage.setItem("vbv-jsmod-" + id, value ? "1" : "0"); } catch (_) {}
      return value;
    }
  };

  const MODS = [
    { id: "appleskin", name: "AppleSkin", file: "appleskin.js", size: 314300, native: "appleskin", auto: true, defaultEnabled: true },
    { id: "gammautils", name: "Gamma Utils", file: "gammautils.js", size: 419993, native: "gamma-utils", auto: true, defaultEnabled: true },
    { id: "jade", name: "Jade", file: "jade.js", size: 2157904, native: "jade", auto: true, defaultEnabled: true },
    { id: "zoomify", name: "Zoomify", file: "zoomify.js", size: 946715, native: "zoomify", auto: true, defaultEnabled: true },
    { id: "shulkerboxtooltip", name: "Shulker Box Tooltip", file: "shulkerboxtooltip.js", size: 823673, native: "shulkerboxtooltip", auto: true, defaultEnabled: true },
    { id: "jei", name: "JEI", file: "jei.js", size: 19304750, native: "jei", auto: false, large: true, defaultEnabled: false, toggleable: true }
  ];

  const BASE = "https://raw.githubusercontent.com/AmphibianDev/webmc-jsmod/main/mods/converted/";
  for (const mod of MODS) {
    JS.register(mod);
    if (mod.toggleable) {
      try {
        const saved = localStorage.getItem("vbv-jsmod-" + mod.id);
        if (saved === "1") JS.enabled.add(mod.id);
        if (saved === "0") JS.enabled.delete(mod.id);
      } catch (_) {}
    }
  }

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
  JS.toggle = async function (id, value) {
    const mod = JS.mods.get(id);
    if (!mod || !mod.toggleable) return false;
    const enabled = JS.setEnabled(id, value === undefined ? !JS.isEnabled(id) : !!value);
    if (!enabled) return true;
    return await load(id);
  };

  JS.loadAuto = async function () {
    for (const mod of MODS) {
      if (!mod.auto || !JS.isEnabled(mod.id)) continue;
      await load(mod.id);
      await new Promise(requestAnimationFrame);
    }
  };

  root.waitForGameStart?.(function () {
    JS.loadAuto().catch(error => console.error("[VBV JSMod] auto-loader", error));
  });

  window.VBVJSMods = JS;
})();
