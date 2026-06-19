# Changelog

## Unreleased

- Reorganise la documentation autour de l'import ReVanced Manager.
- Ajoute le workflow de publication GitHub Release.
- Ajoute le bundle `patches-latest.rvp` et les checksums de distribution.
- Ignore les formats de bundles APK locaux (`.apks`, `.apkm`, `.xapk`, `.aab`,
  `.idsig`).

## 1.0.0 - 2026-06-20

- Ajoute le patch **Instants : importer depuis la galerie** pour Instagram 434.x.
- Corrige le decalage d'une photo lors de l'upload Instants.
- Copie chaque image selectionnee dans un fichier unique sous `revanced_instants/`.
- Ajoute un filet de securite sur `A01/A02` pour annuler les uploads camera non
  desires.
