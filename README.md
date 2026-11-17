# FFMPEGplaydownloader (v1.6.0)

Samostatná univerzální Android aplikace (APK) v **Kotlinu** a **Jetpack Compose** s vestavěným **`yt-dlp`**, **`FFmpeg`** (`youtubedl-android 0.18.1`) a přehrávačem **`Media3 ExoPlayer 1.5.1`**.

> Aktuální verze: **`1.6.0`** (`versionCode = 7`) • Balíček: `com.ffmpegplaydownloader.app` • Android **7.0+ (API 24–35)**

---

## Obsah
- [Podporované architektury a požadavky](#podporované-architektury-a-požadavky)
- [Hlavní funkce](#hlavní-funkce)
  - [1. Vyhledávání (YouTube + YouTube Music, NLP & Hlasový vstup)](#1-vyhledávání-youtube--youtube-music-nlp--hlasový-vstup)
  - [2. Přehrávač Media3 ExoPlayer, Fullscreen, PiP & Pozadí](#2-přehrávač-media3-exoplayer-fullscreen-pip--pozadí)
  - [3. Zvukové efekty: 5pásmový Equalizér, Bass Boost & Loudness](#3-zvukové-efekty-5pásmový-equalizér-bass-boost--loudness)
  - [4. Rychlost přehrávání, opakování, Shuffle & Časovač vypnutí](#4-rychlost-přehrávání-opakování-shuffle--časovač-vypnutí)
  - [5. Stahování & FFmpeg konverze (Online i Offline v Knihovně)](#5-stahování--ffmpeg-konverze-online-i-offline-v-knihovně)
  - [6. Volitelný import `cookies.txt` (Netscape)](#6-volitelný-import-cookiestxt-netscape)
  - [7. Fronta, Oblíbené, Historie, Knihovna & Bezpečné ukončení aplikace](#7-fronta-oblíbené-historie-knihovna--bezpečné-ukončení-aplikace)
- [Architektura projektu](#architektura-projektu)
- [Oprávnění v AndroidManifest.xml](#oprávnění-v-androidmanifestxml)
- [Sestavení, podepsání (`apksigner`) a instalace přes ADB](#sestavení-podepsání-apksigner-a-instalace-přes-adb)

---

## Podporované architektury a požadavky

Univerzální APK obsahuje nativní knihovny pro všechny hlavní Android ABI (`useLegacyPackaging = true`, `android:extractNativeLibs="true"`):
- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- `x86`

Minimální verze systému je **Android 7.0 (`minSdk = 24`)**, cílová a kompilační verze **Android 15 (`compileSdk = 35`, `targetSdk = 35`)**.

---

## Hlavní funkce

### 1. Vyhledávání (YouTube + YouTube Music, NLP & Hlasový vstup)
- **Souběžné vyhledávání na YouTube i YouTube Music** (`YtDlpRepository`, `YouTubeMusicClient`):
  - **YouTube** přes vestavěný `yt-dlp` a **YouTube Music** přímo přes `WEB_REMIX` InnerTube API (`music.youtube.com/youtubei/v1/search`), včetně podpory přímých `youtube.com`, `youtu.be` a `music.youtube.com` URL.
  - **Příjem sdílených odkazů**: přes systémové menu *Sdílet* (`Intent.ACTION_SEND`, `text/plain` v `MainActivity`) lze odkaz z jiné aplikace otevřít přímo ve vyhledávání.
  - **Výběr zdroje (`SearchSourceFilter`)**: `✨ Vše (YT + YT Music)`, `▶ YouTube`, `🎧 YouTube Music`.
- **Inteligentní vyhledávání v přirozeném jazyce (`NaturalLanguageSearchEngine`)**:
  - Lokální (on-device) analýza konverzačních dotazů v **češtině i angličtině** — odstraňuje výplňové fráze, detekuje hudební vs. video záměr, nálady a situace (soustředění, spaní, trénink/fitko, do auta, akusticky, živě, instrumentální, 80./90. léta, české titulky), přidává vhodné štítky záměru a sestavuje optimalizované dotazy pro YouTube i YT Music.
  - Karta **Inteligentní analýza dotazu** zobrazuje interpretaci dotazu a umožňuje jedním klepnutím vyhledat dotaz doslovně (**Hledat doslova**) nebo přepnout čip **Chytré: ZAP / VYP**.
- **🎙️ Hlasové vyhledávání v češtině (`VoiceSearchController`)**:
  - Tlačítko mikrofonu přímo vedle vyhledávacího pole: při uděleném oprávnění `RECORD_AUDIO` spouští inline rozpoznávání `SpeechRecognizer` (`cs-CZ`) s živým přepisem v dialogu `VoiceSearchDialog`, animovaným indikátorem hlasitosti (`voiceRmsNormalized`) a klikatelnými ukázkami příkazů (*„Pusť rovnou klidný jazz“*, *„Najdi písničky od Kabát na YT Music“*, *„Dokument o vesmíru s českými titulky“*, *„Energický mix do fitka“*).
  - **Systémový fallback**: pokud přímé rozpoznávání není dostupné nebo uživatel zvolí tlačítko **Systémový mikrofon**, bezpečně se otevře systémový dialog `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`.
  - **Hlasové příkazy pro okamžité přehrání**: fráze typu *„Pusť rovnou…“* nebo *„Přehraj hned…“* po dokončení vyhledávání automaticky spustí první nalezený výsledek (`autoPlayFirstResult`). Během diktování aplikace přehrávání dočasně pozastaví (`wasPlayingBeforeVoice`) a při zrušení bez výsledku jej opět obnoví.
- **Rozlišení platforem (`PlatformBadge`, `ResultPlatformFilter`)**:
  - Barevné štítky platforem (`🎧 YT Music`, `▶ YouTube`, `🌐 Web`, `📁 Lokální`) na kartách výsledků, v přehrávači i v seznamech + rychlé filtrační čipy nad výsledky s počty položek (`Vše`, `🎧 YT Music`, `▶ YouTube`, `🌐 Web`).
- **Aktualizace `yt-dlp`**: automatická kontrola při spuštění aplikace i ruční aktualizace tlačítkem **🔄 Aktualizovat** v hlavičce (`YoutubeDL.UpdateChannel._STABLE`).

### 2. Přehrávač Media3 ExoPlayer, Fullscreen, PiP & Pozadí
- **Tři režimy streamování (`StreamMode`)** (`MediaPlayerController`):
  - `⚡ Rychlé video (MP4)` — progresivní stream s okamžitým startem.
  - `🎞️ HQ Mux (1080p+ video+zvuk)` — oddělený obrazový a zvukový DASH stream sloučený v reálném čase přes `MergingMediaSource`.
  - `🎵 Pouze zvuk (Audio stream)` — úsporný poslech bez videa se zobrazením náhledu a štítku platformy.
- **Časová osa a rychlé skoky**: interaktivní posuvník a tlačítka `⏮ 0:00`, `-30s`, `-10s`, Play/Pause, `+10s`, `+30s`.
- **České titulky**: automatické vyhledání českých stop (`cs` / `ces` / `cze`, oficiálních i automaticky generovaných ve formátech VTT/SRT/TTML) s možností zapnutí/vypnutí přímo v přehrávači.
- **Přehrávání na pozadí i při vypnuté obrazovce (`PlaybackService : MediaSessionService`)**:
  - Sdílený engine `PlayerEngine` zajišťuje plynulý chod jako foreground služba (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_MODE_NETWORK`) s ovládáním na zamykací obrazovce a v systémové notifikaci.
- **Fullscreen v režimu na šířku (`FullscreenPlayerOverlay`)**:
  - Automatické přepnutí do `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, skrytí systémových lišt, přepínání poměru obrazu (`Přizpůsobit` / `Vyplnit` — `RESIZE_MODE_FIT` / `RESIZE_MODE_ZOOM`) a plovoucí ovládací HUD.
- **Obraz v obraze — Picture-in-Picture (`PipHelper`)**:
  - Podpora na Androidu 8.0+ (`supportsPictureInPicture="true"`), ručně tlačítkem PiP i automaticky při opuštění aplikace během přehrávání (`onUserLeaveHint()`), s poměrem stran `16:9` pro video a `1:1` pro audio.
- **Automatické pokračování (`ResumePositionStore`)**:
  - Průběžné ukládání rozposlouchané pozice pro online i lokální média do `filesDir/resume_positions.json` (až 200 položek; ukládá se od 5. sekundy a při dosažení 94 % délky se záznam automaticky vyčistí).

### 3. Zvukové efekty: 5pásmový Equalizér, Bass Boost & Loudness
- Hardwarové audio efekty připojené přímo k `audioSessionId` přehrávače (`Equalizer`, `BassBoost`, `LoudnessEnhancer`) v dialogu `EqualizerDialog`:
  - **5 frekvenčních pásem**: `60 Hz`, `230 Hz`, `910 Hz`, `3.6 kHz`, `14 kHz` (vč. živého náhledu frekvenční křivky).
  - **Předvolby (`EqualizerPreset`)**: `Plochý`, `🎸 Bass Boost`, `🤘 Rock`, `🎤 Pop`, `🎷 Jazz`, `🎻 Klasika`, `🎧 Elektronika`, `🎙️ Vokály`, `🎚️ Vlastní`.
  - **Bass Boost** (posuvník `0–1000`) a **Normalizace hlasitosti (`LoudnessEnhancer`, `+650 mB`)**.
  - Trvalé uložení nastavení v `filesDir/equalizer.json` (`PersistenceRepository`).

### 4. Rychlost přehrávání, opakování, Shuffle & Časovač vypnutí
- Ovládání v modálním okně `SleepTimerAndSpeedDialog` (`PlaybackExtrasManager`):
  - **Rychlost přehrávání**: `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`.
  - **Opakování (`RepeatModeOption`)**: `Vypnuto`, `Opakovat 1` (nativní plynulá smyčka v ExoPlayeru), `Opakovat vše` (cyklování fronty).
  - **Náhodné přehrávání (`Shuffle`)**: náhodný výběr následující položky z fronty.
  - **Časovač vypnutí (`SleepTimerPreset`)**: `15 min`, `30 min`, `45 min`, `60 min` nebo `Po skladbě` s živým odpočtem `MM:SS` / `H:MM:SS`.

### 5. Stahování & FFmpeg konverze (Online i Offline v Knihovně)
- **Online stahování přes `yt-dlp` + `FFmpeg`** (`DownloadDialog`, `YtDlpRepository`):
  - **Audio formáty**: `MP3 — 320 kbps` (vč. obalu & metadat), `MP3 — 192 kbps` (vč. obalu & metadat), `FLAC`, `M4A (AAC)` (vč. obalu & metadat), `WAV`, `OPUS`.
  - **Video formáty**: `MP4 — 4K / 2K (best)`, `MP4 — 1080p`, `MP4 — 720p`, `MP4 — 480p`, `MKV — bezztrátové sloučení stop`.
  - **Doplňkové volby**: časový výřez **Od–Do** (`--download-sections` + `--force-keyframes-at-cuts`), vložení českých titulků (`--embed-subs`) a normalizace hlasitosti (`loudnorm=I=-16:TP=-1.5:LRA=11`).
  - **Cílová složka**: `Downloads/FFMPEGplaydownloader` (případně fallback do `Android/data/com.ffmpegplaydownloader.app/files/Download/FFMPEGplaydownloader`), automatická registrace do `MediaStore` přes `MediaScannerConnection`.
- **⚙️ Offline lokální FFmpeg nástroje v Knihovně (`LocalFfmpegProcessor`, `LocalFfmpegDialog`)**:
  - Zpracovávají již stažené soubory přímo v zařízení pomocí nativní binárky `libffmpeg.so` bez připojení k internetu, s živým ukazatelem průběhu a rychlosti:
    1. `🎵 Extrahovat MP3 (320 kbps)` (`_320k.mp3`, `libmp3lame`, ID3v2.3)
    2. `🎵 Extrahovat M4A (AAC)` (`_audio.m4a`, `aac 256k`, `+faststart`)
    3. `✂️ Oříznout Od–Do` (`_trim.<ext>`, `-c copy`, formát času `SS`, `MM:SS` nebo `HH:MM:SS`)
    4. `🔊 Normalizovat hlasitost (Loudnorm)` (`_loudnorm.<ext>`, `loudnorm=I=-16:TP=-1.5:LRA=11`)
    5. `📦 Zmenšit video na 720p (MP4)` (`_720p.mp4`, `libx264 -preset veryfast -crf 24 + aac 128k`, pro video soubory)

### 6. Volitelný import `cookies.txt` (Netscape)
- Kliknutím na štítek **🍪 Cookies** v hlavičce se otevře `CookiesSettingsDialog` (`CookiesRepository`):
  - Umožňuje vybrat textový soubor `cookies.txt` ve formátu Netscape (7 sloupců oddělených tabulátorem, včetně `#HttpOnly_`, limit 5 MB) z prohlížeče s ověřeným věkem.
  - Validovaný soubor se ukládá **výhradně lokálně** v soukromém úložišti aplikace (`filesDir/user_cookies.txt` a `filesDir/cookies_config.json`) a používá se výhradně jako `--cookies` pro požadavky `yt-dlp` (přehrávání, stahování a přímé URL).
  - Cookies lze kdykoli jedním přepínačem **pozastavit/aktivovat** nebo trvale **odstranit** (včetně automatického vyčištění mezipaměti streamů `ytDlpRepo.clearStreamCache()`).

### 7. Fronta, Oblíbené, Historie, Knihovna & Bezpečné ukončení aplikace
- **6 spodních záložek (`MainTab`)**:
  - `Hledat` • `Fronta` (`queue.json`) • `Oblíbené` (`favorites.json`) • `Historie` (`history.json`, posledních 100 položek) • `Stahování` (živý průběh, ETA, zrušení) • `Knihovna` (přehrávání, lokální FFmpeg konverze, sdílení přes `FileProvider`, mazání).
- **⏻ Elegantní ukončení aplikace (`Ukončit` v `TopStudioHeader`, novinka v1.6.0)**:
  - Tlačítko **Ukončit** (`Icons.Default.PowerSettingsNew`) v pravém horním rohu hlavičky zobrazí potvrzovací dialog (`"Ukončit aplikaci?"`), který upozorní i na případné běžící úlohy stahování nebo lokálního FFmpeg zpracování.
  - Po potvrzení (`MainViewModel.confirmExitApp()`):
    1. Uloží aktuální pozici přehrávání do `ResumePositionStore`.
    2. Zruší časovač vypnutí, hlasové vyhledávání (`VoiceSearchController`), vyhledávací korutiny, aktivní stahování `yt-dlp` (`ytDlpRepo.cancelDownload(...)`) a lokální FFmpeg procesy (`localFfmpegProcessor.cancelAll()`).
    3. Korektně vypne přehrávač a foreground službu `PlaybackService` přes `MediaPlayerController.shutdownAll()` → `PlayerEngine.shutdown(context)` (včetně uvolnění `ExoPlayer` a `stopService(...)`).
    4. Čistě zavře okno i úlohu v přehledu posledních aplikací (`activity?.finishAndRemoveTask()`).
  - **Zachování přehrávání na pozadí**: běžný odchod tlačítkem Domů nebo Zpět službu `PlaybackService` neukončuje (`onCleared()` volá pouze `playerController.release()`, nikoli `shutdownAll()`), takže přehrávání na pozadí i při vypnuté obrazovce funguje beze změny; navíc při odchodu během přehrávání se automaticky aktivuje PiP (pokud není otevřen dialog ukončení). Při odstranění karty ze seznamu nedávných aplikací v pozastaveném stavu `PlaybackService.onTaskRemoved()` notifikaci čistě odstraní (`stopForeground(STOP_FOREGROUND_REMOVE)`).

---

## Architektura projektu

```text
app/src/main/java/com/ffmpegplaydownloader/app/
├── FFmpegPlayDownloaderApp.kt          # Inicializace YoutubeDL a FFmpeg při startu aplikace
├── MainActivity.kt                     # Runtime oprávnění, Share intent, PiP callbacky
├── data/
│   ├── CookiesRepository.kt            # Validace, lokální uložení a správa Netscape cookies.txt
│   ├── LocalFfmpegProcessor.kt         # Offline spouštění libffmpeg.so + parser průběhu
│   ├── NaturalLanguageSearchEngine.kt  # CZ/EN NLP analýza dotazů, hlasové příkazy, řazení výsledků
│   ├── PersistenceRepository.kt        # JSON perzistence fronty, oblíbených, historie a equalizéru
│   ├── YouTubeMusicClient.kt           # InnerTube WEB_REMIX API klient pro YouTube Music
│   └── YtDlpRepository.kt              # Vyhledávání, stream cache, titulky, stahování a Knihovna
├── model/
│   └── MediaModels.kt                  # Datové modely a enumy (MediaPlatform, StreamMode, DownloadFormat...)
├── player/
│   ├── MediaPlayerController.kt        # ExoPlayer ovládání, MergingMediaSource, Equalizér/BassBoost/Loudness, shutdownAll()
│   ├── PipHelper.kt                    # Picture-in-Picture (API 26+) s poměrem 16:9 / 1:1
│   ├── PlaybackExtrasManager.kt        # Předvolby rychlosti, opakování a časovače vypnutí
│   ├── PlaybackService.kt              # MediaSessionService + PlayerEngine singleton pro chod na pozadí
│   └── ResumePositionStore.kt          # Perzistence pozic přehrávání (resume_positions.json)
├── ui/
│   ├── components/
│   │   ├── CookiesSettingsDialog.kt    # Dialog pro import a správu cookies.txt
│   │   ├── DownloadDialog.kt           # Dialog pro online stahování a FFmpeg parametry
│   │   ├── EqualizerDialog.kt          # 5pásmový Equalizér, Bass Boost a normalizace
│   │   ├── FullscreenPlayerOverlay.kt  # Krajinný fullscreen přehrávač s HUD
│   │   ├── LocalFfmpegDialog.kt        # Dialog pro lokální offline FFmpeg úlohy
│   │   ├── MainScreen.kt               # Hlavní obrazovka, TopStudioHeader, hlasový dialog, exit dialog, záložky
│   │   ├── PlatformBadge.kt            # Štítky platforem (YT Music / YouTube / Web / Lokální)
│   │   ├── PlayerSection.kt            # Karta přehrávače, posuvník, titulky, režimy streamu
│   │   └── SleepTimerAndSpeedDialog.kt # Dialog rychlosti, opakování, shuffle a časovače
│   └── theme/Theme.kt                  # Tmavé barevné schéma aplikace (AppColors)
├── viewmodel/
│   └── MainViewModel.kt                # Stav aplikace (MainUiState), koordinace všech modulů a confirmExitApp()
└── voice/
    └── VoiceSearchController.kt        # Správa SpeechRecognizer (cs-CZ) a RecognizerIntent fallbacku
```

---

## Oprávnění v AndroidManifest.xml

| Oprávnění | Účel |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Vyhledávání, streamování, stahování a aktualizace `yt-dlp` |
| `POST_NOTIFICATIONS` | Ovládací notifikace přehrávače na pozadí (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Služba `PlaybackService` pro přehrávání na pozadí a při vypnuté obrazovce |
| `WAKE_LOCK` | Udržení síťového a audio přehrávání při vypnutém displeji |
| `RECORD_AUDIO` | Přímé hlasové vyhledávání v češtině (`SpeechRecognizer`) |
| `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` | Čtení stažených souborů v Knihovně na Androidu 13+ |
| `WRITE_EXTERNAL_STORAGE` (≤ API 29), `READ_EXTERNAL_STORAGE` (≤ API 32) | Zápis a čtení ve složce `Downloads/FFMPEGplaydownloader` na starších verzích Androidu |

---

## Sestavení, podepsání (`apksigner`) a instalace přes ADB

### Požadavky prostředí (Windows / PowerShell)
- **JDK 17** (např. Android Studio JBR nebo Temurin 17 — projekt používá `JavaVersion.VERSION_17`)
- **Android SDK** (`compileSdk = 35`, `build-tools`, `platform-tools`)

### 1. Sestavení Debug APK
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jbr-17.0.14"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```
Výstupní Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Sestavení podepsaného Release APK
Konfigurace podepisování v `app/build.gradle.kts` načítá údaje ze souboru `keystore.properties` v kořeni repozitáře:
```properties
storeFile=keystore/ffmpegplaydownloader-release.jks
storePassword=VAS_HESLO_KEYSTORE
keyAlias=ffmpegplaydownloader
keyPassword=VAS_HESLO_KLICE
```
Sestavení Release APK:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleRelease
```
Výstupní Release APK: `app/build/outputs/apk/release/app-release.apk`

### 3. Ověření nebo ruční podepsání přes `apksigner`
Ověření podpisu výsledného APK (včetně v2/v3 schématu):
```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```
Pokud sestavujete nepodepsaný APK (`app-release-unsigned.apk`), lze jej po zarovnání pomocí `zipalign` podepsat příkazem:
```powershell
& "$env:ANDROID_HOME\build-tools\35.0.0\zipalign.exe" -p -f 4 app-release-unsigned.apk app-release-aligned.apk
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" sign `
  --ks keystore\ffmpegplaydownloader-release.jks `
  --ks-key-alias ffmpegplaydownloader `
  --out app-release-signed.apk `
  app-release-aligned.apk
```

### 4. Instalace do zařízení přes ADB
```powershell
adb devices
adb install -r app\build\outputs\apk\release\app-release.apk
```
*(Pro Debug variantu nahraďte cestu za `app\build\outputs\apk\debug\app-debug.apk`.)*
