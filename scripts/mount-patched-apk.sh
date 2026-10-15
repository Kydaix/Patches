#!/system/bin/sh
# Monte un APK patché par-dessus une application installée depuis le Play Store (root).
#
# ReVanced Manager, en mode « Monter », réinstalle toujours l'APK source avant de
# monter. Pour une app distribuée en bundle (ChatGPT, Instagram...), l'APK source
# est un APK fusionné re-signé : Android refuse alors de l'installer par-dessus la
# version du Play Store (INSTALL_FAILED_UPDATE_INCOMPATIBLE).
#
# Ce script fait le montage sans cette réinstallation : il garde l'app du Play Store
# (signature d'origine) et monte l'APK patché sur son base.apk. Il crée le même
# module Magisk/KernelSU que Manager, pour que le montage soit refait à chaque
# démarrage tant que la version installée ne change pas.
#
# Usage : su -c "sh mount-patched-apk.sh /sdcard/Download/chatgpt-revanced.apk [paquet]"

APK="$1"
PKG="${2:-com.openai.chatgpt}"
DIR="/data/adb/modules/$PKG-revanced"

fail() {
    echo "Erreur : $1" >&2
    exit 1
}

[ "$(id -u)" = "0" ] || fail "lance ce script avec su."
[ -n "$APK" ] || fail "indique le chemin de l'APK patché."
[ -f "$APK" ] || fail "APK introuvable : $APK"

STOCK="$(pm path "$PKG" | grep base.apk | sed 's/^package://')"
[ -n "$STOCK" ] || fail "$PKG n'est pas installé. Installe d'abord la version d'origine (Play Store)."

VERSION="$(dumpsys package "$PKG" | grep -m 1 versionName | cut -d '=' -f 2)"
[ -n "$VERSION" ] || fail "version de $PKG introuvable."

echo "Application : $PKG $VERSION"
echo "base.apk    : $STOCK"
echo "Vérifie que l'APK patché a été construit depuis cette même version ($VERSION)."

# Démonte un éventuel montage précédent avant de remplacer l'APK.
if grep -qF " $STOCK " /proc/mounts; then
    umount -l "$STOCK" || fail "impossible de démonter l'ancien APK."
fi

mkdir -p "$DIR" || fail "impossible de créer $DIR."
cp "$APK" "$DIR/$PKG.apk" || fail "copie de l'APK impossible."
chmod 644 "$DIR/$PKG.apk"
chown system:system "$DIR/$PKG.apk"
chcon u:object_r:apk_data_file:s0 "$DIR/$PKG.apk"

cat > "$DIR/module.prop" <<EOF
id=$PKG-ReVanced
name=$PKG ReVanced
version=$VERSION
versionCode=0
author=ReVanced
description=Mounts the patched APK on top of the original one
EOF

# Même logique que le service.sh de ReVanced Manager : remonte au démarrage, sauf si
# le Play Store a mis l'application à jour entre-temps.
cat > "$DIR/service.sh" <<EOF
#!/system/bin/sh
DIR=\${0%/*}

package_name="$PKG"
version="$VERSION"

rm "\$DIR/log"

{

until [ "\$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 5

base_path="\$DIR/\$package_name.apk"
stock_path="\$(pm path "\$package_name" | grep base | sed 's/package://g')"
stock_version="\$(dumpsys package "\$package_name" | grep versionName | cut -d "=" -f2)"
stock_selinux_label="\$(ls -Z "\$stock_path" | cut -d " " -f1)"

chcon "\$stock_selinux_label" "\$base_path"

if mount | grep -q "\$stock_path" ; then
  echo "Not mounting as stock path is already mounted"
  exit 1
fi

if [ "\$version" != "\$stock_version" ]; then
  echo "Not mounting as versions don't match"
  exit 1
fi

if [ -z "\$stock_path" ]; then
  echo "Not mounting as app info could not be loaded"
  exit 1
fi

mount -o bind "\$base_path" "\$stock_path"

} >> "\$DIR/log"
EOF
chmod +x "$DIR/service.sh"

am force-stop "$PKG"
mount -o bind "$DIR/$PKG.apk" "$STOCK" || fail "le montage a échoué."

echo "OK : $PKG est monté avec l'APK patché."
echo "Désactive la mise à jour automatique de l'app dans le Play Store."
echo "Pour annuler : su -c \"umount -l '$STOCK'; rm -rf '$DIR'\""
