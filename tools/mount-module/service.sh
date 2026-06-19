#!/system/bin/sh
DIR=${0%/*}

package_name="com.instagram.android"
version="434.0.0.44.74"

rm "$DIR/log"
{
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 5

base_path="$DIR/$package_name.apk"
stock_path="$(pm path "$package_name" | grep base | sed 's/package://g')"
stock_version="$(dumpsys package "$package_name" | grep versionName | cut -d "=" -f2)"

echo "base_path: $base_path"
echo "stock_path: $stock_path"
echo "base_version: $version"
echo "stock_version: $stock_version"

mount | grep -q "$stock_path" && { echo "already mounted"; exit 1; }
[ "$version" != "$stock_version" ] && { echo "version mismatch"; exit 1; }
[ -z "$stock_path" ] && { echo "app not found"; exit 1; }

mount -o bind "$base_path" "$stock_path"
} >> "$DIR/log"
