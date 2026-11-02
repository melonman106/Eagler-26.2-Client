package com.melonman106.vbvclient;
public final class VBVCrosshairConfig {
 public static boolean enabled=false; public static int size=15; public static int color=0xFFFFFFFF;
 public static final boolean[] pixels=new boolean[225];
 static { for(int i=0;i<225;i++) pixels[i]=(i%15==7)||(i/15==7); }
 private VBVCrosshairConfig(){}
 public static void clear(){for(int i=0;i<pixels.length;i++)pixels[i]=false;}
 public static void defaultCrosshair(){clear();for(int i=0;i<15;i++){pixels[i*15+7]=true;pixels[7*15+i]=true;}}
}
