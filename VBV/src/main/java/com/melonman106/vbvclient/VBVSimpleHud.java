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
    // The text is rebuilt ten times a second instead of every frame: String.format and the world
    // lookups (biome, light, ping) are slow in the browser build, and none of the values need 60 Hz.
    private static final java.util.ArrayList<String> LINES=new java.util.ArrayList<>(24);
    private static long builtAtMs=0L;
    private static boolean builtFor=false;
    public static void render(GuiGraphicsExtractor graphics){
        if(!VBVSimpleHudConfig.enabled)return;
        if(VBVExtraFeatures.isBetterF3Enabled()&&VBVExtraFeatures.isDebugHudEnabled())return;
        Minecraft mc=Minecraft.getInstance(); Player p=mc.player; if(p==null||mc.level==null)return;
        long now=System.currentTimeMillis();
        if(!builtFor||now-builtAtMs>=100L||now<builtAtMs){builtFor=true;builtAtMs=now;rebuild(mc,p,now);}
        int x=6+VBVSimpleHudConfig.hudXOffset,y=6+VBVSimpleHudConfig.hudYOffset,line=Math.max(8,Math.round(11*VBVSimpleHudConfig.hudScale));
        int count=Math.max(1,LINES.size()); graphics.fill(x-3,y-3,x+Math.max(205,Math.round(230*VBVSimpleHudConfig.hudScale)),y+count*line+2,0x66000000);
        for(int i=0;i<LINES.size();i++)y=text(graphics,LINES.get(i),x,y,line);
    }
    private static void rebuild(Minecraft mc,Player p,long now){
        LINES.clear();
        long gameTick=mc.level.getGameTime(), dayTime=mc.level.getOverworldClockTime();
        if(lastTickTime>=0){long dt=gameTick-lastTickTime, dw=now-lastWallTime;if(dw>0&&dt>=0)clientTps=Math.max(0,Math.min(20.0,dt*1000.0/dw));}
        lastTickTime=gameTick;lastWallTime=now;
        net.minecraft.core.BlockPos bp=p.blockPosition();
        if(VBVSimpleHudConfig.fps)LINES.add("FPS: "+mc.getFps());
        if(VBVSimpleHudConfig.coordinates)LINES.add("XYZ: "+bp.getX()+" "+bp.getY()+" "+bp.getZ());
        if(VBVSimpleHudConfig.direction)LINES.add("Facing: "+p.getDirection().getName());
        if(VBVSimpleHudConfig.chunkCoordinates)LINES.add("Chunk: "+(bp.getX()>>4)+" "+(bp.getZ()>>4));
        if(VBVSimpleHudConfig.subChunkCoordinates)LINES.add("Sub: "+(bp.getX()&15)+" "+(bp.getY()&15)+" "+(bp.getZ()&15));
        double h=p.getDeltaMovement().horizontalDistance()*20.0;
        if(VBVSimpleHudConfig.velocity)LINES.add(String.format(Locale.ROOT,"Velocity: H %.2f / V %.2f m/s",h,p.getDeltaMovement().y*20.0));
        if(VBVSimpleHudConfig.speed)LINES.add(String.format(Locale.ROOT,"Speed: %.2f m/s",h));
        if(VBVSimpleHudConfig.tps)LINES.add(String.format(Locale.ROOT,"Client TPS: %.1f",clientTps));
        if(VBVSimpleHudConfig.ping&&mc.getConnection()!=null&&mc.getConnection().getPlayerInfo(p.getUUID())!=null)LINES.add("Ping: "+mc.getConnection().getPlayerInfo(p.getUUID()).getLatency()+" ms");
        if(VBVSimpleHudConfig.lightLevel)LINES.add("Light: "+mc.level.getMaxLocalRawBrightness(bp));
        if(VBVSimpleHudConfig.biome){String b="unknown";try{b=mc.level.getBiome(bp).getRegisteredName();}catch(Exception ignored){}LINES.add("Biome: "+b);}
        if(VBVSimpleHudConfig.gameTime)LINES.add("Game time: "+formatTime(dayTime));
        if(VBVSimpleHudConfig.dayCounter)LINES.add("Day: "+(dayTime/24000L));
        if(VBVSimpleHudConfig.systemTime)LINES.add("System: "+formatSystemTime());
        if(VBVSimpleHudConfig.netherConversion){boolean nether=mc.level.dimension()==Level.NETHER;double nx=p.getX()*(nether?8.0:0.125),nz=p.getZ()*(nether?8.0:0.125);LINES.add(String.format(Locale.ROOT,"Portal: %.0f %.0f",nx,nz));}
        if(VBVSimpleHudConfig.playerName)LINES.add("Player: "+p.getName().getString());
        if(VBVSimpleHudConfig.health)LINES.add(String.format(Locale.ROOT,"Health: %.1f / %.1f",p.getHealth(),p.getMaxHealth()));
        if(VBVSimpleHudConfig.hunger)LINES.add("Food: "+p.getFoodData().getFoodLevel());
        if(VBVSimpleHudConfig.movement){String state=p.isSprinting()?"Sprinting":p.isSwimming()?"Swimming":p.isCrouching()?"Sneaking":p.getAbilities().flying?"Flying":"Walking";LINES.add("State: "+state);}
        if(VBVSimpleHudConfig.effectTimers){for(var effect:p.getActiveEffects()){int sec=Math.max(0,effect.getDuration()/20);LINES.add(effect.getEffect().value().getDisplayName().getString()+": "+sec+"s");}}
        else if(VBVSimpleHudConfig.effects)LINES.add("Effects: "+p.getActiveEffects().size());
    }
    private static int text(GuiGraphicsExtractor g,String s,int x,int y,int line){g.text(Minecraft.getInstance().font,s,x,y,0xFFFFFFFF,true);return y+line;}
    private static String formatTime(long ticks){long t=Math.floorMod(ticks,24000L);long mins=((t+6000L)*60L)/1000L;int h=(int)((mins/60L)%24L),m=(int)(mins%60L);return String.format(Locale.ROOT,"%02d:%02d",h,m);}
    private static String formatSystemTime(){Calendar c=Calendar.getInstance();int h=c.get(Calendar.HOUR);if(!VBVSimpleHudConfig.twelveHourTime)h=c.get(Calendar.HOUR_OF_DAY);return String.format(Locale.ROOT,"%02d:%02d:%02d%s",h,c.get(Calendar.MINUTE),c.get(Calendar.SECOND),VBVSimpleHudConfig.twelveHourTime?(c.get(Calendar.AM_PM)==Calendar.AM?" AM":" PM"):"");}
}
