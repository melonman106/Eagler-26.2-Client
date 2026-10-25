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
 public static boolean enabled=true,showCoordinates=true; public static int offsetX=0,offsetY=0; private VBVJadeHud(){}
 public static void render(GuiGraphicsExtractor g){
  if(!enabled)return; Minecraft mc=Minecraft.getInstance(); if(mc.player==null||mc.level==null)return;
  String title=null,detail=null,coords=null;
  if(mc.crosshairPickEntity!=null&&mc.crosshairPickEntity.isAlive()){
   var e=mc.crosshairPickEntity;title=e.getType().getDescription().getString();detail=e.getName().getString();
   if(e instanceof LivingEntity living)detail+="  HP: "+Math.round(living.getHealth()*10.0F)/10.0F+"/"+Math.round(living.getMaxHealth()*10.0F)/10.0F;
   BlockPos p=e.blockPosition();coords=p.getX()+", "+p.getY()+", "+p.getZ();
  }else if(mc.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK){
   BlockPos p=hit.getBlockPos();BlockState s=mc.level.getBlockState(p);title=s.getBlock().getName().getString();
   float hardness=s.getDestroySpeed(mc.level,p);detail=hardness<0?"Unbreakable":"Hardness: "+String.format(java.util.Locale.ROOT,"%.1f",hardness);
   coords=p.getX()+", "+p.getY()+", "+p.getZ();
  }
  if(title==null)return;int x=g.guiWidth()/2-90+offsetX,y=8+offsetY,h=showCoordinates?43:30;
  g.fill(x-2,y-2,x+184,y+h,0xD0101010);g.fill(x-2,y-2,x+184,y-1,0xFF707070);
  g.text(mc.font,title,x+6,y+4,0xFFFFFFFF,true);if(detail!=null)g.text(mc.font,detail,x+6,y+17,0xFFCCCCCC,false);
  if(showCoordinates&&coords!=null)g.text(mc.font,"XYZ: "+coords,x+6,y+30,0xFFAAAAAA,false);
 }
}