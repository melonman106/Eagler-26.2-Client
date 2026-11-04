package com.melonman106.vbvclient;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VBVSimpleHudConfigScreen extends Screen {
    private final Screen parent;
    public VBVSimpleHudConfigScreen(Screen parent){super(Component.literal("Simple HUD Enhanced Config"));this.parent=parent;}
    @Override protected void init(){super.init();
        addToggle("Simple HUD",0,0,()->VBVSimpleHudConfig.enabled=!VBVSimpleHudConfig.enabled,()->VBVSimpleHudConfig.enabled);
        addToggle("FPS",1,0,()->VBVSimpleHudConfig.fps=!VBVSimpleHudConfig.fps,()->VBVSimpleHudConfig.fps);
        addToggle("Coordinates",2,0,()->VBVSimpleHudConfig.coordinates=!VBVSimpleHudConfig.coordinates,()->VBVSimpleHudConfig.coordinates);
        addToggle("Directions",3,0,()->VBVSimpleHudConfig.direction=!VBVSimpleHudConfig.direction,()->VBVSimpleHudConfig.direction);
        addToggle("Velocity",4,0,()->VBVSimpleHudConfig.velocity=!VBVSimpleHudConfig.velocity,()->VBVSimpleHudConfig.velocity);
        addToggle("Chunk coords",5,0,()->VBVSimpleHudConfig.chunkCoordinates=!VBVSimpleHudConfig.chunkCoordinates,()->VBVSimpleHudConfig.chunkCoordinates);
        addToggle("Sub-chunk coords",6,0,()->VBVSimpleHudConfig.subChunkCoordinates=!VBVSimpleHudConfig.subChunkCoordinates,()->VBVSimpleHudConfig.subChunkCoordinates);
        addToggle("Light level",0,1,()->VBVSimpleHudConfig.lightLevel=!VBVSimpleHudConfig.lightLevel,()->VBVSimpleHudConfig.lightLevel);
        addToggle("Ping",1,1,()->VBVSimpleHudConfig.ping=!VBVSimpleHudConfig.ping,()->VBVSimpleHudConfig.ping);
        addToggle("Biome",2,1,()->VBVSimpleHudConfig.biome=!VBVSimpleHudConfig.biome,()->VBVSimpleHudConfig.biome);
        addToggle("Client TPS",3,1,()->VBVSimpleHudConfig.tps=!VBVSimpleHudConfig.tps,()->VBVSimpleHudConfig.tps);
        addToggle("Game time",4,1,()->VBVSimpleHudConfig.gameTime=!VBVSimpleHudConfig.gameTime,()->VBVSimpleHudConfig.gameTime);
        addToggle("Day counter",5,1,()->VBVSimpleHudConfig.dayCounter=!VBVSimpleHudConfig.dayCounter,()->VBVSimpleHudConfig.dayCounter);
        addToggle("System time",6,1,()->VBVSimpleHudConfig.systemTime=!VBVSimpleHudConfig.systemTime,()->VBVSimpleHudConfig.systemTime);
        addToggle("Nether conversion",0,2,()->VBVSimpleHudConfig.netherConversion=!VBVSimpleHudConfig.netherConversion,()->VBVSimpleHudConfig.netherConversion);
        addToggle("Effect timers",1,2,()->VBVSimpleHudConfig.effectTimers=!VBVSimpleHudConfig.effectTimers,()->VBVSimpleHudConfig.effectTimers);
        addToggle("Movement state",2,2,()->VBVSimpleHudConfig.movement=!VBVSimpleHudConfig.movement,()->VBVSimpleHudConfig.movement);
        addToggle("12-hour clock",3,2,()->VBVSimpleHudConfig.twelveHourTime=!VBVSimpleHudConfig.twelveHourTime,()->VBVSimpleHudConfig.twelveHourTime);
        addRenderableWidget(Button.builder(Component.literal("HUD SIZE: "+Math.round(VBVSimpleHudConfig.hudScale*100)+"%"),b->{VBVSimpleHudConfig.hudScale=VBVSimpleHudConfig.hudScale>=1.5f?0.75f:VBVSimpleHudConfig.hudScale+0.25f;VBVConfigStore.saveAll();b.setMessage(Component.literal("HUD SIZE: "+Math.round(VBVSimpleHudConfig.hudScale*100)+"%"));}).bounds(width/2-100,height-82,200,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset defaults"),b->{VBVSimpleHudConfig.reset();VBVConfigStore.saveAll();VBVNav.open(new VBVSimpleHudConfigScreen(parent));}).bounds(width/2-100,height-56,95,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->VBVNav.open(parent)).bounds(width/2+5,height-56,95,20).build());
    }
    private void addToggle(String label,int row,int column,Runnable action,java.util.function.BooleanSupplier state){int columns=3;int w=180;int gap=8;int x=width/2-(columns*w+(columns-1)*gap)/2+column*(w+gap);addRenderableWidget(Button.builder(Component.literal(label+": "+(state.getAsBoolean()?"ON":"OFF")),b->{action.run();VBVConfigStore.saveAll();b.setMessage(Component.literal(label+": "+(state.getAsBoolean()?"ON":"OFF")));}).bounds(x,22+row*27,w,22).build());}
}
