package com.melonman106.vbvclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native settings launcher; Minecraft's real OptionsScreen owns the settings. */
public final class VBVSettingsScreen extends Screen {
    private final Screen parent;
    public VBVSettingsScreen(Screen parent) { super(Component.literal("Options")); this.parent = parent; }
    @Override protected void init() {
        super.init();
        int w=Math.min(380,width-40),x=width/2-w/2;
        addRenderableWidget(Button.builder(Component.literal("OPEN MINECRAFT SETTINGS"),b->VBVNav.openOptions(this)).bounds(x,100,w,28).build());
        addRenderableWidget(Button.builder(Component.literal("CLIENT MOD SETTINGS"),b->VBVNav.open(new VBVModsScreen(this))).bounds(x,134,w,24).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->VBVNav.open(parent)).bounds(width/2-100,height-38,200,22).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){
        graphics.fill(0,0,width,height,0xFF101010);super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        graphics.text(font,"OPTIONS",28,28,0xFFFFFFFF,true);
        graphics.text(font,"Video, controls, audio, accessibility, language and other settings",28,48,0xFFAAAAAA,false);
        graphics.text(font,"are managed by Minecraft's native settings screen.",28,62,0xFF888888,false);
    }
    @Override public void onClose(){VBVNav.open(parent);}
}
