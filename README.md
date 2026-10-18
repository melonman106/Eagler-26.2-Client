# Eaglercraft 26.2 VBV Client

This repository contains the native Eaglercraft 26.2 u2 client-side ViaBackportVisuals project.

## Build pipeline (run in order)

1. **01 - Prepare Eaglercraft 26.2 u2 Source Project** — extracts `eaglercraft-26.2-patcher-main.tar.gz` into `U2-Patcher/`, then uses the U2 Setup JAR from `U1-Patcher/` (or the authenticated U2 release asset as a fallback) to obtain the verified portable build inputs. It uploads `Eaglercraft-26.2-u2-Source-Project-<run-id>`. The Minecraft 26.2 client JAR is not stored in this repository; the workflow downloads and verifies the pinned Mojang client unless an override is supplied.
2. **02 - Compile Eaglercraft 26.2 u2 (VBV + Herz)** — takes the latest successful source artifact from step 01, applies the client mod patch, compiles the Java sources, and builds the browser WASM output.
3. **03 - Package Eaglercraft 26.2 u2 (Single HTML)** — takes the successful run from step 02 and uses the U2 Patcher's single-HTML packager when available, falling back to the legacy packager only if the U2 source does not contain it.

All three workflows are manually triggered; pushes do not start builds automatically.

## Patcher layout

- `U1-Patcher/` — location for the U2 Setup JAR you are maintaining locally in the repository
- `eaglercraft-26.2-patcher-main.tar.gz` — U2 Patcher source archive
- `U2-Patcher/` — extracted U2 Patcher workspace created by workflow 01
- `VBV/` — client mod sources, patch scripts, and web-side mod helpers
- `ModsPack/` — optional resource-pack client mods imported during the VBV patch
- `u1-1-source/` — retained legacy tooling used only as a packaging fallback
- `.github/workflows/01-prepare-eaglercraft-source.yml` — U2 source-project preparation
- `.github/workflows/02-compile-eagler-vbv.yml` — U2 VBV + Herz source compilation
- `.github/workflows/03-single-html-eagler-vbv.yml` — U2 single-HTML packaging
- `.github/workflows/herz-compatibility.yml` — upstream Herz compatibility investigation

Server-side Fabric/Paper VBV code and old experimental build/probe files have been removed from this client repository.
