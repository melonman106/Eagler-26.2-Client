package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Browser-safe native Litematica manager. */
public final class VBVLitematicaScreen extends Screen {
    private final Screen parent;
    public VBVLitematicaScreen(Screen parent) { super(Component.literal("Litematica")); this.parent = parent; }
    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("IMPORT .LITEMATIC"), b -> VBVStorage.importLitematic()).bounds(width / 2 - 110, 96, 220, 24).build());
        addRenderableWidget(Button.builder(Component.literal("STORAGE"), b -> VBVNav.open(new VBVStorageScreen(this))).bounds(width / 2 - 110, 126, 105, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> VBVNav.open(parent)).bounds(width / 2 + 5, 126, 105, 22).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF101010); super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font,"LITEMATICA",width/2-font.width("LITEMATICA")/2,34,0xFFFFFFFF,true);
        graphics.text(font,"Native Eagler schematic importer",width/2-font.width("Native Eagler schematic importer")/2,54,0xFFAAAAAA,false);
        String status=lastImportStatus(); graphics.text(font,status,width/2-font.width(status)/2,168,0xFF55FF55,false);
        graphics.text(font,"Imported schematics are kept in Cache/Litematica/",width/2-font.width("Imported schematics are kept in Cache/Litematica/")/2,188,0xFFAAAAAA,false);
    }
    @JSBody(script="try { var x=window.VBVLitematicaSummary; if(x){ if(x.error) return 'Import error: '+x.error; var s='Loaded '+x.name+' | '+x.width+'x'+x.height+'x'+x.length+' | regions '+x.regions+' | palette '+x.palette; if(x.author) s+=' | '+x.author; return s; } var y=window.VBVStorageLastImport; if(y&&y.kind==='litematic') return 'Imported: '+y.name+' ('+y.size+' bytes)'; } catch(e){} return 'No .litematic imported this session.';")
    private static native String lastImportStatus();
    @Override public void onClose(){VBVNav.open(parent);}
}
