package com.melonman106.vbvclient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
/** Lightweight native Jade-style target tooltip. */
public final class VBVJadeHud {
 public static boolean enabled=true,showCoordinates=true,showEntityHealth=true,showBlockHardness=true,showTargetName=true; public static int offsetX=0,offsetY=0; private VBVJadeHud(){}
 public static void loadSettings(boolean active,boolean coordinates,boolean health,boolean hardness,boolean name,int x,int y){
  enabled=active;showCoordinates=coordinates;showEntityHealth=health;showBlockHardness=hardness;showTargetName=name;offsetX=x;offsetY=y;
 }
 // The tooltip text is rebuilt when the target changes and four times a second otherwise: name lookups,
 // String.format and the block-hardness query are slow in the browser build and were done every frame.
 private static Object cachedKey; private static String cTitle,cDetail,cCoords; private static long cachedAt;
 public static void render(GuiGraphicsExtractor g){
  if(!enabled)return; Minecraft mc=Minecraft.getInstance(); if(mc.player==null||mc.level==null)return;
  Object key=null; BlockPos blockPos=null;
  if(mc.crosshairPickEntity!=null&&mc.crosshairPickEntity.isAlive())key=mc.crosshairPickEntity;
  else if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK){blockPos=hit.getBlockPos();key=blockPos;}
  if(key==null){cachedKey=null;return;}
  long now=System.currentTimeMillis();
  if(cachedKey==null||!key.equals(cachedKey)||now-cachedAt>250L||now<cachedAt){
   cachedKey=key;cachedAt=now;String title,detail,coords;
   if(key instanceof net.minecraft.world.entity.Entity e){
    title=e.getType().getDescription().getString();detail=e.getName().getString();
    if(showEntityHealth&&e instanceof LivingEntity living)detail+="  HP: "+Math.round(living.getHealth()*10.0F)/10.0F+"/"+Math.round(living.getMaxHealth()*10.0F)/10.0F;
    BlockPos p=e.blockPosition();coords=p.getX()+", "+p.getY()+", "+p.getZ();
   }else{
    BlockPos p=blockPos;BlockState s=mc.level.getBlockState(p);title=s.getBlock().getName().getString();
    float hardness=s.getDestroySpeed(mc.level,p);detail=showBlockHardness?(hardness<0?"Unbreakable":"Hardness: "+String.format(java.util.Locale.ROOT,"%.1f",hardness)):"Block";
    coords=p.getX()+", "+p.getY()+", "+p.getZ();
   }
   cTitle=title;cDetail=detail;cCoords=coords;
  }
  String title=cTitle,detail=cDetail,coords=cCoords;
  if(title==null)return;if(!showTargetName)title="";int x=g.guiWidth()/2-90+offsetX,y=8+offsetY,h=showCoordinates?43:30;
  g.fill(x-2,y-2,x+184,y+h,0xD0101010);g.fill(x-2,y-2,x+184,y-1,0xFF707070);
  g.text(mc.font,title,x+6,y+4,0xFFFFFFFF,true);if(detail!=null)g.text(mc.font,detail,x+6,y+(title.isEmpty()?4:17),0xFFCCCCCC,false);
  if(showCoordinates&&coords!=null)g.text(mc.font,"XYZ: "+coords,x+6,y+30,0xFFAAAAAA,false);
 }
}
