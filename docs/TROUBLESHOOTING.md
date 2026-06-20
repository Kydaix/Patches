# Depannage

## Manager refuse le bundle

- Utilise ReVanced Manager v2 / Patcher 22.x.
- Importe le fichier `.rvp`, pas le ZIP du code source GitHub.
- Reessaie avec l'asset de release `revanced-instagram-patches.rvp`.

## Instagram plante au lancement

Cause la plus probable : l'APK Instagram a ete reconstruit sans les bons splits
natifs. Il faut un APK unique qui contient au minimum la base et le split `arm64-v8a`.

## Manager demande un APK unique

Instagram est distribue en split APK. Utilise un downloader compatible Manager v2 ou
fusionne le bundle manuellement :

```powershell
java -jar APKEditor.jar m -i bundle.apks -o instagram-single.apk
```

## Le selecteur galerie ne s'ouvre pas

- Verifie que le patch **Instants : importer depuis la galerie** est bien active.
- Verifie que tu utilises le dernier `.rvp`.
- Reinstalle l'APK patche proprement si une ancienne version du patch etait deja
  installee.

## La mauvaise photo est envoyee

Ce bug etait cause par un etat QuickSnap reutilise trop tot et par un fichier cache
unique. Le patch actuel utilise un fichier unique par image, remplace aussi le
`File` passe a `A02` et annule les uploads camera qui partent pendant qu'un
selecteur galerie est deja ouvert.

Si le probleme reapparait :

- supprime l'application patchee ;
- reinstalle avec le dernier `.rvp` ;
- verifie que le bundle utilise est celui de la derniere release.

## Installation bloquee sur Xiaomi / HyperOS

Certains appareils bloquent `adb install` avec `USER_RESTRICTED`. Sur un appareil
root, installe via :

```shell
su -c 'pm install instagram-single_patched.apk'
```

## `BitmapFactory.decodeFile` renvoie null

Le patch copie l'image choisie dans le dossier prive d'Instagram :

```text
/sdcard/Android/data/com.instagram.android/files/revanced_instants/
```

Ce chemin evite les limites de scoped storage qui cassent la lecture directe depuis
`/sdcard/Pictures`.
