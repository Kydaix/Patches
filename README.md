# Kydaix ReVanced Patches

Patches [ReVanced](https://revanced.app) personnels :

| Application | Patch | Rôle |
| --- | --- | --- |
| Instagram (`com.instagram.android`) | **Instants : importer depuis la galerie** | Envoie une photo de la galerie dans **Instants** au lieu de la capture caméra imposée. |
| ChatGPT (`com.openai.chatgpt`) | **Localisation : États-Unis** | Fait croire à l'app que l'appareil est aux États-Unis, sans VPN. Toutes versions. Voir [docs/CHATGPT_REGION.md](docs/CHATGPT_REGION.md). |

## Installation rapide avec mises a jour

Dans **ReVanced Manager v2**, ajoute cette URL comme source distante de patches :

```text
https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.json
```

Garde **Auto update** active. Manager telecharge ensuite automatiquement le dernier
bundle `.rvp` reference par cette source.

Ensuite, selectionne Instagram, active **Instants : importer depuis la galerie**, puis
patche l'APK. Pour ChatGPT, selectionne ChatGPT et active **Localisation : États-Unis**.

## Import local de secours

Si la source distante ne fonctionne pas, telecharge et importe manuellement ce bundle :

[revanced-instagram-patches.rvp](https://github.com/Kydaix/Patches/releases/latest/download/revanced-instagram-patches.rvp)

Fallback si aucune release GitHub n'est encore disponible :
[`dist/patches-latest.rvp`](dist/patches-latest.rvp) ou
[`dist/patches-1.1.0.rvp`](dist/patches-1.1.0.rvp).

## ChatGPT : localisation aux États-Unis

Le patch remplace, dans tout le bytecode de ChatGPT, les appels aux API système qui
révèlent le pays (locale, pays de la SIM et du réseau, opérateur, fuseau horaire en
option) par des valeurs américaines. La langue de l'interface est conservée
(`fr-FR` devient `fr-US`). Comme il ne vise que des API publiques Android/Java, il ne
dépend d'aucune classe obfusquée et reste compatible avec toutes les versions.

Limite : l'adresse IP ne change pas. Une fonctionnalité que les serveurs d'OpenAI
réservent selon l'IP ou le pays du compte restera bloquée. Détails et options :
[docs/CHATGPT_REGION.md](docs/CHATGPT_REGION.md).

## Compatibilite testee (Instagram)

| Element | Version / valeur |
| --- | --- |
| Application cible | Instagram `com.instagram.android` |
| Version Instagram recommandée | `438.0.0.28.88` |
| Variante testée | `arm64-v8a` / `xxxhdpi` (`versionCode 384409708`) |
| Compatibilité conservée | `434.0.0.44.74` (`384009971`) |
| ReVanced Patcher | `22.0.0` |
| Plugin Gradle ReVanced | `app.revanced.patches:1.0.0-dev.10` |
| ReVanced Manager | Manager v2 / patcher 22.x |
| Bundle local | `dist/patches-latest.rvp` |
| Source distante | `dist/patches.json` |

## Utilisation (Instagram)

Apres installation, ouvre **Instants**, ecris une legende si besoin, puis appuie sur
le bouton de capture habituel. Le selecteur de galerie systeme s'ouvre. Choisis une
photo : elle est envoyee comme Instant.

Il n'y a pas de bouton dedie. Le declencheur de capture devient le selecteur de
galerie, ce qui evite de modifier l'UI Compose interne d'Instagram.

## Documentation

- [Installer avec ReVanced Manager](docs/INSTALL_MANAGER.md)
- [Construire le bundle localement](docs/BUILD.md)
- [Organisation du repo](docs/REPO_MODEL.md)
- [Depannage](docs/TROUBLESHOOTING.md)
- [ChatGPT : localisation aux États-Unis](docs/CHATGPT_REGION.md)
- [Historique des changements](CHANGELOG.md)

## Organisation du repo

```text
patches/src/.../instants/InstantsGalleryPatch.kt   patch ReVanced Instagram
patches/src/.../chatgpt/region/SpoofRegionPatch.kt patch ReVanced ChatGPT
extensions/instants/                               extension Android Instagram
extensions/chatgpt/                                extension Android ChatGPT
dist/patches.json                                  source distante Manager
dist/patches-latest.rvp                            bundle local importable
docs/                                              installation, build, depannage
.github/workflows/release.yml                      publication des assets GitHub Release
.github/workflows/build.yml                        build de test des branches (artefact .rvp)
```

## Fonctionnement technique (Instagram)

Instants n'a pas d'import galerie public, mais son pipeline interne accepte un
`Bitmap` via les méthodes d'upload `A01/A02` et la méthode de capture de
`QuickSnapCameraViewModel`.

Le patch repère la méthode de capture par sa signature (`A03` en Instagram 434,
`A04` en 438) pour ouvrir le selecteur galerie, copie l'image choisie dans le
dossier prive d'Instagram sous `revanced_instants/`, puis reinjecte cette image
dans le pipeline QuickSnap. `A01` et `A02` capturent l'upload camera annule pendant
le selecteur, puis le relancent avec la photo choisie. Les classes Kotlin obfusquées
utilisées pour `Unit` et le contexte de coroutine sont également découvertes ou
émulées dynamiquement.

Sources principales :

- [`InstantsGalleryPatch.kt`](patches/src/main/kotlin/app/revanced/patches/instagram/instants/InstantsGalleryPatch.kt)
- [`InstantsGallery.java`](extensions/instants/src/main/java/app/revanced/extension/instants/InstantsGallery.java)
