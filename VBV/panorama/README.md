# Custom title panorama

Upload your own six square PNG images into this folder:

- `panorama_0.png`
- `panorama_1.png`
- `panorama_2.png`
- `panorama_3.png`
- `panorama_4.png`
- `panorama_5.png`

In GitHub, open **VBV/panorama**, choose **Add file → Upload files**, and upload all six images together. Use six faces intended for a Minecraft panorama/skybox; 256×256 or 512×512 PNGs are suitable.

The build copies these faces into Minecraft's title-screen panorama assets. If no custom faces are present, it generates the built-in pixel-art panorama instead. If you upload only some of the six files, the build stops and lists the missing faces rather than silently using a mixed panorama.

These are title-screen images, not a single equirectangular 360° panorama. If your artwork is one wide 360° image, it needs to be split into six cube faces first.
