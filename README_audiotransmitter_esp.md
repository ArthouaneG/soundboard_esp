# ESP32 — Récepteur Audio Bluetooth vers I2S

## Vue d'ensemble

Ce projet transforme un **ESP32** en récepteur audio Bluetooth stéréo. Il reçoit un flux audio via le protocole **A2DP** (Advanced Audio Distribution Profile) depuis n'importe quel appareil Bluetooth (téléphone, ordinateur, etc.) et le renvoie en temps réel vers un DAC audio **UDA1334A** via le protocole **I2S**.

```
[ Téléphone / PC ]  ---Bluetooth A2DP--->  [ ESP32 ]  ---I2S--->  [ UDA1334A ]  --->  [ 🎧 Casque / Haut-parleur ]
```

---

## Matériel utilisé

| Composant | Rôle |
|---|---|
| **ESP32-WROOM-32** | Microcontrôleur avec Wi-Fi et Bluetooth intégrés |
| **Adafruit UDA1334A** | DAC I2S stéréo — convertit le signal numérique en audio analogique |
| Breadboards (x2) | Support de prototypage |
| Fils de connexion | Liaison entre l'ESP32 et le DAC |

---

## Schéma de montage

```
        ESP32-WROOM-32
       ┌──────────────┐
       │              │
  3.3V ┤──────────────┼──────────────────────┐
   GND ┤──────────────┼──────────────────────┼──┐
       │              │                      │  │
GPIO26 ┤ (BCLK)       ├──────────────────┐   │  │
GPIO25 ┤ (WS / LRCK)  ├───────────────┐  │   │  │
GPIO22 ┤ (DOUT / DIN) ├────────────┐  │  │   │  │
       │              │            │  │  │   │  │
       └──────────────┘            │  │  │   │  │
                                   │  │  │   │  │
        UDA1334A (DAC I2S)         │  │  │   │  │
       ┌──────────────┐            │  │  │   │  │
   DIN ┤◄─────────────┼────────────┘  │  │   │  │
  BCLK ┤◄─────────────┼───────────────┘  │   │  │
  WSEL ┤◄─────────────┼──────────────────┘   │  │
   VIN ┤◄─────────────┼──────────────────────┘  │
   GND ┤◄─────────────┼─────────────────────────┘
       │              │
  LOUT ┤──┐  Sortie audio
  ROUT ┤──┘  analogique stéréo → 🎧
       └──────────────┘
```

### Tableau de câblage

| Broche ESP32 | Broche UDA1334A | Couleur (photo) | Description |
|:---:|:---:|:---:|---|
| GPIO **26** | **BCLK** | Bleu | Bit Clock — horloge de synchronisation bit |
| GPIO **25** | **WSEL** | Blanc | Word Select — sélection canal Gauche/Droit |
| GPIO **22** | **DIN** | Vert/Teal | Data In — données audio numériques |
| **3.3V** | **VIN** | Rouge | Alimentation du DAC |
| **GND** | **GND** | Noir | Masse commune |

> Le protocole **I2S** (Inter-IC Sound) utilise 3 fils de signal : une horloge bit (BCLK), une horloge de sélection de mot/canal (WS/LRCK) et une ligne de données série (DATA).

---

## Librairies utilisées

### 1. `AudioTools` — *pschatzmann/arduino-audio-tools*

Librairie généraliste pour le traitement et le transport audio sur microcontrôleurs Arduino/ESP32.

- Fournit la classe **`I2SStream`** qui abstrait le pilote I2S de l'ESP32.
- Gère la configuration du format audio : fréquence d'échantillonnage, résolution (bits), nombre de canaux.
- Sert de **pipeline** entre la source audio (Bluetooth A2DP) et la sortie physique (I2S).
- Doit impérativement être incluse **avant** `BluetoothA2DPSink`.

### 2. `BluetoothA2DPSink` — *pschatzmann/ESP32-A2DP*

Librairie qui implémente le profil Bluetooth **A2DP Sink** (récepteur) sur l'ESP32.

- **A2DP** (Advanced Audio Distribution Profile) est le protocole Bluetooth utilisé pour le streaming audio stéréo haute qualité.
- Le mode **Sink** signifie que l'ESP32 se comporte comme un récepteur (équivalent d'une enceinte Bluetooth).
- Décode automatiquement l'audio reçu au format **SBC** (Sub-Band Coding).
- Expose un nom Bluetooth configurable (`"ESP_Speaker"` dans ce projet).
- Accepte un objet `AudioStream` (ici `I2SStream`) comme sortie via `set_output()`.

---

## Explication du code

### Initialisation des objets

```cpp
BluetoothA2DPSink a2dp_sink;   // Objet récepteur Bluetooth A2DP
I2SStream i2s;                 // Objet flux audio I2S (sortie vers le DAC)
```

### Définition des broches I2S

```cpp
#define I2S_BCLK 26   // Bit Clock
#define I2S_WS   25   // Word Select (LR Clock)
#define I2S_DOUT 22   // Data Out de l'ESP32 → Data In du DAC
```

### `setup()` — Configuration au démarrage

```cpp
// 1. Désactivation du watchdog (évite les redémarrages intempestifs)
esp_task_wdt_deinit();

// 2. Configuration du flux I2S
auto cfg = i2s.defaultConfig(TX_MODE);  // Mode transmission (envoi de données)
cfg.pin_bck  = I2S_BCLK;               // Broche Bit Clock
cfg.pin_ws   = I2S_WS;                 // Broche Word Select
cfg.pin_data = I2S_DOUT;               // Broche Data
cfg.sample_rate     = 44100;           // Qualité CD (44.1 kHz)
cfg.bits_per_sample = 16;              // Résolution 16 bits
cfg.channels        = 2;               // Stéréo
i2s.begin(cfg);

// 3. Connexion du pipeline : A2DP → I2S
a2dp_sink.set_output(i2s);
a2dp_sink.set_on_connection_state_changed(connection_state_changed);
a2dp_sink.set_auto_reconnect(false);
a2dp_sink.start("ESP_Speaker");        // Nom visible par les appareils Bluetooth
```

### `loop()` — Boucle principale

```cpp
// Affiche un message toutes les 5 secondes si en attente de connexion
// L'audio est entièrement géré en arrière-plan par les librairies
```

### `connection_state_changed()` — Callback de connexion

Fonction appelée automatiquement lors d'un changement d'état Bluetooth :
- **CONNECTED** → l'audio commence à être transmis au DAC
- **DISCONNECTED** → aucun signal audio
- **CONNECTING** → négociation en cours

---

## Flux de données

```
Appareil Bluetooth
        │
        │ Bluetooth A2DP (SBC codec)
        ▼
  [ ESP32 — BluetoothA2DPSink ]
        │  Décodage SBC → PCM 44100Hz / 16-bit / Stéréo
        ▼
  [ I2SStream (AudioTools) ]
        │  3 fils I2S : BCLK, WSEL, DIN
        ▼
  [ UDA1334A — DAC I2S ]
        │  Conversion numérique → analogique
        ▼
  [ Sortie Jack 3.5mm stéréo ]
        │
       🎧 Audio
```

---

## Utilisation

1. Téléverser le code sur l'ESP32 via Arduino IDE.
2. Ouvrir le **Moniteur série** à **115200 bauds**.
3. Sur un téléphone ou ordinateur, rechercher les appareils Bluetooth.
4. Se connecter à **`ESP_Speaker`**.
5. Lancer n'importe quelle musique — elle sera jouée via le DAC UDA1334A.

---

## Dépendances Arduino IDE

| Librairie | Auteur | Installation |
|---|---|---|
| `arduino-audio-tools` | Phil Schatzmann | Library Manager ou GitHub |
| `ESP32-A2DP` | Phil Schatzmann | Library Manager ou GitHub |

> **Note :** Sélectionner la carte **ESP32 Dev Module** dans Arduino IDE. La librairie `AudioTools` doit être installée avant `ESP32-A2DP`.
