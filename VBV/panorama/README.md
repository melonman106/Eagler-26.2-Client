# Custom title panorama and animated overlay

## Panorama faces

Upload your own six square PNG images into this folder:

- `panorama_0.png`
- `panorama_1.png`
- `panorama_2.png`
- `panorama_3.png`
- `panorama_4.png`
- `panorama_5.png`

In GitHub, open **VBV/panorama**, choose **Add file → Upload files**, and upload all six images together. Use six faces intended for a Minecraft panorama/skybox; 256×256 or 512×512 PNGs are suitable.

These are title-screen images, not a single equirectangular 360° panorama. If your artwork is one wide 360° image, it needs to be split into six cube faces first.

## Panorama overlay (PNG, GIF or MP4)

Upload one overlay using the exact filename:

- `panorama_overlay.png` — static overlay.
- `panorama_overlay.gif` — animated overlay; transparency is preserved.
- `panorama_overlay.mp4` — animated video overlay.

The build automatically detects and converts GIF/MP4 into PNG animation frames; you do not need to convert them yourself. The animation loops on the custom home screen at 12 FPS, with frames scaled down to fit within 1280×720 while preserving their aspect ratio. FFmpeg runs in GitHub Actions, not on your Chromebook.

If more than one overlay format exists, MP4 takes priority over GIF, and GIF takes priority over PNG. Keep only the format you intend to use. PNG continues to use Minecraft's native static panorama overlay path.
