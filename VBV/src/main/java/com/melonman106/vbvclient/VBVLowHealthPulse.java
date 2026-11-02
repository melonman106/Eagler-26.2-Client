package com.melonman106.vbvclient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
public final class VBVLowHealthPulse {
 private VBVLowHealthPulse(){}
 public static void render(GuiGraphicsExtractor g){if(!VBVLowHealthConfig.enabled)return;Minecraft m=Minecraft.getInstance();if(m.player==null)return;float hearts=m.player.getHealth()/2.0f;if(hearts>=VBVLowHealthConfig.thresholdHearts)return;float ratio=Math.max(0f,Math.min(1f,(VBVLowHealthConfig.thresholdHearts-hearts)/Math.max(.5f,VBVLowHealthConfig.thresholdHearts)));double pulse=(Math.sin(System.currentTimeMillis()/180.0)+1.0)/2.0;int alpha=(int)(255.0*VBVLowHealthConfig.pulseStrength*ratio*(0.35+0.65*pulse));g.fill(0,0,m.getWindow().getGuiScaledWidth(),m.getWindow().getGuiScaledHeight(),(alpha<<24)|0x00FF0000);}
}
