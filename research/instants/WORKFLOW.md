# Patch "Instants : import galerie" — workflow d'analyse dynamique

Le tracing statique est défait par Instagram (chaînes chiffrées `StringTreeSet` +
caméra Instants probablement en Jetpack Compose, donc sans ID de ressource).
On trouve donc la cible **à l'exécution** avec Frida, puis on traduit en
fingerprint ReVanced.

État de l'outillage (déjà installé sur ce PC) :
- ✅ `frida` **17.15.0** (PC) — `frida --version`
- ✅ `adb` — `C:\Users\Administrator\IdeaProjects\.tools\platform-tools\adb.exe`
- ✅ jadx + corpus décompilé — `C:\Users\Administrator\IdeaProjects\.tools\ig\out_full`
- ✅ ressources décodées — `...\.tools\ig\apktool_out\res`
- ✅ projet ReVanced qui builde le `.rvp`

---

## 1. Un appareil de test (obligatoire)

Frida a besoin de `frida-server` qui tourne en **root** sur l'appareil. Deux voies :

- **Téléphone Android rooté** (Magisk), connecté en USB (débogage USB activé) ; ou
- **Émulateur** : Android Studio AVD **image "Google APIs" (pas "Play Store")** =
  rootable via `adb root`. (Attention : Instagram peut détecter l'émulateur ;
  un vrai device rooté est plus fiable.)

Vérifie la connexion :
```powershell
$adb = "C:\Users\Administrator\IdeaProjects\.tools\platform-tools\adb.exe"
& $adb devices
& $adb shell getprop ro.product.cpu.abi   # -> arm64-v8a (en général) ou x86_64 (émulateur)
```

---

## 2. Installer frida-server sur l'appareil

La version DOIT être **17.15.0** (= la version PC). Architecture = sortie de `ro.product.cpu.abi`.

1. Télécharge `frida-server-17.15.0-android-<abi>.xz` depuis
   <https://github.com/frida/frida/releases/tag/17.15.0>
2. Décompresse, pousse, lance :
```powershell
& $adb root
& $adb push frida-server-17.15.0-android-arm64 /data/local/tmp/frida-server
& $adb shell "chmod 755 /data/local/tmp/frida-server"
& $adb shell "/data/local/tmp/frida-server &"
```
Test : `frida-ps -U | findstr instagram`

---

## 3. Installer Instagram v434 sur l'appareil (split APK)

```powershell
& $adb install-multiple `
  "C:\Users\Administrator\IdeaProjects\.tools\ig\apkm\base.apk" `
  "C:\Users\Administrator\IdeaProjects\.tools\ig\apkm\split_config.arm64_v8a.apk" `
  "C:\Users\Administrator\IdeaProjects\.tools\ig\apkm\split_config.xxhdpi.apk"
```
(Adapte les splits à l'ABI/densité de l'appareil. L'`.apkm` contient tous les splits.)

> ℹ️ Le `.apkm` ne contient que les densités d'écran + `base.apk`. Si le split ABI
> (`split_config.arm64_v8a.apk`) manque, re-télécharge un bundle complet APKMirror
> pour l'ABI cible, ou installe la version du Play Store puis remplace par v434.

---

## 4. Lancer la traque

```powershell
cd C:\Users\Administrator\IdeaProjects\RevancedInstagram\research\instants
frida -U -f com.instagram.android -l frida-instants-gallery.js
```
Puis, dans l'app : connecte-toi → ouvre **Instants** (entrée caméra éphémère).

Ce que tu observes :
- `setVisibility(GONE) sur 'gallery_button'` + pile → **jackpot** : la dernière
  frame non-framework de la pile est la classe/méthode obfusquée qui masque la
  galerie. C'est la cible.
- `findViewById -> 'camera_roll_button'` + pile → la classe contrôleur caméra.
- Rien ne matche → la caméra est en **Compose** (cf. §Compose).

---

## 5. §Compose — si aucune vue XML ne matche

La caméra Instants est probablement en Compose (UI en code, sans `R.id`). Alors :
- Repère, via la pile des clics (`AndroidComposeView.dispatchTouchEvent`) ou via
  `Java.enumerateLoadedClasses()` filtré, la classe de l'écran caméra Instants.
- Cherche une méthode renvoyant `boolean` qui décide d'afficher la rangée galerie
  (souvent un `Composable` state) ; hooke-la et teste `return true` (voir le bas
  du script `.js`).
- Astuce ciblage : au moment où Instants s'ouvre, logue les classes nouvellement
  chargées :
  ```js
  Java.perform(() => Java.enumerateLoadedClasses({
    onMatch: n => { if (/Instant|Capture|Camera|Gallery/i.test(n)) console.log(n); },
    onComplete: () => {}
  }));
  ```
  (les classes app sont en `X.Cxxxx` mais certaines gardent un suffixe parlant.)

---

## 6. Du hook au fingerprint ReVanced

Une fois la méthode-gate confirmée (`return true` débloque la galerie) :

1. Récupère sa **signature obfusquée exacte** : classe `X.Cxxxx`, nom, type de
   retour, paramètres (Frida les donne via `.overloads`).
2. Retrouve-la dans le corpus décompilé (`out_full/sources/X/Cxxxx.java`) pour
   lire son corps et choisir des ancres **stables** :
   - type de retour + paramètres,
   - opcodes / appels internes caractéristiques,
   - constantes **numériques** (les int littéraux, eux, ne sont pas chiffrés),
   - `custom { method, classDef -> ... }` sur un trait reconnaissable.
   ⚠️ N'utilise PAS de `strings(...)` ici : elles sont poolées, donc absentes.
3. Reporte tout ça dans `instantsGalleryGateFingerprint`
   (`patches/src/.../instants/InstantsGalleryPatch.kt`) et code l'injection
   (`return true`, ou neutraliser le `setVisibility(GONE)`).
4. Build + test :
   ```powershell
   cd C:\Users\Administrator\IdeaProjects\RevancedInstagram
   .\gradlew.bat build      # -> patches\build\libs\*.rvp
   ```
   Patche l'APK via ReVanced Manager et vérifie sur l'appareil.

---

## 7. Le risque serveur (à tester en dernier)

Même galerie débloquée côté client, l'upload peut être **refusé côté serveur**
si Instagram exige une preuve de capture caméra (les events `instants_upload_start`
peuvent la transporter). Seul le test réel tranche. Si c'est bloqué serveur, un
patch client seul ne suffira pas — fin de la route côté ReVanced.
