# Instagram ReVanced - Instants depuis la galerie

Patch [ReVanced](https://revanced.app) pour **Instagram** (`com.instagram.android`) qui
permet d'envoyer une photo de la galerie dans **Instants** au lieu d'utiliser la
capture camera imposee par l'application.

## Installation rapide

1. Telecharge le bundle de patchs :
   [revanced-instagram-patches.rvp](https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.rvp)
2. Ouvre **ReVanced Manager**.
3. Importe le fichier `.rvp` comme source/bundle de patchs local.
4. Selectionne Instagram, active **Instants : importer depuis la galerie**, puis patche l'APK.

Fallback si aucune release GitHub n'est encore disponible :
[`dist/patches-latest.rvp`](dist/patches-latest.rvp) ou
[`dist/patches-1.0.2.rvp`](dist/patches-1.0.2.rvp).

## Compatibilite testee

| Element | Version / valeur |
| --- | --- |
| Application cible | Instagram `com.instagram.android` |
| Version Instagram | `434.x` |
| ReVanced Patcher | `22.0.0` |
| Plugin Gradle ReVanced | `app.revanced.patches:1.0.0-dev.10` |
| ReVanced Manager | Manager v2 / patcher 22.x |
| Bundle local | `dist/patches-latest.rvp` |

## Utilisation

Apres installation, ouvre **Instants**, ecris une legende si besoin, puis appuie sur
le bouton de capture habituel. Le selecteur de galerie systeme s'ouvre. Choisis une
photo : elle est envoyee comme Instant.

Il n'y a pas de bouton dedie. Le declencheur de capture devient le selecteur de
galerie, ce qui evite de modifier l'UI Compose interne d'Instagram.

## Documentation

- [Installer avec ReVanced Manager](docs/INSTALL_MANAGER.md)
- [Construire le bundle localement](docs/BUILD.md)
- [Depannage](docs/TROUBLESHOOTING.md)
- [Historique des changements](CHANGELOG.md)

## Organisation du repo

```text
patches/src/.../instants/InstantsGalleryPatch.kt   patch ReVanced
extensions/instants/                               extension Android injectee
dist/patches-latest.rvp                            bundle local importable
docs/                                              installation, build, depannage
.github/workflows/release.yml                      publication des assets GitHub Release
```

## Fonctionnement technique

Instants n'a pas d'import galerie public, mais son pipeline interne accepte un
`Bitmap` via `QuickSnapCameraViewModel.A01/A02/A03`.

Le patch intercepte `A03` pour ouvrir le selecteur galerie, copie l'image choisie
dans le dossier prive d'Instagram sous `revanced_instants/`, puis reinjecte cette
image dans le pipeline QuickSnap. `A01` et `A02` servent de filet de securite pour
annuler un upload camera si Instagram tente de publier avant la reinjection.

Sources principales :

- [`InstantsGalleryPatch.kt`](patches/src/main/kotlin/app/revanced/patches/instagram/instants/InstantsGalleryPatch.kt)
- [`InstantsGallery.java`](extensions/instants/src/main/java/app/revanced/extension/instants/InstantsGallery.java)
