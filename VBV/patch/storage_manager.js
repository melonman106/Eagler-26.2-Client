/* VBV_STORAGE_MANAGER_V1 */
(function () {
  "use strict";
  if (window.VBVStorage && window.VBVStorage.version) return;

  const DB_NAME = "vbv-storage-handles-v1";
  const STORE = "handles";
  const KV_STORE = "data";
  const LAYOUT = [
    "Worlds", "WorldMap", "Resource Packs", "Resource Packs/Global",
    "Resource Packs/Servers", "Datapacks", "Datapacks/Global",
    "Datapacks/Servers", "Server Profiles", "Backups", "Backups/WorldMap",
    "Backups/Worlds", "Cache", "Settings"
  ];
  const cache = new Map();
  const pending = new Map();
  let root = null, rootName = "", revision = 0, activeAddress = "", activeProfile = null, writeError = "", dbPromise;

  function openDB() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise((resolve, reject) => {
      if (!("indexedDB" in window)) return reject(new Error("IndexedDB is unavailable"));
      const request = indexedDB.open(DB_NAME, 2);
      request.onupgradeneeded = () => { const db = request.result; if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE); if (!db.objectStoreNames.contains(KV_STORE)) db.createObjectStore(KV_STORE); };
      request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error || new Error("Could not open storage metadata"));
    }); return dbPromise;
  }
  async function kvSet(key, value) { const db = await openDB(); return new Promise((resolve,reject)=>{const tx=db.transaction(KV_STORE,"readwrite");tx.objectStore(KV_STORE).put(String(value),String(key));tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);}); }
  async function kvLoadAll() { const db=await openDB(); return new Promise((resolve,reject)=>{const tx=db.transaction(KV_STORE,"readonly"),r=tx.objectStore(KV_STORE).getAll(),k=tx.objectStore(KV_STORE).getAllKeys();tx.oncomplete=()=>resolve((k.result||[]).map((x,i)=>[String(x),String(r.result[i])]));tx.onerror=()=>reject(tx.error);}); }
  async function handleGet(key) { const db=await openDB(); return new Promise((resolve,reject)=>{const r=db.transaction(STORE,"readonly").objectStore(STORE).get(key);r.onsuccess=()=>resolve(r.result||null);r.onerror=()=>reject(r.error);}); }
  async function handleSet(key,value) { const db=await openDB(); return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,"readwrite");tx.objectStore(STORE).put(value,key);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);}); }
  async function permission(handle,ask){if(!handle)return false;const opts={mode:"readwrite"};let s=await handle.queryPermission(opts);if(s!=="granted"&&ask)s=await handle.requestPermission(opts);return s==="granted";}
  async function dir(path,create){if(!root)throw new Error("Choose an Eaglercraft storage folder first.");let current=root;for(const part of path.split("/").filter(Boolean))current=await current.getDirectoryHandle(part,{create:!!create});return current;}
  async function readFile(path){const slash=path.lastIndexOf("/"),folder=await dir(slash<0?"":path.slice(0,slash),false),handle=await folder.getFileHandle(path.slice(slash+1));return await(await handle.getFile()).text();}
  async function writeFile(path,content,backup){const slash=path.lastIndexOf("/"),folder=await dir(slash<0?"":path.slice(0,slash),true),name=path.slice(slash+1);const handle=await folder.getFileHandle(name,{create:true});const writer=await handle.createWritable();await writer.write(content);await writer.close();}
  function queueWrite(path,content){pending.set(path,{content});if(pending.get(path).timer)clearTimeout(pending.get(path).timer);const item=pending.get(path);item.timer=setTimeout(async()=>{const current=pending.get(path);if(!current)return;pending.delete(path);try{await writeFile(path,current.content,false);writeError="";}catch(e){writeError=String(e&&e.message||e);}updateStatus();},350);}
  function pathForKey(key){if(key==="vbv.server-profiles.v1")return "Server Profiles/profiles.json";return "Settings/"+encodeURIComponent(key)+".json";}
  function getTextSync(key){return cache.has(key)?String(cache.get(key)):((()=>{try{return localStorage.getItem(key)||"";}catch(_){return "";}})());}
  function setTextSync(key,value){cache.set(key,String(value));revision++;try{localStorage.setItem(key,String(value));}catch(_){}kvSet(key,String(value)).catch(()=>{});if(root)queueWrite(pathForKey(key),String(value));updateStatus();return true;}
  function normalizedAddress(address){return String(address||"").trim().toLowerCase();}
  function profileList(){try{return JSON.parse(getTextSync("vbv.server-profiles.v1")||"[]");}catch(_){return [];}}
  function saveProfiles(list){setTextSync("vbv.server-profiles.v1",JSON.stringify(list,null,2));if(activeAddress)activeProfile=profileList().find(p=>normalizedAddress(p.address)===activeAddress)||null;}
  function saveProfile(name,address,renderDistance){const addr=normalizedAddress(address);if(!addr)return false;const list=profileList();let p=list.find(x=>normalizedAddress(x.address)===addr);if(!p)p={id:"server-"+Date.now().toString(36),resourcePacks:[],serverResourcePacks:[],datapacks:[],serverDatapacks:[],settings:{}};p.name=String(name||addr).trim()||addr;p.address=addr;p.settings=p.settings||{};p.settings.renderDistance=Number(renderDistance)||16;const i=list.findIndex(x=>x.id===p.id);if(i>=0)list[i]=p;else list.push(p);saveProfiles(list);return true;}
  function updateStatus(){const status={connected:!!root,folder:rootName,revision,activeAddress,activeProfile:activeProfile?.name||"",writeError};window.VBVStorageStatus=status;window.dispatchEvent(new CustomEvent("vbv:storage-status",{detail:status}));}
  function matchServer(address){activeAddress=normalizedAddress(address);activeProfile=profileList().find(p=>normalizedAddress(p.address)===activeAddress)||null;updateStatus();}
  async function ensureLayout(){for(const path of LAYOUT)await dir(path,true);}
  async function loadProfiles(){try{const raw=await readFile("Server Profiles/profiles.json"),parsed=JSON.parse(raw);if(Array.isArray(parsed))cache.set("vbv.server-profiles.v1",JSON.stringify(parsed));}catch(_){} }
  async function connectWithHandle(handle){if(!await permission(handle,true))throw new Error("Folder permission was not granted.");root=handle;rootName=handle.name||"Eaglercraft Storage";await handleSet("root",handle);await ensureLayout();await loadProfiles();revision++;updateStatus();return true;}
  async function chooseFolder(){try{return await connectWithHandle(await window.showDirectoryPicker({mode:"readwrite"}));}catch(e){if(e&&e.name!=="AbortError")alert("Could not choose storage folder: "+e.message);return false;}}
  async function reconnect(ask){try{const h=await handleGet("root");if(h&&await permission(h,!!ask)){root=h;rootName=h.name||"Eaglercraft Storage";await ensureLayout();await loadProfiles();revision++;updateStatus();return true;}}catch(e){console.warn("[VBV Storage] reconnect failed",e);}return false;}
  function openUI(){ if(window.VBVStorageNativeRequested){window.dispatchEvent(new CustomEvent("vbv:storage-native-open"));return;} }

  window.VBVStorage={version:2,openUI,chooseFolder,reconnect:(()=>reconnect(true)),getTextSync,setTextSync,revision:()=>revision,refresh:()=>{revision++;updateStatus();},updateServer:matchServer,saveProfile,status:()=>({connected:!!root,folder:rootName,revision,activeAddress,activeProfile,writeError}),async initialize(){try{const saved=await kvLoadAll();for(const [k,v] of saved)cache.set(k,v);}catch(_){}try{const h=await handleGet("root");if(h&&await permission(h,false)){root=h;rootName=h.name||"Eaglercraft Storage";await ensureLayout();await loadProfiles();revision++;}}catch(_){}updateStatus();}};
  if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",()=>window.VBVStorage.initialize());else window.VBVStorage.initialize();
})();
