# Instagram ReVanced — Patch "Instants : importer depuis la galerie"

Patch [ReVanced](https://revanced.app) pour **Instagram** (`com.instagram.android`)
qui permet d'**envoyer une photo de ta galerie dans Instants** (photos éphémères,
nom de code interne *QuickSnap*), alors qu'Instagram impose la caméra in-app.

> Patcher `22.0.0` · plugin Gradle `app.revanced.patches:1.0.0-dev.10` · Gradle `9.3.1`.
> `.rvp` avec DEX → **importable dans ReVanced Manager** (testé Manager `2.7.0-dev.5`).
> Cible Instagram : **434.x**. ✅ Validé de bout en bout (le serveur accepte l'envoi).

---

## Utilisation

Après installation : ouvre **Instants** → écris une légende → appuie sur le
**bouton de capture habituel**. Au lieu de prendre une photo caméra, un
**sélecteur de galerie système s'ouvre** → choisis ta photo → elle part comme Instant.

> Il n'y a pas de bouton dédié : le déclencheur de capture **est** le sélecteur
> (ajouter un bouton dans l'UI Compose d'Instants serait bien plus complexe).
> Aucune permission requise (sélecteur photo système `ACTION_PICK_IMAGES`).

---

## Comment ça marche

Instants n'a aucun import galerie implémenté, mais son pipeline accepte un `Bitmap` :
`com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel.A01/A02/A03(Context, Bitmap, …)`.

1. **`A03`** (arrivée de la capture) : on appelle l'extension qui lance le
   **sélecteur photo** ; la capture caméra est annulée.
2. **`ModalActivity.onActivityResult`** : à la sélection, l'extension copie l'image
   choisie dans le **dossier privé d'Instagram**
   (`/sdcard/Android/data/com.instagram.android/files/instant.jpg`) puis re-déclenche `A03`.
3. **`A01/A02/A03`** lisent ce fichier (`BitmapFactory.decodeFile`, *null-safe*) →
   l'Instant part avec ta photo.

> Le dossier privé d'IG est crucial : un chemin `/sdcard/Pictures` échoue
> (`decodeFile` renvoie null à cause du scoped storage → NPE → pas d'upload).

Sources : [`patches/.../instants/InstantsGalleryPatch.kt`](patches/src/main/kotlin/app/revanced/patches/instagram/instants/InstantsGalleryPatch.kt)
· extension [`extensions/instants/`](extensions/instants/).

---

## Le `.rvp` (pour ReVanced Manager)

[`dist/patches-1.0.0.rvp`](dist/patches-1.0.0.rvp) — contient le DEX + l'extension.

**Import dans ReVanced Manager :** sources → ajouter → depuis le stockage → choisir
le `.rvp`. Sélectionner Instagram, activer *« Instants : importer depuis la galerie »*,
patcher. Compatibilité : patcher **22.x** (Manager 2.7+, revanced-cli 6.x ; ❌ cli ≤ 5.x).

**APK à patcher :** Instagram est en *split* ; ReVanced Manager veut un APK unique.
Fusionne les splits avec [APKEditor](https://github.com/REAndroid/APKEditor) :
`java -jar APKEditor.jar m -i bundle.apks -o single.apk`. Prends une source avec la
**vraie base + le split arm64** (sinon l'APK plante, libs natives manquantes).

> Sur Xiaomi/HyperOS, `adb install` peut être bloqué (`USER_RESTRICTED`) : installe
> en root (`su -c 'pm install single_patched.apk'`).

---

## Construire le `.rvp` soi-même

Prérequis :
- **JDK 17+**
- **Token GitHub `read:packages`** dans `~/.gradle/gradle.properties`
  (`githubPackagesUsername` / `githubPackagesPassword`, cf. `gradle.properties.example`)
- **SDK Android** (android-34 + build-tools 34) — requis par le module extension.
  Renseigner `local.properties` : `sdk.dir=C:/chemin/vers/Android/Sdk`

Puis — **tâche `buildAndroid`** (compile l'extension en DEX et l'ajoute au `.rvp`) :
```powershell
.\gradlew.bat :patches:buildAndroid
# -> patches\build\libs\patches-1.0.0.rvp
```

---

## Structure

```
patches/src/.../instants/InstantsGalleryPatch.kt   le patch (hooks A03/A01/A02 + onActivityResult)
extensions/instants/                               extension Android (sélecteur + copie + ré-injection)
patches/stub/                                      stubs d'API Android
dist/patches-1.0.0.rvp                             bundle pré-construit (DEX + extension)
research/instants/                                 notes de reverse-engineering
tools/mount-module/                                module KernelSU (mount alternatif)
```

---

## Reverse-engineering

Démarche complète (Instants=QuickSnap, string-pooling FB, UI Compose, pipeline
Bitmap, anti-Frida, dex vs class, scoped storage) : [`research/instants/`](research/instants/).
