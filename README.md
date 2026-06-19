# Instagram ReVanced Patches

Projet de patchs [ReVanced](https://revanced.app) personnels ciblant l'application
**Instagram** (`com.instagram.android`). Le build produit un fichier **`.rvp`**
que l'on importe comme *source de patchs* dans **ReVanced Manager** sur le
smartphone.

> Basé sur le [template officiel](https://github.com/ReVanced/revanced-patches-template)
> (ReVanced Patcher `21.0.0`, plugin Gradle `app.revanced.patches:1.0.0-dev.5`).

---

## Prérequis

| Outil | Pourquoi | État |
|-------|----------|------|
| **JDK 17+** | Compiler le projet Gradle/Kotlin | ✅ installé (Microsoft OpenJDK 17) |
| **Token GitHub** (`read:packages`) | Les dépendances (patcher + plugin) sont sur **GitHub Packages**, qui exige une authentification même en lecture | ⚠️ à fournir |
| **Android SDK** | Uniquement pour compiler le module `extensions/` (code Java/Kotlin injecté dans l'APK) | ⚠️ requis tant que le module `extensions/` existe |

### 1. Token GitHub (obligatoire)

Crée un *Personal Access Token (classic)* avec le scope **`read:packages`** :
<https://github.com/settings/tokens>

Place-le dans le fichier Gradle **global** (jamais commité) :
`C:\Users\Administrator\.gradle\gradle.properties`

```properties
gpr.user=ton_pseudo_github
gpr.key=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

(Voir `gradle.properties.example`.) Alternative : les variables d'environnement
`GITHUB_ACTOR` et `GITHUB_TOKEN`.

---

## Construire le `.rvp`

```powershell
# JDK déjà sur le PATH après installation ; sinon :
#   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"

.\gradlew.bat build
```

Le bundle est généré dans `patches\build\libs\` sous la forme
`revanced-patches-instagram-<version>.rvp`.

---

## Importer dans ReVanced Manager

1. Transfère le fichier `.rvp` sur le téléphone.
2. ReVanced Manager → réglages → **Patch sources** → ajoute le `.rvp` local.
3. Sélectionne l'APK Instagram, choisis les patchs, applique.

---

## Structure

```
patches/                         ← le sous-projet des patchs
  build.gradle.kts               ← métadonnées (bloc about)
  api/patches.api                ← API publique figée (validation binaire)
  src/main/kotlin/app/revanced/patches/instagram/
    HelloWorldPatch.kt           ← patch de démarrage (no-op, à compléter)
extensions/extension/            ← code Java/Kotlin injecté dans l'APK (optionnel)
gradle/libs.versions.toml        ← versions (patcher, smali)
settings.gradle.kts              ← plugin + dépôt GitHub Packages
```

---

## Écrire un vrai patch

Un patch suit toujours deux étapes :

1. **Fingerprint** — localiser une méthode dans le bytecode *obfusqué*
   d'Instagram (par chaîne, opcodes, signature de méthode…). C'est l'étape de
   reverse-engineering : il faut décompiler l'APK (ex. avec `jadx`) pour trouver
   les points d'ancrage.
2. **`execute { }`** — récupérer la méthode via le fingerprint et injecter du
   smali (`addInstructions`, `replaceInstruction`, `addInstructionsWithLabels`…),
   éventuellement en appelant du code depuis `extensions/`.

Voir `HelloWorldPatch.kt` pour le squelette commenté.

Après modification de l'API publique d'un patch, régénère la validation binaire :

```powershell
.\gradlew.bat apiDump
```

---

## Note

ReVanced n'a pas de patchs Instagram officiels — ce dépôt est un travail
personnel. Le patcher est générique et peut cibler n'importe quel APK ; toute la
difficulté est dans les fingerprints, à refaire à chaque grosse mise à jour
d'Instagram (le code est ré-obfusqué).
