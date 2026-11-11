# 🖍️📸 CamCrayon: Magic Living Drawings (Compose Multiplatform)

Turn kids' paper drawings held up to the camera (or drawn directly on screen) into a **60 FPS multi-scene living animated story** with **2D puppet physics**, **expressive read-aloud narration**, **storybook character upgrades**, and **interactive touch play** across **Android, iOS, and Desktop (JVM)**.

---

## 🏆 AI Model Evaluation: Gemini vs. Kimi vs. DeepSeek

| Capability Needed for Kids' Living Animation | **Google Gemini (Interactions API)** ⭐ **BEST FIT** | **Kimi (Moonshot AI)** | **DeepSeek (DeepSeek-V3 / R1)** |
| :--- | :--- | :--- | :--- |
| **Camera Drawing Vision Understanding** | ✅ **`gemini-3.8-flash`** (Multimodal vision + native JSON Schema output) | ✅ `moonshot-v1-8k-vision-preview` | ⚠️ Standard chat API is text-only (uses local color/shape analyzer + kid prompt) |
| **4-Scene Story & Animation Choreography** | ✅ **`gemini-3.8-flash`** (Outputs motion style, squash/stretch physics, biomes, particles & companions) | ✅ Supported via JSON prompt | ✅ Fast & creative JSON story generation |
| **Storybook Character Sprite Upgrade** | ✅ **`gemini-nano-banana-2.1`** (Transforms raw sketch into a vibrant die-cut character puppet) | ❌ Not available natively | ❌ Not available natively |
| **Expressive Storyteller Voiceover (TTS)** | ✅ **`gemini-3.8-flash-tts`** (`speech_metadata` styled WAV storyteller voice) | ❌ Not available natively | ❌ Not available natively |
| **First-Frame-to-Video MP4 Generation** | ✅ **`gemini-omni-1.2-flash`** (5s–15s 720p/1080p video from kid's drawing) | ❌ Not available natively | ❌ Not available natively |
| **Overall Fit Score** | **10 / 10 (#1 Recommended)** | **6.5 / 10 (Good Vision Fallback)** | **5.0 / 10 (Text Story Only)** |

> **Why Gemini is the #1 Choice:** A single `GEMINI_API_KEY` on the **Gemini Interactions API (`POST /v1beta/interactions`)** powers all four stages of the experience (`gemini-3.8-flash`, `gemini-nano-banana-2.1`, `gemini-3.8-flash-tts`, and `gemini-omni-1.2-flash`). And inside the app's **🔑 AI Key & Evaluation Hub**, you can also paste a **Kimi** or **DeepSeek** key to compare them live, or run in **Instant 60 FPS Magic Mode** with zero API keys required!

---

## ✨ How the Animation Engine Makes Kids' Drawings Come Alive

1. **📸 Smart Camera Paper Scanner & Die-Cut Puppet Extractor (`DrawingProcessor.kt`)**:
   - Samples paper background tones around the border of the camera photo, removes paper shadows and notebook lines via border flood-fill (preserving interior white regions like eyes or teeth!), boosts crayon vibrancy, and adds a crisp **white die-cut sticker halo** around the child's character.
2. **🎭 60 FPS Multi-Layer Living Animation Theater (`MagicalAnimationTheater.kt`)**:
   - **Squash-and-Stretch 2D Puppet Physics**: Compresses on landing, stretches at launch, banks into turns, and pulses when talking.
   - **6 Choreographed Motion Styles**: `BOUNCE_ADVENTURE` (parabolic hops), `FLY_LOOP` (figure-8 sky flight with rainbow contrail), `SWIM_WAVE` (underwater undulation), `DANCE_SPIN` (beat-bounce + 360° flips), `FLOAT_SPACE` (zero-G cosmic hover), and `SUPER_HERO_ZOOM` (sonic dash).
   - **6 Animated Parallax Biomes**: `SUNNY_MEADOW`, `STARRY_SPACE`, `OCEAN_REEF`, `CANDY_KINGDOM`, `ENCHANTED_FOREST`, and `VOLCANO_ISLAND`.
   - **Interactive Touch & Remix Controls**: Kids can **tap** their animated character to launch a 360° backflip + confetti burst, **drag** their character around the sky, toggle **Living Blinking Eyes & Talking Mouth**, switch between **Original Crayon Cutout** and **Nano Banana 2.1 Art**, and listen to **Karaoke Read-Aloud Narration**!

---

## 🚀 Running the Project

### 1. Run on Desktop (macOS / Windows / Linux)
```bash
./gradlew :composeApp:run
```

### 2. Build / Run on Android
```bash
./gradlew :composeApp:assembleDebug
```
Or open `/Users/ranjith/AIProjects/DoodleTales` in **Android Studio** and run the `composeApp` configuration on an Android device or emulator.

### 3. Build / Run on iOS
```bash
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```
Or open the project in **Xcode** / **Android Studio KMP plugin** to launch on an iOS Simulator or iPhone/iPad.
