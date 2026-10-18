/*
 * Copyright (c) 2026 lax1dude / Eagler 26.2. All Rights Reserved.
 */

package net.lax1dude.eaglercraft.v1_8.internal.webgl2;

import java.util.Arrays;

import org.teavm.jso.JSBody;

/**
 * Small cache for state that the command encoder owns exclusively between
 * render-pass boundaries. It deliberately does not cache texture parameters or
 * vertex attributes: those have wider correctness contracts (mip views and folded
 * baseVertex respectively).
 */
final class WebGL2StateCache {

	private static final int UNKNOWN = Integer.MIN_VALUE;
	private static final int GL_TEXTURE0 = 33984;

	private final boolean enabled;
	private int activeTexture = UNKNOWN;
	private Object[] textures2d = new Object[8];
	private Object[] texturesCube = new Object[8];
	private Object[] samplers = new Object[8];
	private boolean[] texture2dKnown = new boolean[8];
	private boolean[] textureCubeKnown = new boolean[8];
	private boolean[] samplerKnown = new boolean[8];
	private Object[] uboBuffers = new Object[8];
	private int[] uboOffsets = new int[8];
	private int[] uboSizes = new int[8];
	private boolean[] uboKnown = new boolean[8];
	private int scissorEnabled = UNKNOWN;
	private int scissorX;
	private int scissorY;
	private int scissorWidth;
	private int scissorHeight;
	private boolean scissorKnown;

	WebGL2StateCache() {
		this.enabled = isRequested();
		reportEnabled(this.enabled);
	}

	boolean isEnabled() {
		return this.enabled;
	}

	void invalidate() {
		this.activeTexture = UNKNOWN;
		Arrays.fill(this.texture2dKnown, false);
		Arrays.fill(this.textureCubeKnown, false);
		Arrays.fill(this.samplerKnown, false);
		Arrays.fill(this.uboKnown, false);
		this.scissorEnabled = UNKNOWN;
		this.scissorKnown = false;
	}

	boolean activeTexture(int unit) {
		if (!this.enabled || this.activeTexture != unit) {
			this.activeTexture = unit;
			return true;
		}
		return false;
	}

	boolean bindTexture(int target, Object texture) {
		if (!this.enabled) {
			return true;
		}
		int unit = this.activeTexture - GL_TEXTURE0;
		if (unit < 0) {
			return true;
		}
		ensureCapacity(unit);
		Object[] values;
		boolean[] known;
		if (target == WebGL2Const.GL_TEXTURE_2D) {
			values = this.textures2d;
			known = this.texture2dKnown;
		} else if (target == WebGL2Const.GL_TEXTURE_CUBE_MAP) {
			values = this.texturesCube;
			known = this.textureCubeKnown;
		} else {
			return true;
		}
		if (known[unit] && values[unit] == texture) {
			return false;
		}
		known[unit] = true;
		values[unit] = texture;
		return true;
	}

	boolean bindSampler(int unit, Object sampler) {
		if (!this.enabled) {
			return true;
		}
		ensureCapacity(unit);
		if (this.samplerKnown[unit] && this.samplers[unit] == sampler) {
			return false;
		}
		this.samplerKnown[unit] = true;
		this.samplers[unit] = sampler;
		return true;
	}

	boolean bindUboRange(int binding, Object buffer, int offset, int size) {
		if (!this.enabled) {
			return true;
		}
		ensureCapacity(binding);
		if (this.uboKnown[binding] && this.uboBuffers[binding] == buffer && this.uboOffsets[binding] == offset
				&& this.uboSizes[binding] == size) {
			return false;
		}
		this.uboKnown[binding] = true;
		this.uboBuffers[binding] = buffer;
		this.uboOffsets[binding] = offset;
		this.uboSizes[binding] = size;
		return true;
	}

	boolean scissorEnabled(boolean enabled) {
		int value = enabled ? 1 : 0;
		if (!this.enabled || this.scissorEnabled != value) {
			this.scissorEnabled = value;
			return true;
		}
		return false;
	}

	boolean scissor(int x, int y, int width, int height) {
		if (!this.enabled || !this.scissorKnown || this.scissorX != x || this.scissorY != y
				|| this.scissorWidth != width || this.scissorHeight != height) {
			this.scissorKnown = true;
			this.scissorX = x;
			this.scissorY = y;
			this.scissorWidth = width;
			this.scissorHeight = height;
			return true;
		}
		return false;
	}

	private void ensureCapacity(int index) {
		if (index < this.samplers.length) {
			return;
		}
		int length = this.samplers.length;
		while (length <= index) {
			length <<= 1;
		}
		this.textures2d = Arrays.copyOf(this.textures2d, length);
		this.texturesCube = Arrays.copyOf(this.texturesCube, length);
		this.samplers = Arrays.copyOf(this.samplers, length);
		this.texture2dKnown = Arrays.copyOf(this.texture2dKnown, length);
		this.textureCubeKnown = Arrays.copyOf(this.textureCubeKnown, length);
		this.samplerKnown = Arrays.copyOf(this.samplerKnown, length);
		this.uboBuffers = Arrays.copyOf(this.uboBuffers, length);
		this.uboOffsets = Arrays.copyOf(this.uboOffsets, length);
		this.uboSizes = Arrays.copyOf(this.uboSizes, length);
		this.uboKnown = Arrays.copyOf(this.uboKnown, length);
	}

	@JSBody(params = {}, script = "var o=(typeof eaglercraftXOpts!=='undefined')?eaglercraftXOpts:null;"
			+ "if(typeof o==='string'){try{o=JSON.parse(o);}catch(e){o=null;}}"
			+ "var q='';try{q=(new URLSearchParams(location.search||'')).get('glCache')||'';}catch(e){}"
			+ "if(q==='0'||q==='false')return false;"
			+ "if(q==='1'||q==='true')return true;"
			+ "return !(o&&o.glCache===false);")
	private static native boolean isRequested();

	@JSBody(params = { "enabled" }, script = "globalThis.__eaglerGlCacheEnabled=enabled;")
	private static native void reportEnabled(boolean enabled);
}
