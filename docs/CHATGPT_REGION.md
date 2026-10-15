# ChatGPT : localisation aux États-Unis

Patch **Localisation : États-Unis** pour ChatGPT (`com.openai.chatgpt`).

Il fait croire à l'application que l'appareil se trouve aux États-Unis, sans VPN,
pour tenter de débloquer les fonctionnalités dont l'affichage dépend du pays détecté
sur le téléphone.

## Ce que l'application voit

| Information lue par ChatGPT | Valeur réelle (exemple) | Valeur après patch |
| --- | --- | --- |
| `Locale.getDefault()` | `fr-FR` | `fr-US` |
| `LocaleList.getDefault()` / `getAdjustedDefault()` | `[fr-FR, en-GB]` | `[fr-US, en-US]` |
| Pays de la SIM (`getSimCountryIso`) | `fr` | `us` |
| Pays du réseau (`getNetworkCountryIso`) | `fr` | `us` |
| Opérateur (`getSimOperator` / `getNetworkOperator`) | `20801` | `310260` |
| Nom de l'opérateur | `Orange F` | `T-Mobile` |
| Fuseau horaire (option, désactivé par défaut) | `Europe/Paris` | `America/New_York`… |

La langue est conservée : l'interface reste en français, seule la région change.
Le chinois garde son écriture (`zh-TW` devient `zh-Hant-US`).

## Options

| Option | Défaut | Rôle |
| --- | --- | --- |
| **Pays** | `US` | Code ISO à deux lettres. Préréglages : US, CA, GB, AU, JP. |
| **Conserver la langue** | activé | Désactivé, la locale présentée devient `en-<pays>`. |
| **Fuseau horaire** | désactivé | Fuseau IANA présenté à l'app. Les heures affichées et les tâches planifiées l'utiliseront aussi. |

## Compatibilité

Le patch est déclaré compatible avec **toutes les versions** de ChatGPT : Manager
n'affiche aucune contrainte de version.

ChatGPT est obfusqué et ses classes internes changent à chaque mise à jour. Le patch
ne vise donc aucune classe de l'application : il parcourt tout le bytecode et
remplace chaque appel aux API publiques Android/Java listées ci-dessus par un appel
vers l'extension `RegionSpoof`. Ces API ne changent pas d'une version de ChatGPT à
l'autre.

Le remplacement est fait instruction par instruction, avec les mêmes registres :

```smali
# avant
invoke-virtual {v3}, Landroid/telephony/TelephonyManager;->getSimCountryIso()Ljava/lang/String;
# après
invoke-static {v3}, Lapp/revanced/extension/chatgpt/RegionSpoof;->getSimCountryIso(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
```

Les classes AndroidX (`androidx.*`) ne sont pas modifiées, pour ne pas perturber la
gestion de la langue de l'interface. L'extension elle-même non plus, puisqu'elle doit
lire les vraies valeurs.

## Limite importante : la vérification côté serveur

Le patch ne change que **ce que l'application lit sur le téléphone**. Il ne change
pas l'adresse IP : les serveurs d'OpenAI voient toujours une connexion française.

- Si une fonctionnalité est masquée par l'application selon la langue, la SIM ou le
  réseau, le patch peut la débloquer.
- Si une fonctionnalité est activée par le serveur selon l'IP ou le pays du compte
  (ce qui est le cas de la plupart des déploiements « US only » d'OpenAI), le patch
  ne suffit pas : seule une connexion qui sort réellement aux États-Unis (VPN ou
  proxy) peut le faire.

Le patch ne garantit donc pas l'accès à une fonctionnalité donnée. Il élimine
seulement les signaux de localisation que le téléphone envoie à ChatGPT.

## Vérifier que le patch est actif

Le journal de patch (ReVanced Manager ou CLI) contient une ligne comme
`N appels système redirigés vers RegionSpoof (US)`. Si `N` vaut 0, ChatGPT ne lit
aucune de ces API et le patch n'aura aucun effet sur cette version.

## Installation avec root (montage)

ChatGPT est distribué en bundle (APK splittés). Deux contraintes de ReVanced Manager
empêchent son mode **Monter** de fonctionner tel quel :

- Manager refuse l'app installée depuis le Play Store comme source, car c'est un
  bundle : il faut lui donner un APK fusionné (AntiSplit M, APKEditor) ;
- en mode **Monter**, Manager réinstalle toujours cet APK source avant de monter.
  L'APK fusionné étant re-signé, Android refuse de l'installer par-dessus la version
  du Play Store : `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`.

Le script [`scripts/mount-patched-apk.sh`](../scripts/mount-patched-apk.sh) fait le
montage sans cette réinstallation, en gardant l'app du Play Store et sa signature
d'origine (la connexion Google continue de fonctionner) :

1. Installe ChatGPT depuis le Play Store, dans la version de l'APK fusionné.
2. Patche l'APK fusionné dans Manager, puis enregistre l'APK patché au lieu de
   l'installer (bouton d'export), par exemple dans `/sdcard/Download/chatgpt-revanced.apk`.
3. Lance, par exemple depuis Termux :

   ```sh
   su -c "sh /sdcard/Download/mount-patched-apk.sh /sdcard/Download/chatgpt-revanced.apk"
   ```

4. Désactive la mise à jour automatique de ChatGPT dans le Play Store.

Le script crée le même module (`/data/adb/modules/com.openai.chatgpt-revanced`) que
Manager : le montage est refait à chaque démarrage, sauf si la version installée a
changé. Pour une nouvelle version de ChatGPT, mets l'app à jour, repatche l'APK
correspondant et relance le script.
