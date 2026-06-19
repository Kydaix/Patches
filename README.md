# Instagram ReVanced — Patch "Instants : envoyer depuis la galerie"

Patch [ReVanced](https://revanced.app) personnel pour **Instagram** (`com.instagram.android`)
qui permet d'envoyer une **photo de la galerie** dans **Instants** (la fonction de
photos éphémères, nom de code interne *QuickSnap*), alors qu'Instagram impose
normalement la caméra in-app.

> Construit sur le [template officiel](https://github.com/ReVanced/revanced-patches-template)
> (Patcher `21.0.0`, plugin Gradle `app.revanced.patches:1.0.0-dev.5`).
> **Version Instagram ciblée : `434.0.0.44.74`.**

---

## Comment ça marche

Instants n'a **aucun import galerie implémenté** : impossible de « réactiver » une
fonction absente. Mais son pipeline d'envoi accepte un `Bitmap` —
`com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel.A01/A02/A03(Context, Bitmap, …)`.

Le patch injecte à l'entrée de ces 3 méthodes un remplacement du `Bitmap` capturé
par une image décodée depuis un chemin fixe :

```smali
const-string p1, "/sdcard/Pictures/instant.jpg"
invoke-static/range { p1 .. p1 }, Landroid/graphics/BitmapFactory;->decodeFile(Ljava/lang/String;)Landroid/graphics/Bitmap;
move-result-object p1
```

**UX** : déposer la photo à envoyer dans `/sdcard/Pictures/instant.jpg`, puis
capturer dans Instants (la caméra peut pointer n'importe où) → l'Instant utilise
ta photo. Le ciblage se fait par **nom de classe** (`QuickSnapCameraViewModel`,
non obfusqué), pas par chaîne (Instagram chiffre ses strings).

Source du patch : [`patches/src/main/kotlin/app/revanced/patches/instagram/instants/InstantsGalleryPatch.kt`](patches/src/main/kotlin/app/revanced/patches/instagram/instants/InstantsGalleryPatch.kt).

### ⚠️ Statut / inconnue restante

Le patch **compile et s'injecte** correctement, et le mount sur l'appareil a été
**vérifié** (MD5 identique). **Reste à confirmer côté serveur** : Instagram peut
rejeter une image non issue de la caméra (preuve de capture). Non tranché à ce
jour — à valider en envoyant un Instant réel.

---

## Le `.rvp`

Pré-construit : [`dist/patches-1.0.4.rvp`](dist/patches-1.0.4.rvp).
Pour le régénérer (nécessite un token GitHub `read:packages`, cf. plus bas) :

```powershell
.\gradlew.bat build   # -> patches\build\libs\patches-1.0.4.rvp
```

---

## L'appliquer toi-même

> Le `.rvp` est construit avec **Patcher 21.0.0**. Utilise donc un outil avec un
> patcher compatible. **revanced-cli 5.0.1** l'est (testé). Les versions récentes
> (revanced-cli 6.x, ReVanced Manager 2.7+) embarquent un patcher plus neuf où la
> classe `BytecodePatch` a disparu → elles **refuseront** ce `.rvp` tel quel.

### Méthode A — KernelSU (mount, sans désinstaller) ✅ testée

Téléphone rooté (Magisk/KernelSU). On patche **l'APK réellement installé** puis on
le monte par-dessus via un module — pas de re-login, réversible.

1. Extraire la base.apk installée :
   ```bash
   adb shell su -c "cp $(adb shell pm path com.instagram.android | grep base | sed 's/package://') /sdcard/ig_base.apk"
   adb pull /sdcard/ig_base.apk
   ```
2. Patcher avec revanced-cli 5.0.1 :
   ```bash
   java -jar revanced-cli-5.0.1-all.jar patch -p dist/patches-1.0.4.rvp \
     -e "Instants : envoyer depuis la galerie" --exclusive -f \
     -o ig_base_patched.apk ig_base.apk
   ```
3. Créer un module KernelSU (template fourni dans [`tools/mount-module/`](tools/mount-module/)) :
   copier `ig_base_patched.apk` en `com.instagram.android.apk` dans le module,
   pousser le tout dans `/data/adb/modules/com.instagram.android-revanced/`, **reboot**.
   Voir [`tools/mount-module/README.md`](tools/mount-module/README.md).
4. Déposer ta photo en `/sdcard/Pictures/instant.jpg`, ouvrir Instants, capturer.

Pour **désinstaller** : supprimer `/data/adb/modules/com.instagram.android-revanced` + reboot.

### Méthode B — sans root (install classique)

Patcher l'APK (un APK **unique** ; pour Instagram en split, fusionner d'abord les
splits avec [APKEditor](https://github.com/REAndroid/APKEditor) : `java -jar APKEditor.jar m -i bundle.apkm -o universal.apk`),
puis :
```bash
java -jar revanced-cli-5.0.1-all.jar patch -p dist/patches-1.0.4.rvp \
  -e "Instants : envoyer depuis la galerie" --exclusive -f -o patched.apk universal.apk
adb uninstall com.instagram.android   # ⚠️ perte de la session locale
adb install patched.apk
```

---

## Régénérer le `.rvp` (prérequis token)

Les dépendances ReVanced sont sur **GitHub Packages** (auth obligatoire même en
lecture). Crée un PAT `read:packages` (<https://github.com/settings/tokens>) et
mets-le dans `~/.gradle/gradle.properties` (`gpr.user` / `gpr.key`, cf.
`gradle.properties.example`). Puis `./gradlew.bat build`.

---

## Notes de reverse-engineering

La découverte (Instants = QuickSnap, string-pooling FB, UI Compose, pipeline
Bitmap, anti-Frida de Meta) est documentée dans [`research/instants/`](research/instants/).
