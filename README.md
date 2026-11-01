# PhotoForge для Android

Офлайн-восстановление и апскейл фотографий на устройстве. Версия 1.2.3
получила интерфейс в стиле PhotoForge 2.0 и один проверенный ARM64-движок
Real-ESRGAN. Результат нейросети мягко смешивается с исходником, чтобы не
перерисовывать лицо, тело и мелкие формы. Выпуск подписывается отдельным
ключом, который не хранится в репозитории.

Скачать: [Android APK 1.2.3](dist/PhotoForge-Android-v1.2.3.apk) ·
[Windows x64](dist/PhotoForge-Windows-x64.zip).
Windows-версия находится в ZIP: распакуйте архив и запустите `PhotoForge.exe`.

Автор: [OlegBawq](https://github.com/OlegBawq) · Telegram: [@niix3](https://t.me/niix3)

Четыре профиля: без ИИ, мягкий (20%), баланс (35%) и максимум (50%). Для
маленьких повреждённых изображений используется x4, для более крупных —
автоматически более лёгкий режим с ограничением результата до 20 МП. В x4
нейросеть восстанавливает детали на уменьшенной копии, после чего PhotoForge
аккуратно доводит изображение до итогового размера. Для совместимости с Mali
используется безопасный CPU-режим без повреждённых Vulkan-тайлов.

Проект основан на [MatUnCNN](https://github.com/Celesth/MatUnCNN), который
использует [RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)
и [ncnn](https://github.com/Tencent/ncnn). Сохраняйте лицензии и уведомления
об исходных компонентах при распространении.

Для сборки нужны JDK 17, Android SDK 36, NDK 27.2.12479018 и CMake 3.22.1.
Запуск: `gradlew.bat assembleRelease`. Путь к ключу и параметры подписи
передаются переменными `PHOTO_FORGE_KEYSTORE`,
`PHOTO_FORGE_STORE_PASSWORD`, `PHOTO_FORGE_KEY_ALIAS` и
`PHOTO_FORGE_KEY_PASSWORD`.

## Исходный проект: MatUnCNN

Android native image upscaler powered by [ncnn](https://github.com/Tencent/ncnn). Uses AI models to upscale images with high quality on-device, with Vulkan GPU acceleration.

**Forked from** [Tumuyan/RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android) — rewritten in Kotlin with Jetpack Compose (Material 3), dynamic model downloading, result caching, and several stability improvements.

---

## Screenshots

<div align="center">
  <img src=".Screenshots/Screenshot_2026-06-07-08-55-26-609_com.matuncnn.app.png" width="130" alt="Upscale Tab"/>
  <img src=".Screenshots/Screenshot_2026-06-07-09-01-08-034_com.matuncnn.app.png" width="130" alt="Output Preview"/>
  <img src=".Screenshots/Screenshot_2026-06-07-09-05-35-186_com.matuncnn.app.png" width="130" alt="Debug Log"/>
</div>

> Screenshots taken on Mali-G52 | 4gb

---

## Features

- **AI Image Upscaling** — select from multiple ncnn models (Real-ESRGAN, RealSR, etc.)
- **Vulkan GPU Acceleration** — automatic Vulkan detection; falls back to CPU gracefully
- **Progress Tracking** — real-time ETA and percentage parsed from ncnn stdout
- **Output Preview** — pinch-to-zoom viewer with image metadata; save or share the result
- **Result Caching** — in-memory LRU cache avoids redundant processing
- **Noexec Bypass** — runs binaries on noexec-mounted filesystems via `/proc/self/fd`
- **Dynamic Asset Download** — model `.bin`/`.param` files fetched from GitHub Releases on first launch
- **Debug Log** — 500-entry ring buffer with copy-to-clipboard and file save
- **Customizable** — tile size, thread count, save format/name, notification mode, custom command arguments
- **Material 3 UI** — dark theme, bottom navigation, scrollable settings

---

## Requirements

- Android 8.0+ (API 26+)
- Vulkan-compatible GPU recommended (CPU fallback available)

---

## Installation

Download the latest APK from the [PhotoForge Releases](https://github.com/OlegBawq/PhotoUpscaling/releases) page and install it.

The signed APK contains the compatible ARM64 engines and model weights and works offline.

---

## Building

Requires JDK 17 and Android SDK with NDK 27+ and CMake 3.22+.

```shell
./gradlew assembleRelease -Dorg.gradle.java.home=/path/to/jdk-17
```

---

## Usage

1. Launch the app — model assets download on first run
2. Tap the image card to select an image
3. Choose a model from the dropdown
4. Tap **Run** to upscale
5. View the result in the output card; save or share from there

Tweak settings in the **Settings** tab: tile size, thread count, save format, naming scheme, custom arguments, and notification mode.

---

## Adding Models

Place `.bin` + `.param` file pairs into the `models/` folder. The app will detect and list them automatically.

Compatible sources:
- [Real-ESRGAN releases](https://github.com/xinntao/Real-ESRGAN/releases) — grab the `ncnn` zip from assets
- [Upscayl model pack](https://github.com/upscayl/upscayl-ncnn/tree/master/models)
- [RealSR-NCNN-Android assets](https://github.com/tumuyan/RealSR-NCNN-Android/tree/master/app/src/main/assets)

To convert a PyTorch `.pth` model yourself: `PyTorch → ONNX → ncnn` using `torch.onnx.export` and `onnx2ncnn`.

---

## Known Issues

- **Black output on some Adreno GPUs** — `vkQueueSubmit failed -4` (`VK_ERROR_DEVICE_LOST`) is a known upstream ncnn issue on Qualcomm Adreno drivers. The GPU watchdog kills heavy shader dispatches before they complete. Workarounds: use a lighter model (x2 or anime variant), or reduce tile size in settings. Tracked upstream at [Tencent/ncnn#925](https://github.com/Tencent/ncnn/issues/925).

---

## Tech Stack

| Layer | Tech |
|---|---|
| Language | Kotlin + C++ |
| UI | Jetpack Compose / Material 3 |
| Inference | ncnn (Vulkan) |
| Image processing | ImageMagick |
| Image loading | Coil |
| Preferences | DataStore |
| Min SDK | API 26 (Android 8.0) |

---

## Credits

- [nihui](https://github.com/nihui) — ncnn and realsr-ncnn-vulkan
- [Tumuyan](https://github.com/tumuyan) — original RealSR-NCNN-Android
- [xinntao](https://github.com/xinntao) — Real-ESRGAN

---

## License

MIT
