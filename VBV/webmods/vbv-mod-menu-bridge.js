/* VBV browser bridge for the immutable Eagler HTML client. */
(function () {
  const nativeMods = [
    {
      id: "vbv-resource-packs",
      name: "Resource Packs",
      description: "Import and manage local ZIP resource packs.",
      open() { window.VBVResourcePacks?.openUI?.(); }
    },
    {
      id: "ukus-armor-hud",
      name: "uku's Armor HUD",
      description: "Minimal vanilla-style armor HUD for 26.2.",
      open() { window.VBVArmorHud?.openConfig?.(); }
    }
  ];

  window.VBVModMenu = window.VBVModMenu || {};
  window.VBVModMenu.mods = nativeMods;

  function registerMetadata() {
    try {
      const api = window.ModAPI || window.PluginAPI;
      if (!api || !api.meta) return;
      api.meta.title("VBV Client Mods");
      api.meta.description("Native VBV ports plus converted Fabric JS mods");
    } catch (_) {}
  }

  // WebMC/JSMod packages can leave a small DOM control at the browser's
  // upper-left corner. Eagler's actual HUD is canvas-based, so remove only
  // fixed-position HTML buttons occupying that corner.
  function removeStrayTopLeftButtons() {
    for (const button of document.querySelectorAll("button")) {
      const rect = button.getBoundingClientRect();
      const style = getComputedStyle(button);
      if (style.position === "fixed" && rect.left <= 8 && rect.top <= 8 && rect.width <= 320 && rect.height <= 80) {
        button.remove();
      }
    }
  }

  function makePanel() {
    if (document.getElementById("vbv-mod-menu")) return;

    const panel = document.createElement("div");
    panel.id = "vbv-mod-menu";
    Object.assign(panel.style, {
      position: "fixed", left: "50%", top: "50%",
      transform: "translate(-50%,-50%)", width: "460px",
      maxWidth: "calc(100vw - 32px)", maxHeight: "calc(100vh - 32px)",
      overflow: "auto", padding: "14px", boxSizing: "border-box",
      background: "rgba(15,15,15,.96)", color: "white",
      border: "2px solid #777", borderRadius: "4px",
      fontFamily: "sans-serif", zIndex: "2147483647", display: "none"
    });

    const title = document.createElement("div");
    title.textContent = "Mods";
    Object.assign(title.style, {fontSize:"20px",fontWeight:"bold",marginBottom:"12px"});
    panel.appendChild(title);

    const note = document.createElement("div");
    note.textContent = "Native ports stay active; converted Fabric mods are optional enhancements.";
    Object.assign(note.style, {fontSize:"11px",opacity:".65",marginBottom:"8px"});
    panel.appendChild(note);

    function addRow(mod, converted) {
      const row = document.createElement("button");
      row.type = "button";
      Object.assign(row.style, {
        display:"block", width:"100%", textAlign:"left", margin:"6px 0",
        padding:"10px", background:"#303030", color:"white",
        border:"1px solid #666", borderRadius:"3px", cursor:"pointer"
      });
      const name = document.createElement("div");
      name.textContent = mod.name;
      name.style.fontWeight = "bold";
      const desc = document.createElement("div");
      desc.textContent = mod.description || (converted ? "Converted Fabric mod." : "Native VBV mod.");
      desc.style.fontSize = "12px";
      desc.style.opacity = ".75";
      const status = document.createElement("div");
      status.style.fontSize = "11px";
      status.style.opacity = ".55";
      row.append(name, desc, status);

      if (converted) {
        const refreshStatus = () => {
          const api = window.VBVJSMods;
          if (mod.toggleable) {
            const enabled = api?.isEnabled?.(mod.id) || false;
            const loaded = api?.isLoaded?.(mod.id) || false;
            status.textContent = enabled ? (loaded ? "JEI: ON" : "JEI: ON (loading)") : "JEI: OFF";
            row.setAttribute("aria-pressed", enabled ? "true" : "false");
          } else {
            const s = api?.status?.(mod.id) || "available";
            status.textContent = s === "loaded" ? "Converted: loaded" : s === "failed" ? "Converted: failed; native fallback remains" : "Converted: click to load";
          }
        };
        refreshStatus();
        row.addEventListener("click", async () => {
          const api = window.VBVJSMods;
          if (mod.toggleable) {
            status.textContent = "JEI: toggling…";
            await api?.toggle?.(mod.id);
            refreshStatus();
          } else {
            status.textContent = "Loading converted mod…";
            const ok = await api?.load?.(mod.id);
            status.textContent = ok ? "Converted: loaded" : "Converted: failed; native fallback remains";
          }
        });
      } else {
        row.addEventListener("click", () => mod.open());
      }
      panel.appendChild(row);
    }

    for (const mod of nativeMods) addRow(mod, false);

    const converted = window.VBVJSMods;
    if (converted?.mods) {
      const heading = document.createElement("div");
      heading.textContent = "Converted Fabric mods";
      Object.assign(heading.style, {fontSize:"13px",fontWeight:"bold",margin:"14px 0 6px"});
      panel.appendChild(heading);
      for (const mod of converted.mods.values()) {
        const size = mod.size ? ` ${(mod.size / 1048576).toFixed(1)} MB` : "";
        addRow({
          ...mod,
          description: mod.toggleable ? "Optional JEI integration." : ((mod.large ? "Large optional package." : "Converted Fabric package.") + size + ".")
        }, true);
      }
    }

    const close = document.createElement("button");
    close.type = "button";
    close.textContent = "Close";
    Object.assign(close.style, {marginTop:"10px",padding:"7px 14px",cursor:"pointer"});
    close.onclick = () => { panel.style.display = "none"; };
    panel.appendChild(close);

    document.body.appendChild(panel);
    window.VBVModMenu.open = () => { panel.style.display = "block"; };
    window.VBVModMenu.close = () => { panel.style.display = "none"; };
  }

  function start() {
    makePanel();
    registerMetadata();
    removeStrayTopLeftButtons();
    const cleanupTimer = setInterval(removeStrayTopLeftButtons, 1000);
    setTimeout(() => clearInterval(cleanupTimer), 15000);

    window.addEventListener("keydown", (e) => {
      if (e.repeat || e.ctrlKey || e.altKey || e.metaKey) return;
      if (e.key.toLowerCase() === "m" && document.activeElement?.tagName !== "INPUT") {
        const panel = document.getElementById("vbv-mod-menu");
        if (panel) panel.style.display = panel.style.display === "none" ? "block" : "none";
      }
      if (e.key === "Escape") window.VBVModMenu.close?.();
    }, true);
  }

  if (window.VBVClientMods) {
    window.VBVClientMods.waitForGameStart(start);
  } else {
    const timer = setInterval(() => {
      if (window.VBVClientMods) { clearInterval(timer); window.VBVClientMods.waitForGameStart(start); }
    }, 100);
  }
})();
