package com.melonman106.vbvclient;

import java.util.Locale;
import java.util.Calendar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Simple HUD Enhanced: configurable compact information overlay. */
public final class VBVSimpleHud {
    private static long lastTickTime=-1,lastWallTime=-1; private static double clientTps=20.0;
    private VBVSimpleHud(){}
    public static void render(GuiGraphicsExtractor graphics){
        if(!VBVSimpleHudConfig.enabled)return;
        if(VBVExtraFeatures.isBetterF3Enabled()&&VBVExtraFeatures.isDebugHudEnabled())return;
        Minecraft mc=Minecraft.getInstance(); Player p=mc.player; if(p==null||mc.level==null)return;
        long gameTick=mc.level.getGameTime(), dayTime=mc.level.getOverworldClockTime(), now=System.currentTimeMillis();
        if(lastTickTime>=0){long dt=gameTick-lastTickTime, dw=now-lastWallTime;if(dw>0&&dt>=0)clientTps=Math.max(0,Math.min(20.0,dt*1000.0/dw));}
        lastTickTime=gameTick;lastWallTime=now;
        int x=6+VBVSimpleHudConfig.hudXOffset,y=6+VBVSimpleHudConfig.hudYOffset,line=Math.max(8,Math.round(11*VBVSimpleHudConfig.hudScale));
        int count=lineCount(); graphics.fill(x-3,y-3,x+Math.max(205,Math.round(230*VBVSimpleHudConfig.hudScale)),y+count*line+2,0x66000000);
        if(VBVSimpleHudConfig.fps)y=text(graphics,"FPS: "+mc.getFps(),x,y,line);
        if(VBVSimpleHudConfig.coordinates)y=text(graphics,"XYZ: "+p.blockPosition().getX()+" "+p.blockPosition().getY()+" "+p.blockPosition().getZ(),x,y,line);
        if(VBVSimpleHudConfig.direction)y=text(graphics,"Facing: "+p.getDirection().getName(),x,y,line);
        if(VBVSimpleHudConfig.chunkCoordinates)y=text(graphics,"Chunk: "+(p.blockPosition().getX()>>4)+" "+(p.blockPosition().getZ()>>4),x,y,line);
        if(VBVSimpleHudConfig.subChunkCoordinates)y=text(graphics,"Sub: "+(p.blockPosition().getX()&15)+" "+(p.blockPosition().getY()&15)+" "+(p.blockPosition().getZ()&15),x,y,line);
        if(VBVSimpleHudConfig.velocity){double h=p.getDeltaMovement().horizontalDistance()*20.0;double v=p.getDeltaMovement().y*20.0;y=text(graphics,String.format(Locale.ROOT,"Velocity: H %.2f / V %.2f m/s",h,v),x,y,line);}
        if(VBVSimpleHudConfig.speed)y=text(graphics,String.format(Locale.ROOT,"Speed: %.2f m/s",p.getDeltaMovement().horizontalDistance()*20.0),x,y,line);
        if(VBVSimpleHudConfig.tps)y=text(graphics,String.format(Locale.ROOT,"Client TPS: %.1f",clientTps),x,y,line);
        if(VBVSimpleHudConfig.ping&&mc.getConnection()!=null&&mc.getConnection().getPlayerInfo(p.getUUID())!=null)y=text(graphics,"Ping: "+mc.getConnection().getPlayerInfo(p.getUUID()).getLatency()+" ms",x,y,line);
        if(VBVSimpleHudConfig.lightLevel)y=text(graphics,"Light: "+mc.level.getMaxLocalRawBrightness(p.blockPosition()),x,y,line);
        if(VBVSimpleHudConfig.biome){String b="unknown";try{b=mc.level.getBiome(p.blockPosition()).getRegisteredName();}catch(Exception ignored){}y=text(graphics,"Biome: "+b,x,y,line);}
        if(VBVSimpleHudConfig.gameTime)y=text(graphics,"Game time: "+formatTime(dayTime),x,y,line);
        if(VBVSimpleHudConfig.dayCounter)y=text(graphics,"Day: "+(dayTime/24000L),x,y,line);
        if(VBVSimpleHudConfig.systemTime)y=text(graphics,"System: "+formatSystemTime(),x,y,line);
        if(VBVSimpleHudConfig.netherConversion){boolean nether=mc.level.dimension()==Level.NETHER;double nx=p.getX()*(nether?8.0:0.125),nz=p.getZ()*(nether?8.0:0.125);y=text(graphics,String.format(Locale.ROOT,"Portal: %.0f %.0f",nx,nz),x,y,line);}
        if(VBVSimpleHudConfig.playerName)y=text(graphics,"Player: "+p.getName().getString(),x,y,line);
        if(VBVSimpleHudConfig.health)y=text(graphics,String.format(Locale.ROOT,"Health: %.1f / %.1f",p.getHealth(),p.getMaxHealth()),x,y,line);
        if(VBVSimpleHudConfig.hunger)y=text(graphics,"Food: "+p.getFoodData().getFoodLevel(),x,y,line);
        if(VBVSimpleHudConfig.movement){String state=p.isSprinting()?"Sprinting":p.isSwimming()?"Swimming":p.isCrouching()?"Sneaking":p.getAbilities().flying?"Flying":"Walking";y=text(graphics,"State: "+state,x,y,line);}
        if(VBVSimpleHudConfig.effectTimers){for(var effect:p.getActiveEffects()){int sec=Math.max(0,effect.getDuration()/20);y=text(graphics,effect.getEffect().value().getDisplayName().getString()+": "+sec+"s",x,y,line);}}
        else if(VBVSimpleHudConfig.effects)y=text(graphics,"Effects: "+p.getActiveEffects().size(),x,y,line);
    }
    private static int text(GuiGraphicsExtractor g,String s,int x,int y,int line){g.text(Minecraft.getInstance().font,s,x,y,0xFFFFFFFF,true);return y+line;}
    private static int lineCount(){int n=0;if(VBVSimpleHudConfig.fps)n++;if(VBVSimpleHudConfig.coordinates)n++;if(VBVSimpleHudConfig.direction)n++;if(VBVSimpleHudConfig.chunkCoordinates)n++;if(VBVSimpleHudConfig.subChunkCoordinates)n++;if(VBVSimpleHudConfig.velocity)n++;if(VBVSimpleHudConfig.speed)n++;if(VBVSimpleHudConfig.tps)n++;if(VBVSimpleHudConfig.ping)n++;if(VBVSimpleHudConfig.lightLevel)n++;if(VBVSimpleHudConfig.biome)n++;if(VBVSimpleHudConfig.gameTime)n++;if(VBVSimpleHudConfig.dayCounter)n++;if(VBVSimpleHudConfig.systemTime)n++;if(VBVSimpleHudConfig.netherConversion)n++;if(VBVSimpleHudConfig.playerName)n++;if(VBVSimpleHudConfig.health)n++;if(VBVSimpleHudConfig.hunger)n++;if(VBVSimpleHudConfig.movement)n++;if(VBVSimpleHudConfig.effectTimers)n+=Math.max(1,VBVSimpleHudConfig.enabled?4:1);return Math.max(1,n);}
    private static String formatTime(long ticks){long t=Math.floorMod(ticks,24000L);long mins=((t+6000L)*60L)/1000L;int h=(int)((mins/60L)%24L),m=(int)(mins%60L);return String.format(Locale.ROOT,"%02d:%02d",h,m);}
    private static String formatSystemTime(){Calendar c=Calendar.getInstance();int h=c.get(Calendar.HOUR);if(!VBVSimpleHudConfig.twelveHourTime)h=c.get(Calendar.HOUR_OF_DAY);return String.format(Locale.ROOT,"%02d:%02d:%02d%s",h,c.get(Calendar.MINUTE),c.get(Calendar.SECOND),VBVSimpleHudConfig.twelveHourTime?(c.get(Calendar.AM_PM)==Calendar.AM?" AM":" PM"):"");}
}
