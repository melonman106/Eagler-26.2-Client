package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/** Browser filesystem bridge with localStorage fallback. */
public final class VBVStorage {
    private VBVStorage() {}
    public static void chooseFolder() { chooseFolderJS(); }
    public static void reconnect() { reconnectJS(); }
    public static void updateServer(String address) { if(address!=null) updateServerJS(address); }
    public static String getText(String key) { return getTextJS(key); }
    public static boolean putText(String key,String value) { return putTextJS(key,value); }
    public static long revision() { return revisionJS(); }
    public static boolean writeFailed() { return writeFailedJS(); }
    public static boolean connected() { return connectedJS(); }
    public static String folderName() { return folderNameJS(); }
    public static boolean saveProfile(String name,String address,int renderDistance) { return saveProfileJS(name,address,renderDistance); }
    public static void importWorld() { importFileJS("world", ".zip,.mcworld", "Worlds"); }
    public static void importResourcePack() { importFileJS("resource-pack", ".zip,.jar", "Resource Packs/Global"); }
    public static void importDatapack() { importFileJS("datapack", ".zip", "Datapacks/Global"); }

    @JSBody(script="try { if(window.VBVStorage) window.VBVStorage.chooseFolder(); } catch(e){console.error(e);}") private static native void chooseFolderJS();
    @JSBody(script="try { if(window.VBVStorage) window.VBVStorage.reconnect(); } catch(e){console.error(e);}") private static native void reconnectJS();
    @JSBody(params={"address"},script="try { if(window.VBVStorage) window.VBVStorage.updateServer(address); } catch(e){ }") private static native void updateServerJS(String address);
    @JSBody(params={"key"},script="try { if(window.VBVStorage) return window.VBVStorage.getTextSync(key); return localStorage.getItem(key)||''; } catch(e){ return ''; }") private static native String getTextJS(String key);
    @JSBody(params={"key","value"},script="try { if(window.VBVStorage) return !!window.VBVStorage.setTextSync(key,value); localStorage.setItem(key,value); return true; } catch(e){ return false; }") private static native boolean putTextJS(String key,String value);
    @JSBody(script="try { return window.VBVStorage ? Number(window.VBVStorage.revision())||0 : 0; } catch(e){ return 0; }") private static native long revisionJS();
    @JSBody(script="try { return !!(window.VBVStorage&&window.VBVStorage.status().writeError); } catch(e){ return false; }") private static native boolean writeFailedJS();
    @JSBody(script="try { return !!(window.VBVStorage&&window.VBVStorage.status().connected); } catch(e){ return false; }") private static native boolean connectedJS();
    @JSBody(script="try { return window.VBVStorage ? String(window.VBVStorage.status().folder||'') : ''; } catch(e){ return ''; }") private static native String folderNameJS();
    @JSBody(params={"name","address","distance"},script="try { return !!(window.VBVStorage&&window.VBVStorage.saveProfile(name,address,distance)); } catch(e){ return false; }") private static native boolean saveProfileJS(String name,String address,int distance);
    @JSBody(params={"kind","accept","folder"},script="try { if(window.VBVStorage&&window.VBVStorage.importFile) window.VBVStorage.importFile(kind,accept,folder); } catch(e){ console.error(e); }") private static native void importFileJS(String kind,String accept,String folder);
}
