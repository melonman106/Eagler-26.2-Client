package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native server-profile editor backed by the VBV filesystem storage bridge. */
public final class VBVStorageProfileScreen extends Screen {
    private final Screen parent;
    private EditBox nameBox, addressBox, distanceBox;
    public VBVStorageProfileScreen(Screen parent){super(Component.literal("Server Profile"));this.parent=parent;}
    @Override protected void init(){
        super.init(); int x=width/2-130;
        nameBox=new EditBox(font,x,72,260,20,Component.literal("Profile name"));
        addressBox=new EditBox(font,x,101,260,20,Component.literal("Server address"));
        distanceBox=new EditBox(font,x,130,260,20,Component.literal("Render distance")); distanceBox.setValue("128");
        addRenderableWidget(nameBox);addRenderableWidget(addressBox);addRenderableWidget(distanceBox);
        addRenderableWidget(Button.builder(Component.literal("SAVE PROFILE"),b->save()).bounds(x,160,260,22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->VBVNav.open(parent)).bounds(x,height-34,260,20).build());
    }
    private void save(){int d=128;try{d=Integer.parseInt(distanceBox.getValue().trim());}catch(Exception ignored){}d=Math.max(2,Math.min(128,d));if(VBVStorage.saveProfile(nameBox.getValue(),addressBox.getValue(),d))VBVNav.open(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){graphics.fill(0,0,width,height,0xFF101010);super.extractRenderState(graphics,mouseX,mouseY,partialTick);String t="SERVER PROFILE";graphics.text(font,t,width/2-font.width(t)/2,32,0xFFFFFFFF,true);graphics.text(font,"Saved in Server Profiles on your selected storage folder.",width/2-150,52,0xFFAAAAAA,false);}
    @Override public void onClose(){VBVNav.open(parent);}
}
