package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;
import net.minecraft.network.chat.Component;

/** VBV home screen with native Minecraft widgets and Dungeons-inspired layout. */
public final class VBVDungeonsTitleScreen extends TitleScreen {
    public VBVDungeonsTitleScreen() { super(); }

    @Override protected void init() {
        super.init();
        VBVClient.init();
        EaglerProfile.readIfNeeded();
        clearWidgets();
        int left = Math.max(24, width / 24);
        int mainWidth = 178;
        int smallWidth = 86;
        int buttonHeight = 18;
        int gap = 4;
        int rowGap = 4;
        int y = Math.max(64, height - 140);

        addNativeButton("PLAY", left, y, mainWidth, buttonHeight,
            b -> VBVNav.openMultiplayer(this));
        y += buttonHeight + rowGap;
        addNativeButton("SINGLEPLAYER", left, y, smallWidth, buttonHeight,
            b -> VBVNav.openSingleplayer(this));
        addNativeButton("PARTY", left + smallWidth + gap, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new VBVPartyScreen(this)));
        y += buttonHeight + rowGap;
        addNativeButton("MODS", left, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new VBVModsScreen(this)));
        addNativeButton("OPTIONS", left + smallWidth + gap, y, smallWidth, buttonHeight,
            b -> VBVNav.openOptions(this));
        y += buttonHeight + rowGap;
        addNativeButton("HERO", left, y, smallWidth, buttonHeight,
            b -> VBVNav.open(new net.lax1dude.eaglercraft.v1_8.profile.EaglerProfileScreen26(this)));

        addNativeButton("STORAGE", width - 108, height - 26, 84, 18,
            b -> VBVNav.open(new VBVStorageScreen(this)));
    }

    private void addNativeButton(String label,int x,int y,int w,int h,Button.OnPress action){addRenderableWidget(Button.builder(Component.literal(label),action).bounds(x,y,w,h).build());}

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float partialTick){
        super.extractRenderState(graphics,mouseX,mouseY,partialTick);
        EaglerProfile.readIfNeeded();
        String name = EaglerProfile.getName();
        if(name == null || name.isEmpty()) {
            name = Minecraft.getInstance().getUser() != null ? Minecraft.getInstance().getUser().getName() : "Player";
        }
        Identifier skin = EaglerProfile.getActiveSkinResourceLocation();
        int nameWidth = font.width(name);
        int textX = width - 12 - nameWidth;
        int headX = textX - 25;
        int headY = 10;
        // Draw the actual selected Eagler skin face and hat overlay, without a panel.
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 8, 8, 20, 20, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, headX, headY, 40, 8, 20, 20, 8, 8, 64, 64);
        graphics.text(font, name, textX, headY + 6, 0xFFFFFFFF, true);
        String[] party=VBVParty.players();
        int py=headY+26;
        for(int i=0;i<Math.min(4,party.length);i++){
            String member=party[i];
            graphics.fill(width-170,py,width-10,py+20,0x55000000);
            graphics.text(font,member,width-160,py+6,0xFFE8E8E8,false);
            py+=22;
        }
    }
}
