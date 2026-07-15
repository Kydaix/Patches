# Installer avec ReVanced Manager

## 1. Ajouter la source distante

Dans ReVanced Manager v2, va dans l'onglet **Patches**, ajoute une source distante
avec **Enter URL**, puis colle :

```text
https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.json
```

Laisse **Auto update** active. Cette URL pointe vers un manifeste JSON compatible
Manager ; il reference le dernier bundle `.rvp` publie en release.

## 2. Import local de secours

Si l'ajout distant echoue, telecharge le bundle `.rvp` depuis la derniere release et
importe-le depuis le stockage :

```text
https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.rvp
```

Si aucune release GitHub n'est encore disponible, utilise le bundle commite dans le repo :

```text
dist/patches-latest.rvp
```

Ne telecharge pas le ZIP du code source GitHub : ReVanced Manager attend soit la
source JSON distante, soit le fichier `.rvp`.

## 3. Patcher Instagram

Selectionne Instagram (`com.instagram.android`) et active le patch :

```text
Instants : importer depuis la galerie
```

Le bundle est prevu pour Manager v2 / Patcher 22.x.

Versions Instagram validées :

- recommandée : `438.0.0.28.88` ;
- variante testée : `arm64-v8a` / `xxxhdpi` (`versionCode 384409708`) ;
- compatibilité historique : `434.0.0.44.74` (`versionCode 384009971`).

Manager doit afficher la version comme compatible avant de lancer le patch.

## 4. Fournir l'APK Instagram

Instagram est distribue en split APK. Si Manager ne recupere pas automatiquement un
APK compatible, il faut fournir un APK unique contenant au minimum la base et le split
`arm64-v8a`.

Option simple avec les downloaders Manager v2 :

- installer un downloader compatible, par exemple APKMirror Downloader ;
- redemarrer Manager ;
- laisser Manager recuperer la version Instagram cible.

Option manuelle avec APKEditor :

```powershell
java -jar APKEditor.jar m -i bundle.apks -o instagram-single.apk
```

Utilise ensuite `instagram-single.apk` dans ReVanced Manager.

## 5. Verifier

Une fois Instagram patche et installe :

1. Ouvre Instants.
2. Appuie sur le bouton de capture.
3. Le selecteur de galerie doit s'ouvrir.
4. La photo choisie doit etre envoyee comme Instant.
