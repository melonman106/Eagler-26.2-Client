/* VBV_STORAGE_MANAGER_V1 */
(function () {
  "use strict";
  if (window.VBVStorage && window.VBVStorage.version) return;

  const DB_NAME = "vbv-storage-handles-v1";
  const STORE = "handles";
  const LAYOUT = [
    "Worlds", "WorldMap", "Resource Packs", "Resource Packs/Global",
    "Resource Packs/Servers", "Datapacks", "Datapacks/Global",
    "Datapacks/Servers", "Server Profiles", "Backups", "Backups/WorldMap",
    "Backups/Worlds", "Cache", "Settings"
  ];
  const cache = new Map();
  const pending = new Map();
  let root = null;
  let rootName = "";
  let revision = 0;
  let activeAddress = "";
  let activeProfile = null;
  let writeError = "";
  let dbPromise;

  function openDB() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise((resolve, reject) => {
      if (!("indexedDB" in window)) return reject(new Error("IndexedDB is unavailable"));
      const request = indexedDB.open(DB_NAME, 1);
      request.onupgradeneeded = () => request.result.createObjectStore(STORE);
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Could not open storage metadata"));
    });
    return dbPromise;
  }
  async function handleGet(key) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const request = db.transaction(STORE, "readonly").objectStore(STORE).get(key);
      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error);
    });
  }
  async function handleSet(key, value) {
    const db = await openDB();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(value, key);
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  }
  async function permission(handle, ask) {
    if (!handle) return false;
    const opts = { mode: "readwrite" };
    let state = await handle.queryPermission(opts);
    if (state !== "granted" && ask) state = await handle.requestPermission(opts);
    return state === "granted";
  }
  async function dir(path, create) {
    if (!root) throw new Error("Choose an Eaglercraft storage folder first.");
    let current = root;
    for (const part of path.split("/").filter(Boolean))
      current = await current.getDirectoryHandle(part, { create: !!create });
    return current;
  }
  async function readFile(path) {
    const slash = path.lastIndexOf("/");
    const folder = await dir(slash < 0 ? "" : path.slice(0, slash), false);
    const handle = await folder.getFileHandle(path.slice(slash + 1));
    return await (await handle.getFile()).text();
  }
  async function writeFile(path, content, backup) {
    const slash = path.lastIndexOf("/");
    const folder = await dir(slash < 0 ? "" : path.slice(0, slash), true);
    const name = path.slice(slash + 1);
    if (backup) {
      try {
        const old = await folder.getFileHandle(name);
        const oldText = await (await old.getFile()).text();
        if (oldText && oldText !== content) {
          const backupDir = await dir("Backups/WorldMap", true);
          const backupName = name + ".previous";
          const backupHandle = await backupDir.getFileHandle(backupName, { create: true });
          const backupWriter = await backupHandle.createWritable();
          await backupWriter.write(oldText);
          await backupWriter.close();
        }
      } catch (_) {}
    }
    const handle = await folder.getFileHandle(name, { create: true });
    const writer = await handle.createWritable();
    await writer.write(content);
    await writer.close();
  }
  function pathForKey(key) {
    const prefix = "vbv.worldmap.v1.";
    if (key.startsWith(prefix)) {
      const token = key.slice(prefix.length);
      let decoded = "";
      try {
        let base64 = token.replace(/-/g, "+").replace(/_/g, "/");
        while (base64.length % 4) base64 += "=";
        const binary = atob(base64);
        const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));
        decoded = new TextDecoder().decode(bytes);
      } catch (_) {}
      const parts = decoded.split("|");
      const owner = parts.length > 1 ? parts[1] : "unknown";
      return "WorldMap/" + serverSlug(owner) + "/" + token + ".txt";
    }
    if (key === "vbv.server-profiles.v1") return "Server Profiles/profiles.json";
    return "Settings/" + encodeURIComponent(key) + ".json";
  }
  function queueWrite(path, content, backup) {
    pending.set(path, { content, backup });
    if (pending.get(path).timer) clearTimeout(pending.get(path).timer);
    const item = pending.get(path);
    item.timer = setTimeout(async () => {
      const current = pending.get(path);
      if (!current) return;
      pending.delete(path);
      try { await writeFile(path, current.content, current.backup); writeError = ""; }
      catch (error) { writeError = String(error && error.message || error); console.error("[VBV Storage] write failed", error); }
      updateStatus();
    }, 350);
  }
  async function loadWorldMaps() {
    if (!root) return;
    const folder = await dir("WorldMap", true);
    async function visit(current) {
      for await (const [name, handle] of current.entries()) {
        if (handle.kind === "directory") {
          await visit(handle);
        } else if (handle.kind === "file" && name.endsWith(".txt")) {
          const key = "vbv.worldmap.v1." + name.slice(0, -4);
          try { cache.set(key, await (await handle.getFile()).text()); } catch (_) {}
        }
      }
    }
    await visit(folder);
    revision++;
  }
  async function loadProfiles() {
    try {
      const raw = await readFile("Server Profiles/profiles.json");
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) cache.set("vbv.server-profiles.v1", JSON.stringify(parsed));
    } catch (_) {}
  }
  async function ensureLayout() {
    for (const path of LAYOUT) await dir(path, true);
    const manifest = { format: 1, client: "Eaglercraft 26.2 VBV", created: new Date().toISOString() };
    try { await writeFile("manifest.json", JSON.stringify(manifest, null, 2), false); } catch (_) {}
  }
  async function connectWithHandle(handle) {
    if (!await permission(handle, true)) throw new Error("Folder permission was not granted.");
    root = handle;
    rootName = handle.name || "Eaglercraft Storage";
    await handleSet("root", handle);
    await ensureLayout();
    await loadWorldMaps();
    await loadProfiles();
    revision++;
    updateStatus();
    render();
    return true;
  }
  async function chooseFolder() {
    if (!("showDirectoryPicker" in window)) throw new Error("This browser does not support folder access. IndexedDB fallback remains available.");
    const handle = await window.showDirectoryPicker({ mode: "readwrite", id: "eaglercraft-storage" });
    await connectWithHandle(handle);
  }
  async function reconnect(ask) {
    const handle = await handleGet("root");
    if (!handle) throw new Error("No previous storage folder is registered. Choose Import Existing Storage.");
    if (!await permission(handle, !!ask)) throw new Error("Folder permission is required. Select Reconnect and grant access.");
    root = handle;
    rootName = handle.name || "Eaglercraft Storage";
    await ensureLayout();
    await loadWorldMaps();
    await loadProfiles();
    revision++;
    updateStatus();
    render();
    return true;
  }
  function getTextSync(key) {
    if (cache.has(key)) return cache.get(key);
    try { return localStorage.getItem(key) || ""; } catch (_) { return ""; }
  }
  function setTextSync(key, value) {
    value = String(value);
    cache.set(key, value);
    if (root) {
      queueWrite(pathForKey(key), value, key.startsWith("vbv.worldmap.v1."));
      return true;
    }
    try { localStorage.setItem(key, value); return true; } catch (_) { return false; }
  }
  function profileList() {
    try { return JSON.parse(getTextSync("vbv.server-profiles.v1") || "[]"); } catch (_) { return []; }
  }
  function saveProfiles(list) {
    setTextSync("vbv.server-profiles.v1", JSON.stringify(list, null, 2));
    if (activeAddress) {
      activeProfile = profileList().find(p => normalizedAddress(p.address) === activeAddress) || null;
      window.VBVActiveServerProfile = activeProfile;
      window.dispatchEvent(new CustomEvent("vbv:server-profile-changed", {
        detail: { address: activeAddress, profile: activeProfile }
      }));
    }
  }
  function serverSlug(value) {
    return normalizedAddress(value).replace(/[^a-z0-9._-]+/g, "_").replace(/^_+|_+$/g, "") || "unassigned";
  }
  function normalizedAddress(value) {
    return String(value || "").trim().toLowerCase().replace(/\/+$/, "");
  }
  function matchServer(address) {
    const nextAddress = normalizedAddress(address);
    if (nextAddress === activeAddress) return activeProfile;
    activeAddress = nextAddress;
    const profiles = profileList();
    activeProfile = profiles.find(p => normalizedAddress(p.address) === activeAddress) || null;
    window.VBVActiveServerProfile = activeProfile;
    window.dispatchEvent(new CustomEvent("vbv:server-profile-changed", {
      detail: { address: activeAddress, profile: activeProfile }
    }));
    updateStatus();
    return activeProfile;
  }
  async function folderBytes(handle) {
    let total = 0;
    for await (const [, child] of handle.entries()) {
      if (child.kind === "file") { try { total += (await child.getFile()).size; } catch (_) {} }
      else total += await folderBytes(child);
    }
    return total;
  }
  function humanBytes(value) {
    if (!Number.isFinite(value)) return "Unknown";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let n = value, i = 0;
    while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
    return (i ? n.toFixed(1) : String(n)) + " " + units[i];
  }
  async function stats() {
    let managed = null;
    if (root) { try { managed = await folderBytes(root); } catch (_) {} }
    let quota = null;
    try { if (navigator.storage && navigator.storage.estimate) quota = await navigator.storage.estimate(); } catch (_) {}
    return { managed, quota };
  }
  async function listFiles(path) {
    const folder = await dir(path, true);
    const out = [];
    for await (const [name, handle] of folder.entries()) {
      if (handle.kind === "file") {
        const file = await handle.getFile();
        out.push({ name, path, size: file.size, modified: file.lastModified });
      }
    }
    return out.sort((a,b) => a.name.localeCompare(b.name));
  }
  async function listTreeFiles(path) {
    const folder = await dir(path, true);
    const out = [];
    async function visit(current, currentPath) {
      for await (const [name, handle] of current.entries()) {
        if (handle.kind === "directory") await visit(handle, currentPath + "/" + name);
        else {
          const file = await handle.getFile();
          out.push({ name, path: currentPath, size: file.size, modified: file.lastModified });
        }
      }
    }
    await visit(folder, path);
    return out.sort((a,b) => a.path.localeCompare(b.path) || a.name.localeCompare(b.name));
  }
  async function uploadFiles(input, path) {
    const files = Array.from(input.files || []);
    for (const file of files) {
      const folder = await dir(path, true);
      const handle = await folder.getFileHandle(file.name, { create: true });
      const writer = await handle.createWritable();
      await writer.write(file);
      await writer.close();
    }
    input.value = "";
    render();
  }
  async function deleteFile(path, name) {
    const folder = await dir(path, false);
    await folder.removeEntry(name);
    render();
  }
  async function clearFolder(path) {
    const folder = await dir(path, false);
    for await (const [name] of folder.entries()) await folder.removeEntry(name, { recursive: true });
    render();
  }

  let panel;
  function element(tag, text, cls) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (cls) node.className = cls;
    return node;
  }
  function action(label, fn, cls) {
    const b = element("button", label, cls || "vbvs-btn");
    b.type = "button"; b.addEventListener("click", async () => {
      b.disabled = true;
      try { await fn(); } catch (e) { alert(e && e.message || String(e)); }
      finally { b.disabled = false; }
    });
    return b;
  }
  function input(label, value, type) {
    const wrap = element("label", label, "vbvs-field");
    const field = document.createElement("input");
    field.type = type || "text"; field.value = value || "";
    wrap.appendChild(field); return { wrap, field };
  }
  function select(label, value, options) {
    const wrap = element("label", label, "vbvs-field");
    const field = document.createElement("select");
    for (const [v, name] of options) { const o = document.createElement("option"); o.value = v; o.textContent = name; field.appendChild(o); }
    field.value = value;
    wrap.appendChild(field); return { wrap, field };
  }
  function section(title) {
    const box = element("section", null, "vbvs-section");
    box.appendChild(element("h3", title));
    return box;
  }
  function openUI() {
    if (!panel) {
      const style = element("style");
      style.textContent = ".vbvs-shade{position:fixed;inset:0;background:#000b;z-index:2147483600;display:flex;align-items:center;justify-content:center;font:14px Arial,sans-serif;color:#eee}.vbvs-panel{width:min(760px,calc(100vw - 24px));max-height:88vh;overflow:auto;background:#17191d;border:1px solid #59616d;border-radius:9px;box-shadow:0 16px 60px #000b;padding:18px}.vbvs-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.vbvs-head h2{margin:0}.vbvs-tabs{display:flex;gap:6px;flex-wrap:wrap;margin:14px 0}.vbvs-btn,.vbvs-tabs button{background:#343b46;color:#fff;border:1px solid #697383;border-radius:5px;padding:8px 11px;cursor:pointer}.vbvs-btn.primary{background:#285a91}.vbvs-btn.danger{background:#762d32}.vbvs-btn:disabled{opacity:.5}.vbvs-section{background:#22262c;border:1px solid #363d47;border-radius:7px;padding:12px;margin:10px 0}.vbvs-section h3{margin:0 0 10px;font-size:16px}.vbvs-muted{color:#aeb8c5;font-size:12px}.vbvs-row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:8px 0}.vbvs-field{display:flex;flex-direction:column;gap:5px;min-width:170px;flex:1}.vbvs-field input,.vbvs-field select{box-sizing:border-box;width:100%;background:#111419;color:#fff;border:1px solid #59616d;border-radius:4px;padding:8px}.vbvs-item{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:8px;background:#303640;border-radius:5px;margin:6px 0}.vbvs-item small{color:#aeb8c5}.vbvs-warning{color:#ffd27a}.vbvs-check{display:flex;gap:8px;align-items:center;margin:7px 0}.vbvs-foot{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}";
      document.head.appendChild(style);
      panel = element("div", null, "vbvs-shade");
      panel.style.display = "none";
      const box = element("div", null, "vbvs-panel");
      panel.appendChild(box);
      document.body.appendChild(panel);
      panel.addEventListener("click", e => { if (e.target === panel) panel.style.display = "none"; });
      panel.box = box;
    }
    panel.style.display = "flex";
    render();
  }
  async function render() {
    if (!panel || panel.style.display === "none") return;
    const box = panel.box;
    box.replaceChildren();
    const head = element("div", null, "vbvs-head");
    head.append(element("h2", "Eaglercraft Storage"));
    head.append(action("Close", () => { panel.style.display = "none"; }));
    box.appendChild(head);
    const tabs = element("div", null, "vbvs-tabs");
    let page = panel.dataset.page || "overview";
    for (const [id, name] of [["overview","Overview"],["servers","Server Profiles"],["files","Files & Backups"]]) {
      const b = action(name, () => { panel.dataset.page = id; render(); });
      if (page === id) b.classList.add("primary");
      tabs.appendChild(b);
    }
    box.appendChild(tabs);
    const state = section("Storage connection");
    state.appendChild(element("div", root ? "Connected folder: " + rootName : "No computer folder connected", root ? "" : "vbvs-warning"));
    state.appendChild(element("div", root ? "World maps and profile data will be written to this folder." : "The client is currently using browser storage as a fallback.", "vbvs-muted"));
    const buttons = element("div", null, "vbvs-row");
    buttons.append(action("Import Existing Storage", chooseFolder, "vbvs-btn primary"));
    buttons.append(action("Choose New Storage Folder", chooseFolder));
    buttons.append(action("Reconnect Existing Folder", () => reconnect(true)));
    state.appendChild(buttons);
    if (writeError) state.appendChild(element("div", "Last filesystem write failed: " + writeError, "vbvs-warning"));
    box.appendChild(state);

    if (page === "overview") {
      const s = section("Storage usage");
      const usage = await stats();
      s.appendChild(element("div", "Files in Eaglercraft folder: " + (usage.managed == null ? "Not connected" : humanBytes(usage.managed))));
      if (usage.quota) s.appendChild(element("div", "Browser-managed storage estimate: " + humanBytes(usage.quota.usage) + " / " + humanBytes(usage.quota.quota), "vbvs-muted"));
      s.appendChild(element("div", "The browser does not expose exact free disk space for a selected folder. The client can measure its own files and detect failed writes.", "vbvs-muted"));
      box.appendChild(s);
      const f = section("Managed folders");
      f.appendChild(element("div", "Worlds · WorldMap · Resource Packs · Datapacks · Server Profiles · Backups · Cache"));
      f.appendChild(element("div", "World maps are already connected to this storage bridge. World save files still use Eaglercraft's existing save backend until its filesystem adapter is ported.", "vbvs-muted"));
      f.appendChild(element("div", "Server profiles can be created and stored here. Automatic resource-pack and graphics-setting application requires the corresponding runtime hooks.", "vbvs-muted"));
      box.appendChild(f);
    } else if (page === "servers") {
      const list = section("Server profiles");
      const profiles = profileList();
      if (!profiles.length) list.appendChild(element("div", "No profiles yet. Create one for a PvP, SMP or minigame server.", "vbvs-muted"));
      for (const p of profiles) {
        const row = element("div", null, "vbvs-item");
        const desc = element("div");
        desc.append(element("strong", p.name || p.address), element("br"));
        desc.append(element("small", p.address + " · " + ((p.resourcePacks || []).length) + " selected packs"));
        row.appendChild(desc);
        row.append(action("Edit", () => editProfile(p.id)));
        row.append(action("Delete", () => {
          if (!confirm("Delete profile " + (p.name || p.address) + "? Resource pack files will not be deleted.")) return;
          saveProfiles(profileList().filter(x => x.id !== p.id)); render();
        }, "vbvs-btn danger"));
        list.appendChild(row);
      }
      list.appendChild(action("+ Add server profile", () => editProfile(null), "vbvs-btn primary"));
      if (activeProfile) list.appendChild(element("div", "Active profile: " + activeProfile.name, "vbvs-muted"));
      box.appendChild(list);
    } else {
      const maps = section("World maps");
      const mapFiles = await listTreeFiles("WorldMap").catch(() => []);
      maps.appendChild(element("div", mapFiles.length + " saved map file(s). Deleting a map removes only its explored map data, not the Minecraft world.", "vbvs-muted"));
      mapFiles.forEach(f => maps.appendChild(fileRow("WorldMap", f)));
      box.appendChild(maps);
      const files = section("Resource packs");
      const global = await listFiles("Resource Packs/Global").catch(() => []);
      files.appendChild(element("div", "Global packs (" + global.length + ")"));
      global.forEach(f => files.appendChild(fileRow("Resource Packs/Global", f)));
      const upload = document.createElement("input"); upload.type = "file"; upload.accept = ".zip,application/zip"; upload.multiple = true;
      upload.addEventListener("change", () => uploadFiles(upload, "Resource Packs/Global").catch(e => alert(e.message)));
      files.appendChild(upload); box.appendChild(files);
      const dp = section("Datapacks");
      const datapacks = await listFiles("Datapacks/Global").catch(() => []);
      datapacks.forEach(f => dp.appendChild(fileRow("Datapacks/Global", f)));
      const dpUpload = document.createElement("input"); dpUpload.type = "file"; dpUpload.accept = ".zip"; dpUpload.multiple = true;
      dpUpload.addEventListener("change", () => uploadFiles(dpUpload, "Datapacks/Global").catch(e => alert(e.message)));
      dp.appendChild(dpUpload); box.appendChild(dp);
      const backup = section("Backups and cleanup");
      const backups = await listFiles("Backups/WorldMap").catch(() => []);
      backup.appendChild(element("div", backups.length + " world-map backup file(s)"));
      backups.forEach(f => backup.appendChild(fileRow("Backups/WorldMap", f)));
      backup.appendChild(action("Clear temporary cache", async () => {
        if (confirm("Delete files in Cache? Worlds, maps, resource packs and profiles will be kept.")) await clearFolder("Cache");
      }, "vbvs-btn danger"));
      box.appendChild(backup);
    }
    const foot = element("div", null, "vbvs-foot");
    foot.append(action("Close", () => { panel.style.display = "none"; }));
    box.appendChild(foot);
  }
  function fileRow(path, file) {
    const row = element("div", null, "vbvs-item");
    const desc = element("div");
    desc.append(element("strong", file.name), element("br"), element("small", humanBytes(file.size)));
    row.appendChild(desc);
    const targetPath = file.path || path;
    row.append(action("Delete", async () => {
      if (confirm("Delete " + file.name + "?")) await deleteFile(targetPath, file.name);
    }, "vbvs-btn danger"));
    return row;
  }
  async function editProfile(id) {
    const existing = profileList().find(p => p.id === id) || null;
    const name = input("Profile name", existing && existing.name);
    const address = input("Server address (hostname:port)", existing && existing.address);
    const renderDistance = select("Render distance", String(existing?.settings?.renderDistance || 8), [["4","4 chunks"],["5","5 chunks"],["6","6 chunks"],["8","8 chunks"],["12","12 chunks"],["16","16 chunks"],["24","24 chunks"]]);
    const graphics = select("Graphics preference", existing?.settings?.graphics || "fast", [["fast","Fast"],["fancy","Fancy"],["auto","Automatic"]]);
    const particles = select("Particles", existing?.settings?.particles || "all", [["minimal","Minimal"],["decreased","Decreased"],["all","All"]]);
    const clouds = select("Clouds", existing?.settings?.clouds || "default", [["off","Off"],["fast","Fast"],["fancy","Fancy"],["default","Default"]]);
    const fps = select("FPS preference", existing?.settings?.fps || "unlimited", [["unlimited","Maximum / unlimited"],["60","60 FPS"],["120","120 FPS"],["30","30 FPS"]]);
    const packs = await listFiles("Resource Packs/Global").catch(() => []);
    const selected = new Set(existing?.resourcePacks || []);
    const selectedServerPacks = new Set(existing?.serverResourcePacks || []);
    const selectedDatapacks = new Set(existing?.datapacks || []);
    const selectedServerDatapacks = new Set(existing?.serverDatapacks || []);
    const shade = element("div", null, "vbvs-shade");
    const form = element("div", null, "vbvs-panel");
    form.append(element("h2", existing ? "Edit Server Profile" : "Add Server Profile"));
    [name.wrap,address.wrap,renderDistance.wrap,graphics.wrap,particles.wrap,clouds.wrap,fps.wrap].forEach(x=>form.appendChild(x));
    const packBox = section("Global resource packs to use");
    packs.forEach(p => {
      const label = element("label", null, "vbvs-check");
      const cb = document.createElement("input"); cb.type = "checkbox"; cb.checked = selected.has(p.name);
      cb.addEventListener("change", () => cb.checked ? selected.add(p.name) : selected.delete(p.name));
      label.append(cb, document.createTextNode(p.name)); packBox.appendChild(label);
    });
    if (!packs.length) packBox.appendChild(element("div","Add ZIP files under Files & Backups first.","vbvs-muted"));
    form.appendChild(packBox);

    const serverPackBox = section("Resource packs for this server only");
    const serverPackInput = document.createElement("input"); serverPackInput.type = "file"; serverPackInput.accept = ".zip"; serverPackInput.multiple = true;
    serverPackBox.appendChild(serverPackInput);
    const serverPackList = element("div");
    serverPackBox.appendChild(serverPackList);
    const serverDatapackBox = section("Datapacks for this server");
    const serverDatapackInput = document.createElement("input"); serverDatapackInput.type = "file"; serverDatapackInput.accept = ".zip"; serverDatapackInput.multiple = true;
    serverDatapackBox.appendChild(serverDatapackInput);
    const serverDatapackList = element("div");
    serverDatapackBox.appendChild(serverDatapackList);
    const globalDatapackBox = section("Global datapacks");
    const globalDatapacks = await listFiles("Datapacks/Global").catch(() => []);
    globalDatapacks.forEach(p => {
      const label = element("label", null, "vbvs-check");
      const cb = document.createElement("input"); cb.type = "checkbox"; cb.checked = selectedDatapacks.has(p.name);
      cb.addEventListener("change", () => cb.checked ? selectedDatapacks.add(p.name) : selectedDatapacks.delete(p.name));
      label.append(cb, document.createTextNode(p.name)); globalDatapackBox.appendChild(label);
    });
    form.append(serverPackBox, serverDatapackBox, globalDatapackBox);

    async function refreshServerFiles() {
      const slug = serverSlug(address.field.value);
      const packPath = "Resource Packs/Servers/" + slug;
      const datapackPath = "Datapacks/Servers/" + slug;
      serverPackList.replaceChildren();
      serverDatapackList.replaceChildren();
      const serverPacks = await listFiles(packPath).catch(() => []);
      serverPacks.forEach(p => {
        const label = element("label", null, "vbvs-check");
        const cb = document.createElement("input"); cb.type = "checkbox"; cb.checked = selectedServerPacks.has(p.name);
        cb.addEventListener("change", () => cb.checked ? selectedServerPacks.add(p.name) : selectedServerPacks.delete(p.name));
        label.append(cb, document.createTextNode(p.name)); serverPackList.appendChild(label);
      });
      const serverDatapacks = await listFiles(datapackPath).catch(() => []);
      serverDatapacks.forEach(p => {
        const label = element("label", null, "vbvs-check");
        const cb = document.createElement("input"); cb.type = "checkbox"; cb.checked = selectedServerDatapacks.has(p.name);
        cb.addEventListener("change", () => cb.checked ? selectedServerDatapacks.add(p.name) : selectedServerDatapacks.delete(p.name));
        label.append(cb, document.createTextNode(p.name)); serverDatapackList.appendChild(label);
      });
    }
    serverPackInput.addEventListener("change", async () => {
      await uploadFiles(serverPackInput, "Resource Packs/Servers/" + serverSlug(address.field.value));
      await refreshServerFiles();
    });
    serverDatapackInput.addEventListener("change", async () => {
      await uploadFiles(serverDatapackInput, "Datapacks/Servers/" + serverSlug(address.field.value));
      await refreshServerFiles();
    });
    address.field.addEventListener("change", refreshServerFiles);
    await refreshServerFiles();

    const foot = element("div",null,"vbvs-foot");
    foot.append(action("Cancel",()=>shade.remove()));
    foot.append(action("Save profile",()=>{
      const addr = normalizedAddress(address.field.value);
      if (!addr) { alert("Enter a server address."); return; }
      const list = profileList();
      const profile = {
        id: existing?.id || ("server-" + Date.now().toString(36)),
        name: name.field.value.trim() || addr,
        address: addr,
        resourcePacks: Array.from(selected),
        serverResourcePacks: Array.from(selectedServerPacks),
        datapacks: Array.from(selectedDatapacks),
        serverDatapacks: Array.from(selectedServerDatapacks),
        settings: {
          renderDistance: Number(renderDistance.field.value),
          graphics: graphics.field.value, particles: particles.field.value,
          clouds: clouds.field.value, fps: fps.field.value
        }
      };
      const index = list.findIndex(p => p.id === profile.id);
      if (index >= 0) list[index] = profile; else list.push(profile);
      saveProfiles(list); shade.remove(); render();
    },"vbvs-btn primary"));
    form.appendChild(foot); shade.appendChild(form); document.body.appendChild(shade);
    shade.addEventListener("click",e=>{if(e.target===shade)shade.remove();});
  }
  function updateStatus() {
    const status = { connected: !!root, folder: rootName, revision, activeAddress, activeProfile: activeProfile?.name || "", writeError };
    window.VBVStorageStatus = status;
    window.dispatchEvent(new CustomEvent("vbv:storage-status", { detail: status }));
  }

  window.VBVStorage = {
    version: 1,
    openUI,
    chooseFolder,
    reconnect: () => reconnect(true),
    getTextSync,
    setTextSync,
    revision: () => revision,
    refresh: () => { revision++; updateStatus(); },
    updateServer: matchServer,
    status: () => ({ connected: !!root, folder: rootName, revision, activeProfile, writeError }),
    async initialize() {
      try {
        const handle = await handleGet("root");
        if (handle && await permission(handle, false)) {
          root = handle; rootName = handle.name || "Eaglercraft Storage";
          await ensureLayout(); await loadWorldMaps(); await loadProfiles(); revision++;
        }
      } catch (e) { console.warn("[VBV Storage] reconnect skipped:", e); }
      updateStatus();
    }
  };

  function addLauncher() {
    if (!document.body || document.getElementById("vbv-storage-launcher")) return;
    const button = element("button", "Storage");
    button.id = "vbv-storage-launcher";
    button.title = "Eaglercraft Storage Manager";
    button.style.cssText = "position:fixed;right:10px;bottom:10px;z-index:2147483500;background:#263b55;color:#fff;border:1px solid #8095b0;border-radius:5px;padding:7px 10px;font:12px Arial;opacity:.82";
    button.addEventListener("click", openUI);
    document.body.appendChild(button);
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", () => { addLauncher(); window.VBVStorage.initialize(); });
  else { addLauncher(); window.VBVStorage.initialize(); }
})();