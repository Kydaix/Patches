# Changelog

## Unreleased

## 1.0.5 - 2026-06-20

- Ajoute une source distante JSON compatible ReVanced Manager v2.
- Publie `revanced-instagram-patches.json` avec les assets de release pour permettre
  l'auto-update du bundle.
- Clarifie la documentation entre source distante et import local `.rvp`.
- Documente l'organisation du repo inspiree du modele ReVanced officiel.

## 1.0.4 - 2026-06-20

- Relance l'upload QuickSnap capture pendant que le selecteur galerie est ouvert.
- Capture les arguments complets de `A01/A02`, puis rejoue l'upload avec le
  `Bitmap` et le `File` de la photo choisie.
- Ajoute une continuation reflective minimale pour relancer les fonctions suspend
  d'Instagram sans reutiliser la coroutine deja terminee.

## 1.0.3 - 2026-06-20

- Corrige le cas ou la photo choisie est bien copiee mais ne part pas en upload.
- Reinjecte `A03` avec le bitmap selectionne au lieu d'un bitmap `null`.
- Rend la recherche reflective de `A03` plus robuste et logge les echecs sous
  le tag `RevancedInstants`.

## 1.0.2 - 2026-06-20

- Corrige un `VerifyError` dans `QuickSnapCameraViewModel.A02` sur Android.
- Copie les parametres `A02` dans des registres bas avant l'appel extension pour
  eviter un `invoke-static` invalide avec les registres hauts de cette methode.

## 1.0.1 - 2026-06-20

- Corrige un crash lors de l'ouverture de la page Instants.
- Limite le filet de securite `A01/A02` aux uploads camera qui partent pendant
  qu'un selecteur galerie est deja ouvert.
- Ignore les appels `A03` sans bitmap original pour eviter d'intercepter des
  chemins d'initialisation.
- Detecte aussi les activites encapsulees dans un `ContextWrapper`.

## 1.0.0 - 2026-06-20

- Ajoute le patch **Instants : importer depuis la galerie** pour Instagram 434.x.
- Corrige le decalage d'une photo lors de l'upload Instants.
- Copie chaque image selectionnee dans un fichier unique sous `revanced_instants/`.
- Ajoute un filet de securite sur `A01/A02` pour annuler les uploads camera non
  desires.
- Reorganise la documentation autour de l'import ReVanced Manager.
- Ajoute le workflow de publication GitHub Release.
- Ajoute le bundle `patches-latest.rvp` et les checksums de distribution.
- Ignore les formats de bundles APK locaux (`.apks`, `.apkm`, `.xapk`, `.aab`,
  `.idsig`).
