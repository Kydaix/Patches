# Module KernelSU/Magisk pour monter l'Instagram patché

Monte un `base.apk` patché par-dessus l'Instagram installé, sans désinstaller
(comme le fait ReVanced Manager). Réversible. Testé sous KernelSU-Next.

## Construire le module

1. Patcher la base.apk **réellement installée** (voir README racine, Méthode A) →
   `ig_base_patched.apk`.
2. Assembler le module :
   ```
   com.instagram.android-revanced/
     module.prop            (ce dossier)
     service.sh             (ce dossier)
     com.instagram.android.apk   <- = ton ig_base_patched.apk renommé
   ```
3. Pousser et installer (root) :
   ```bash
   adb push com.instagram.android-revanced /sdcard/_igm
   adb shell su -c '
     M=/data/adb/modules/com.instagram.android-revanced
     mkdir -p $M && cp /sdcard/_igm/* $M/
     chmod 755 $M/service.sh; chmod 644 $M/com.instagram.android.apk $M/module.prop
     chown -R root:root $M; rm -rf /sdcard/_igm'
   adb reboot
   ```
4. Vérifier après reboot : `adb shell su -c 'cat /data/adb/modules/com.instagram.android-revanced/log'`
   puis comparer la taille/MD5 de la base.apk montée à ton apk patché.

## Important

- `version` dans `module.prop` **et** `service.sh` doit correspondre EXACTEMENT à
  la version Instagram installée (`dumpsys package com.instagram.android | grep versionName`),
  sinon le mount est refusé. Ici : `434.0.0.44.74`.
- L'apk doit être patché **depuis la base installée** (même contenu) pour éviter
  tout mismatch de ressources.

## Désinstaller

```bash
adb shell su -c 'rm -rf /data/adb/modules/com.instagram.android-revanced'
adb reboot
```
