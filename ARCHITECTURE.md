# Architecture du projet — Soundboard ESP

Application Android (Kotlin) permettant de déclencher des sons depuis une grille de boutons, organisés en pages. Les sons sont persistés localement via Room (SQLite) et lus via SoundPool.

---

## Structure générale

```
soundboard_esp/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Déclaration de l'app, permissions, activités
│   │   ├── java/com/example/soundboard_esp/
│   │   │   ├── MainActivity.kt          # Point d'entrée — redirecteur vers SoundboardActivity
│   │   │   ├── data/                    # Couche données (Room + Repository)
│   │   │   │   ├── database/
│   │   │   │   │   ├── Sound.kt         # Entité Room (modèle de données)
│   │   │   │   │   ├── SoundDao.kt      # Requêtes SQL (DAO)
│   │   │   │   │   └── SoundDatabase.kt # Instance singleton de la BDD Room
│   │   │   │   └── repository/
│   │   │   │       └── SoundRepository.kt # Intermédiaire ViewModel ↔ DAO
│   │   │   └── ui/                      # Couche présentation
│   │   │       ├── SoundboardActivity.kt  # Activité principale (grille + audio)
│   │   │       ├── FavoriteSoundAdapter.kt # Adaptateur RecyclerView (liste des favoris)
│   │   │       ├── viewmodel/
│   │   │       │   └── SoundboardViewModel.kt # ViewModel (état UI + actions CRUD)
│   │   │       └── theme/               # Thème Compose (non utilisé dans l'UI principale)
│   │   │           ├── Color.kt         # Palette Material 3
│   │   │           ├── Theme.kt         # Configuration du thème sombre/clair
│   │   │           └── Type.kt          # Styles typographiques
│   │   └── res/
│   │       ├── layout/                  # Layouts XML des activités et dialogs
│   │       ├── drawable/                # Backgrounds et icônes vectoriels
│   │       ├── values/                  # Couleurs, strings, thèmes
│   │       └── mipmap-*/               # Icônes de l'application (densités multiples)
│   └── build.gradle.kts                 # Dépendances et config du module app
├── gradle/
│   └── libs.versions.toml              # Catalogue centralisé des versions de dépendances
└── settings.gradle.kts                 # Déclaration des modules du projet
```

---

## Modules / couches détaillées

### 1. Base de données — `app/src/main/java/.../data/database/`

| Fichier | Rôle |
|---|---|
| `Sound.kt` | Entité Room mappée sur la table `sounds`. Contient : `id`, `name`, `filePath` (URI SAF), `buttonPosition` (1–18), `pageNumber`, `buttonColor` (hex), `isFavorite`. |
| `SoundDao.kt` | Interface DAO Room. Expose les requêtes CRUD et des `Flow<List<Sound>>` pour les lectures réactives. |
| `SoundDatabase.kt` | Classe abstraite `RoomDatabase`. Singleton thread-safe. Fichier SQLite : `soundboard_database`. Version actuelle : **2** (migration destructive activée). |

**Chemin du fichier SQLite sur l'appareil :**
```
/data/data/com.example.soundboard_esp/databases/soundboard_database
```

**Schéma de la table `sounds` :**
```
sounds (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  name           TEXT,
  filePath       TEXT,    -- URI Android (ex: content://...)
  buttonPosition INTEGER, -- 1 à 18
  pageNumber     INTEGER, -- 0-indexé
  buttonColor    TEXT,    -- Hex, ex: "#4ECDC4"
  isFavorite     INTEGER  -- 0 ou 1 (booléen SQLite)
)
```

---

### 2. Repository — `app/src/main/java/.../data/repository/`

| Fichier | Rôle |
|---|---|
| `SoundRepository.kt` | Façade entre le ViewModel et le DAO. Délègue toutes les opérations sans logique supplémentaire. Facilite les tests unitaires et l'échange futur du DAO. |

---

### 3. ViewModel — `app/src/main/java/.../ui/viewmodel/`

| Fichier | Rôle |
|---|---|
| `SoundboardViewModel.kt` | Hérite d'`AndroidViewModel`. Gère la page courante (`_currentPage` MutableLiveData), expose `currentPageSounds` (LiveData via `switchMap`), et fournit les actions CRUD (`insertSound`, `updateSound`, `deleteSound`, `toggleFavorite`). |

**Flux de données :**
```
BDD Room  →  Flow<List<Sound>>  →  SoundRepository  →  ViewModel (asLiveData)  →  SoundboardActivity (observe)
```

---

### 4. UI — `app/src/main/java/.../ui/`

| Fichier | Rôle |
|---|---|
| `SoundboardActivity.kt` | Activité principale. Gère la grille de 18 boutons, la lecture audio (SoundPool), le glisser-déposer, les dialogs (ajout / renommage / suppression / favoris) et la navigation entre pages. |
| `FavoriteSoundAdapter.kt` | Adaptateur `ListAdapter` pour le `RecyclerView` dans le dialog des favoris. Utilise `DiffUtil` pour les mises à jour animées. |

**Grille de boutons :**
- 18 boutons (positions 1–18) disposés en grille 3×6 dans `activity_soundboard.xml`
- Un appui court joue le son
- Un appui long (1,5 s) ouvre le menu options
- Un glissement démarre le drag-and-drop pour réorganiser les sons

---

### 5. Ressources — `app/src/main/res/`

#### Layouts (`res/layout/`)

| Fichier | Utilisé dans |
|---|---|
| `activity_soundboard.xml` | `SoundboardActivity` — grille principale |
| `dialog_favorites.xml` | Dialog liste des favoris |
| `dialog_edittext.xml` | Dialog saisie de nom (ajout/renommage) |
| `dialog_message.xml` | Dialog confirmation (suppression, doublon) |
| `dialog_list_item.xml` | Item générique de liste dans un dialog |
| `item_favorite_sound.xml` | Item du RecyclerView dans `FavoriteSoundAdapter` |
| `simple_list_item_1.xml` | Item du menu options (favoris/renommer/supprimer) |

#### Drawables (`res/drawable/`)

| Fichier | Description |
|---|---|
| `pad_background.xml` | Fond d'un bouton au repos |
| `pad_background_active.xml` | Fond d'un bouton en cours de lecture (état actif) |
| `fav_dialog_bg.xml` | Fond arrondi du dialog favoris |
| `fav_item_bg.xml` | Fond d'un item favori |
| `fav_play_btn.xml` | Bouton play dans la liste favoris |
| `nav_footer_background.xml` | Fond de la barre de navigation (flèches + page) |
| `logo_app_kotlin.xml` | Logo affiché dans le header |

#### Valeurs (`res/values/`)

| Fichier | Contenu |
|---|---|
| `colors.xml` | Couleurs globales de l'app |
| `strings.xml` | Chaînes système (nom de l'app) |
| `strings_soundboard.xml` | Chaînes propres au soundboard |
| `themes.xml` | Thème Android (AppCompat) |

---

### 6. Configuration du projet

| Fichier | Rôle |
|---|---|
| `app/build.gradle.kts` | Dépendances (Room, KSP, Lifecycle, RecyclerView, Compose), version de l'app (1.1, versionCode 4), minSdk 24, targetSdk 36 |
| `gradle/libs.versions.toml` | Catalogue centralisé des versions (Room, Kotlin, Compose BOM, etc.) |
| `settings.gradle.kts` | Modules déclarés (`:app`) et dépôts Maven |
| `AndroidManifest.xml` | Permissions (`READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`), déclaration des activités |

---

## Architecture globale (MVVM)

```
┌─────────────────────────────┐
│       SoundboardActivity    │  ← UI (View)
│       FavoriteSoundAdapter  │
└────────────┬────────────────┘
             │ observe LiveData
┌────────────▼────────────────┐
│    SoundboardViewModel      │  ← ViewModel
└────────────┬────────────────┘
             │ appelle
┌────────────▼────────────────┐
│      SoundRepository        │  ← Repository
└────────────┬────────────────┘
             │ délègue
┌────────────▼────────────────┐
│         SoundDao            │  ← DAO (Room)
│         SoundDatabase       │
│         Sound (entité)      │
└─────────────────────────────┘
                ↕
         soundboard_database  ← SQLite (sur l'appareil)
```

---

## Permissions Android requises

| Permission | Pourquoi |
|---|---|
| `READ_MEDIA_AUDIO` (API 33+) | Lire les fichiers audio sélectionnés par l'utilisateur |
| `READ_EXTERNAL_STORAGE` (API < 33) | Équivalent pour les versions Android plus anciennes |

Les URIs des fichiers sont persistées via `ContentResolver.takePersistableUriPermission()` pour survivre aux redémarrages de l'appareil.
