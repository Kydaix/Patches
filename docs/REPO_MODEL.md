# Organisation du repo

Ce repo suit le modele ReVanced utile pour un projet de patches tiers, sans reprendre
toute l'infrastructure officielle.

## Responsabilites

```text
patches/                 code Kotlin des patches ReVanced
extensions/              code Android injecte dans l'application cible
dist/patches.json        source distante compatible ReVanced Manager v2
dist/*.rvp               bundles locaux de secours
.github/workflows/       build et publication des assets de release
docs/                    installation, build, depannage
```

## Distribution Manager

ReVanced Manager v2 ne compile pas un repo Git. Pour une source distante, il attend un
JSON au format `ReVancedAsset` :

```json
{
  "version": "v1.0.5",
  "created_at": "2026-06-20T00:00:00",
  "description": "Instagram ReVanced Patches 1.0.5",
  "download_url": "https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.rvp"
}
```

L'URL a ajouter dans Manager est donc le manifeste JSON :

```text
https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.json
```

Le fichier `.rvp` reste utile pour un import local de secours.

## Release

Chaque tag `v*` declenche le workflow de release. Il construit le bundle avec
`:patches:buildAndroid`, puis publie :

- `revanced-instagram-patches.json` : source distante Manager ;
- `revanced-instagram-patches.rvp` : asset stable pointe par le JSON ;
- `revanced-instagram-patches-<version>.rvp` : asset versionne ;
- `SHA256SUMS.txt` : checksums des assets publies.

## Ce qui reste volontairement simple

Le repo ne reprend pas le pipeline complet du template officiel : pas de
semantic-release, pas de branche `dev` obligatoire, pas de publication Maven et pas de
GPG pour l'instant. Ces elements deviennent utiles si le nombre de patches augmente ou
si le repo est distribue a plus grande echelle.
