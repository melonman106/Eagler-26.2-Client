package com.melonman106.vbvclient;

public final class VBVSimpleHudConfig {
    public static boolean enabled=true,fps=true,coordinates=true,speed=true,lightLevel=false,gameTime=false,playerName=false,health=true,hunger=true,movement=true,effects=true,equipment=true;
    public static boolean direction=true,tps=true,ping=true,biome=true,dayCounter=true,systemTime=false,netherConversion=true,chunkCoordinates=true,subChunkCoordinates=true,effectTimers=true,velocity=true;
    public static boolean twelveHourTime=false;
    public static int armorWarningPercent=20,armorXOffset=0,armorYOffset=0,hudXOffset=0,hudYOffset=0;
    public static float hudScale=1.0f;

    private VBVSimpleHudConfig() {}

    public static void reset() {
        enabled=true;fps=true;coordinates=true;speed=true;lightLevel=false;gameTime=false;playerName=false;
        health=true;hunger=true;movement=true;effects=true;equipment=true;direction=true;tps=true;ping=true;
        biome=true;dayCounter=true;systemTime=false;netherConversion=true;chunkCoordinates=true;
        subChunkCoordinates=true;effectTimers=true;velocity=true;twelveHourTime=false;
        armorWarningPercent=20;armorXOffset=0;armorYOffset=0;hudXOffset=0;hudYOffset=0;hudScale=1.0f;
    }

    public static String serialize() {
        return enabled+","+fps+","+coordinates+","+speed+","+lightLevel+","+gameTime+","+playerName+","+health+","+hunger+","+movement+","+effects+","+equipment+","+direction+","+tps+","+ping+","+biome+","+dayCounter+","+systemTime+","+netherConversion+","+chunkCoordinates+","+subChunkCoordinates+","+effectTimers+","+velocity+","+twelveHourTime+","+armorWarningPercent+","+armorXOffset+","+armorYOffset+","+hudXOffset+","+hudYOffset+","+hudScale;
    }

    public static void load(String raw) {
        if (raw == null || raw.isEmpty()) return;
        String[] p = raw.split(",", -1);
        if (p.length != 30) return;
        try {
            int i=0;
            enabled=Boolean.parseBoolean(p[i++]);fps=Boolean.parseBoolean(p[i++]);coordinates=Boolean.parseBoolean(p[i++]);
            speed=Boolean.parseBoolean(p[i++]);lightLevel=Boolean.parseBoolean(p[i++]);gameTime=Boolean.parseBoolean(p[i++]);
            playerName=Boolean.parseBoolean(p[i++]);health=Boolean.parseBoolean(p[i++]);hunger=Boolean.parseBoolean(p[i++]);
            movement=Boolean.parseBoolean(p[i++]);effects=Boolean.parseBoolean(p[i++]);equipment=Boolean.parseBoolean(p[i++]);
            direction=Boolean.parseBoolean(p[i++]);tps=Boolean.parseBoolean(p[i++]);ping=Boolean.parseBoolean(p[i++]);
            biome=Boolean.parseBoolean(p[i++]);dayCounter=Boolean.parseBoolean(p[i++]);systemTime=Boolean.parseBoolean(p[i++]);
            netherConversion=Boolean.parseBoolean(p[i++]);chunkCoordinates=Boolean.parseBoolean(p[i++]);
            subChunkCoordinates=Boolean.parseBoolean(p[i++]);effectTimers=Boolean.parseBoolean(p[i++]);
            velocity=Boolean.parseBoolean(p[i++]);twelveHourTime=Boolean.parseBoolean(p[i++]);
            armorWarningPercent=Math.max(0,Math.min(100,Integer.parseInt(p[i++])));
            armorXOffset=Integer.parseInt(p[i++]);armorYOffset=Integer.parseInt(p[i++]);
            hudXOffset=Integer.parseInt(p[i++]);hudYOffset=Integer.parseInt(p[i++]);
            hudScale=Math.max(0.5f,Math.min(2.0f,Float.parseFloat(p[i])));
        } catch (RuntimeException ignored) {
            reset();
        }
    }
}
