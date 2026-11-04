package com.melonman106.vbvclient;
public final class VBVCrosshairConfig {
 public static boolean enabled=false; public static int size=2; public static int color=0xFFFFFFFF;
 public static final boolean[] pixels=new boolean[225];
 static { for(int i=0;i<225;i++) pixels[i]=(i%15==7)||(i/15==7); }
 private VBVCrosshairConfig(){}
 public static void loadSettings(boolean active,int newSize,int newColor,String pattern){enabled=active;size=Math.max(1,Math.min(4,newSize));color=newColor;if(pattern!=null&&pattern.length()==225){for(int i=0;i<225;i++)pixels[i]=pattern.charAt(i)=='1';}}
 public static void cycleSize(){size=size>=4?1:size+1;}\n public static void cycleColor(){color=color==0xFFFFFFFF?0xFF55FF55:color==0xFF55FF55?0xFFFF5555:color==0xFFFF5555?0xFF55FFFF:color==0xFF55FFFF?0xFFFFFF55:0xFFFFFFFF;}\n public static void clear(){for(int i=0;i<pixels.length;i++)pixels[i]=false;}
 public static void defaultCrosshair(){clear();for(int i=0;i<15;i++){pixels[i*15+7]=true;pixels[7*15+i]=true;}}
}
