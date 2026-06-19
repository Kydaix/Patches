# Instagram ReVanced — Patch "Instants : envoyer depuis la galerie"

Patch [ReVanced](https://revanced.app) personnel pour **Instagram** (`com.instagram.android`)
qui permet d'envoyer une **photo de la galerie** dans **Instants** (photos
éphémères, nom de code interne *QuickSnap*), alors qu'Instagram impose la caméra
in-app.

> **Migré sur le patcher `22.0.0` + plugin Gradle `app.revanced.patches:1.0.0-dev.10`
> + Gradle `9.3.1`** → le `.rvp` contient du **DEX** et se charge dans **ReVanced
> Manager** (testé compatible Manager `2.7.0-dev.5`). Cible Instagram : `434.x`.

---

## Comment ça marche

Instants n'a aucun import galerie implémenté, mais son pipeline d'envoi accepte un
`Bitmap` — `com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel.A01/A02/A03(Context, Bitmap, …)`.
Le patch injecte à l'entrée de ces 3 méthodes le remplacement du `Bitmap` capturé
par une image décodée depuis un chemin fixe :

```smali
const-string p1, "/sdcard/Pictures/instant.jpg"
invoke-static/range { p1 .. p1 }, Landroid/graphics/BitmapFactory;->decodeFile(Ljava/lang/String;)Landroid/graphics/Bitmap;
move-result-object p1
```

**UX** : déposer la photo dans `/sdcard/Pictures/instant.jpg`, puis capturer dans
Instants → l'Instant utilise ta photo. Ciblage par **nom de classe**
(`QuickSnapCameraViewModel`, non obfusqué).

Source : [`patches/.../instants/InstantsGalleryPatch.kt`](patches/src/main/kotlin/app/revanced/patches/instagram/instants/InstantsGalleryPatch.kt).

### ⚠️ Inconnue restante
Le patch compile, se dexe et s'injecte. **Non confirmé** : l'acceptation côté
**serveur** d'une image non issue de la caméra. À valider en envoyant un Instant.

---

## Le `.rvp` (prêt pour ReVanced Manager)

[`dist/patches-1.0.0.rvp`](dist/patches-1.0.0.rvp) — contient le DEX, importable
directement dans ReVanced Manager.

### L'importer dans ReVanced Manager
1. Télécharger `dist/patches-1.0.0.rvp` sur le téléphone.
2. ReVanced Manager → **Patch bundles / Sources** → ajouter → **depuis le stockage**
   → sélectionner le `.rvp`.
3. Sélectionner **Instagram**, activer le patch *« Instants : envoyer depuis la
   galerie »*, patcher (en mount si root, sinon install).
4. Déposer ta photo dans `/sdcard/Pictures/instant.jpg`, ouvrir Instants, capturer.

> Compatibilité : `.rvp` en patcher **22.0.0**. OK avec ReVanced Manager 2.7.x et
> revanced-cli 6.x. ❌ revanced-cli ≤ 5.x (patcher 21).

---

## Construire le `.rvp` soi-même

Prérequis : **JDK 17+** et un **token GitHub `read:packages`** (les dépendances
ReVanced sont sur GitHub Packages). Mettre les identifiants dans le fichier Gradle
global `~/.gradle/gradle.properties` (cf. [`gradle.properties.example`](gradle.properties.example)) :
```properties
githubPackagesUsername=ton_pseudo
githubPackagesPassword=ghp_xxx
```

Puis — **utiliser la tâche `buildAndroid`** (et non `build`, qui ne produit que des
`.class` non chargeables par Manager) :
```powershell
.\gradlew.bat :patches:buildAndroid
# -> patches\build\libs\patches-1.0.0.rvp  (contient classes.dex)
```

---

## Structure

```
patches/
  build.gradle.kts            métadonnées + deps (guava, stub)
  src/main/kotlin/.../instants/InstantsGalleryPatch.kt   le patch
  stub/                       stubs d'API Android (java-library)
extensions/proguard-rules.pro (requis par la config, pas d'extension ici)
gradle/ + gradlew             wrapper Gradle 9.3.1
settings.gradle.kts           plugin dev.10 + dépôt GitHub Packages
dist/patches-1.0.0.rvp        bundle pré-construit (DEX)
research/instants/            notes de reverse-engineering (Frida, etc.)
tools/mount-module/           module KernelSU (méthode mount alternative)
```

---

## Reverse-engineering

Démarche complète (Instants = QuickSnap, string-pooling FB, UI Compose, pipeline
Bitmap, anti-Frida de Meta, dex vs class) documentée dans
[`research/instants/`](research/instants/).
