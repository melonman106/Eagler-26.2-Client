package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native Minecraft-style storage area with tabbed sections. */
public final class VBVStorageScreen extends Screen {
    private final Screen parent; private int tab=0;
    private static final String[] TABS={"Overview","Worlds","Resource Packs","Datapacks","Profiles","Backups"};
    public VBVStorageScreen(Screen parent){super(Component.literal("Storage"));this.parent=parent;}
    @Override protected void init(){super.init();rebuild();}
    private void rebuild(){clearWidgets();int tabWidth=Math.min(112,Math.max(78,(width-36)/TABS.length));int total=tabWidth*TABS.length+5*(TABS.length-1);int left=width/2-total/2;for(int i=0;i<TABS.length;i++){final int s=i;addRenderableWidget(Button.builder(Component.literal((tab==i?"■ ":"")+TABS[i]),b->{tab=s;rebuild();}).bounds(left+i*(tabWidth+5),55,tabWidth,20).build());}
        int x=width/2-100;
        if(tab==0){addButton("Choose Storage Folder",x,112,b->VBVStorage.chooseFolder());addButton("Reconnect Existing Folder",x,137,b->VBVStorage.reconnect());}
        else if(tab==1){addButton("World Storage",x,112,b->show("Worlds","Worlds use Minecraft's native save system; the managed folder is reserved for imported/exported world data."));}
        else if(tab==2){addButton("Resource Pack Storage",x,112,b->show("Resource Packs","Use the managed Resource Packs/Global and Resource Packs/Servers folders."));}
        else if(tab==3){addButton("Datapack Storage",x,112,b->show("Datapacks","Use the managed Datapacks/Global and Datapacks/Servers folders."));}
        else if(tab==4){addButton("NEW SERVER PROFILE",x,112,b->VBVNav.open(new VBVStorageProfileScreen(this)));addButton("Refresh Profiles",x,137,b->rebuild());}
        else{addButton("World Map Backups",x,112,b->show("Backups","World-map and world backups remain separate from active saves."));addButton("Clear Cache",x,137,b->show("Cache","Cache cleanup is handled by the managed storage bridge."));}
        addRenderableWidget(Button.builder(Component.literal("Done"),b->VBVNav.open(parent)).bounds(width/2-100,height-34,200,20).build());}
    private void addButton(String label,int x,int y,Button.OnPress action){addRenderableWidget(Button.builder(Component.literal(label),action).bounds(x,y,200,20).build());}
    private void show(String title,String message){VBVNav.open(new VBVStorageInfoScreen(this,title,message));}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){graphics.fill(0,0,width,height,0xFF101010);super.extractRenderState(graphics,mouseX,mouseY,partialTick);graphics.text(font,"STORAGE",width/2-font.width("STORAGE")/2,28,0xFFFFFFFF,true);String status=VBVStorage.connected()?"Connected: "+VBVStorage.folderName():"No computer folder connected";graphics.text(font,status,width/2-font.width(status)/2,88,VBVStorage.connected()?0xFF55FF55:0xFFFFD27A,false);String title=TABS[tab].toUpperCase();graphics.text(font,title,width/2-font.width(title)/2,101,0xFFFFFFFF,true);}
    @Override public void onClose(){VBVNav.open(parent);}
}
