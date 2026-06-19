# Trouver la cible du patch "Instants : import galerie"

Le patch a besoin d'un **fingerprint** pointant vers la méthode qui bloque la
galerie. Voici comment la trouver.

## 1. Récupérer l'APK Instagram

- Sur ton téléphone : extraire l'APK installé (app comme "APKExport"), ou
- Le télécharger (APKMirror / APKPure) — prends une version **récente** (≥ mai 2026,
  pour avoir Instants). Instagram est souvent en *split APK* (`base.apk` +
  `config.*`). C'est `base.apk` qui contient le code (les `classes*.dex`).

## 2. Décompiler

- **jadx-gui** (le plus pratique) : ouvre `base.apk`, attends l'indexation.
- ou `apktool d base.apk` pour le smali brut.

## 3. Chercher les ancres ("strings")

Dans jadx → recherche de texte (Ctrl+Shift+F). Termes à essayer :

- `instants` / `Instants`
- `camera_only`, `capture_only`, `disable_gallery`, `gallery_disabled`
- `media_picker`, `gallery`, `import`
- les **feature flags** Meta : cherche `ig_android_instants` ou des `L` (Launcher)
  dans la config (souvent des méthodes type `is...Enabled()` renvoyant un `boolean`).

But : repérer une **méthode** qui :
- renvoie un `boolean` (`Z`) et porte un nom/des strings liés à la galerie/caméra
  → cas idéal : on la force à `true` ; **ou**
- masque un bouton (`setVisibility(8)` = GONE sur la vue galerie) → on neutralise ;
  **ou**
- fait un `early return` qui empêche d'ouvrir le sélecteur de média.

## 4. Construire le fingerprint

Note, depuis la méthode trouvée :
- ses **strings** constantes (l'ancre la plus stable),
- ses `accessFlags` (public/final/static…),
- son **type de retour** et ses **paramètres**,
- au besoin, un fragment du nom de la classe (`custom { _, classDef -> ... }`).

Reporte-les dans `instantsGalleryGateFingerprint` (fichier `InstantsGalleryPatch.kt`).

## 5. Compiler + tester

```powershell
.\gradlew.bat build          # produit le .rvp
.\gradlew.bat apiDump        # si l'API publique a changé
```

Puis patcher l'APK via ReVanced Manager et tester : le bouton galerie
apparaît-il dans Instants ? L'upload est-il **accepté** (sinon = contrôle
serveur, cf. README racine).

## Astuce : copie-colle la classe ici

Le plus rapide : une fois la méthode candidate repérée dans jadx, colle-moi la
**classe décompilée** (ou le `.smali`). J'écris le fingerprint + l'injection
exacts à ta place.
