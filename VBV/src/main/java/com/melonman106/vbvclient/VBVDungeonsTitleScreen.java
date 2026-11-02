package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** VBV home screen with native Minecraft widgets and Dungeons-inspired layout. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    public VBVDungeonsTitleScreen() { super(); }

    @Override protected void init() {
        super.init();
        clearWidgets();
        int left=Math.max(24,width/24), mainWidth=220, smallWidth=106, gap=6, y=Math.max(72,height-178);
        addNativeButton("PLAY",left,y,mainWidth,20,b->VBVNav.openMultiplayer(this)); y+=26;
        addNativeButton("SINGLEPLAYER",left,y,smallWidth,20,b->VBVNav.openSingleplayer(this));
        addNativeButton("PARTY",left+smallWidth+gap,y,smallWidth,20,b->VBVNav.open(new VBVPartyScreen(this))); y+=26;
        addNativeButton("MODS",left,y,smallWidth,20,b->VBVNav.open(new VBVModsScreen(this)));
        addNativeButton("OPTIONS",left+smallWidth+gap,y,smallWidth,20,b->VBVNav.open(new VBVSettingsScreen(this))); y+=26;
        addNativeButton("HERO",left,y,smallWidth,20,b->VBVNav.open(new VBVProfileScreen(this)));
        addNativeButton("STORAGE",width-136,height-30,112,20,b->VBVNav.open(new VBVStorageScreen(this)));
    }

    private void addNativeButton(String label,int x,int y,int w,int h,Button.OnPress action){addRenderableWidget(Button.builder(Component.literal(label),action).bounds(x,y,w,h).build());}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        String name=VBVProfile.name(); if(name==null||name.isEmpty()) name=Minecraft.getInstance().getUser()!=null?Minecraft.getInstance().getUser().getName():"Player";
        int panelX=width-Math.max(190,font.width(name)+78); int panelY=10;
        graphics.fill(panelX,panelY,width-10,panelY+34,0x99000000);
        // Compact player-head badge. The local skin renderer can replace this badge
        // later without changing the title-screen layout or party data model.
        graphics.fill(panelX+6,panelY+7,panelX+28,panelY+29,0xFFD9A878);
        graphics.fill(panelX+6,panelY+7,panelX+28,panelY+12,0xFF4A3424);
        graphics.fill(panelX+10,panelY+15,panelX+13,panelY+18,0xFF202020);
        graphics.fill(panelX+21,panelY+15,panelX+24,panelY+18,0xFF202020);
        graphics.text(font,name,panelX+36,panelY+9,0xFFFFFFFF,true);
        graphics.text(font,"HERO",panelX+36,panelY+21,0xFFAAAAAA,false);
        String[] party=VBVParty.players();
        int py=panelY+40;
        for(int i=0;i<Math.min(4,party.length);i++){
            String member=party[i]; graphics.fill(panelX,py,width-10,py+22,0x77000000);
            graphics.fill(panelX+5,py+4,panelX+19,py+18,0xFFB98B6B);
            graphics.text(font,member,panelX+25,py+7,0xFFE8E8E8,false); py+=24;
        }
    }
}
