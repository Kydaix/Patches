# Construire et publier

## Prerequis locaux

- JDK 17+
- Android SDK avec platform/build-tools 34
- `local.properties` contenant `sdk.dir=...`
- credentials GitHub Packages dans le Gradle global :

```properties
githubPackagesUsername=ton_pseudo_github
githubPackagesPassword=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
gpr.user=ton_pseudo_github
gpr.key=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Le token doit avoir le scope `read:packages`.

## Build local

```powershell
.\gradlew.bat clean :patches:buildAndroid
```

Le bundle est genere ici :

```text
patches/build/libs/patches-1.1.0.rvp
```

Pour actualiser le bundle distribue dans le repo :

```powershell
Copy-Item patches\build\libs\patches-1.1.0.rvp dist\patches-latest.rvp -Force
Copy-Item patches\build\libs\patches-1.1.0.rvp dist\patches-1.1.0.rvp -Force
```

## Checksums

```powershell
certutil -hashfile dist\patches-latest.rvp SHA256
certutil -hashfile dist\patches-1.1.0.rvp SHA256
certutil -hashfile dist\patches.json SHA256
```

Les valeurs doivent etre reportees dans `dist/SHA256SUMS.txt`.

## Release GitHub automatique

Après avoir augmenté `version` dans `gradle.properties`, pousse `main` :

```powershell
git push origin main
```

Le workflow `.github/workflows/release.yml` lit cette version, construit le bundle,
puis crée automatiquement le tag `v1.1.0` et la release correspondante. Si cette
release existe déjà, le push est ignoré sans reconstruire les assets.

Le déclenchement historique par push d'un tag `v*` et le lancement manuel depuis
l'onglet **Actions** restent disponibles pour le dépannage. Un tag manuel doit
correspondre exactement à la version de `gradle.properties`.

Assets publies :

- `revanced-instagram-patches.json` : source distante a ajouter dans Manager ;
- `revanced-instagram-patches.rvp` : nom stable a utiliser dans le README ;
- `revanced-instagram-patches-<version>.rvp` : asset versionne ;
- `SHA256SUMS.txt` : checksums de la release.

La source JSON suit le format `ReVancedAsset` attendu par Manager v2 et pointe vers
l'asset stable `.rvp`.

## Secrets CI

Le workflow essaie d'utiliser `github.actor` et `github.token` par defaut. Si GitHub
Packages refuse l'acces aux packages ReVanced, ajoute ces secrets au repo :

```text
GH_PACKAGES_USERNAME
GH_PACKAGES_TOKEN
```

`GH_PACKAGES_TOKEN` doit etre un PAT avec `read:packages`.
