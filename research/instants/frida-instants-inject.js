/*
 * frida-instants-inject.js — OPTION A (test)
 * ──────────────────────────────────────────
 * Remplace le Bitmap capturé par la caméra Instants ("QuickSnap") par une PHOTO
 * DE LA GALERIE, au moment de l'envoi. But : prouver que le pipeline accepte une
 * image non-caméra, et tester si le serveur l'accepte aussi.
 *
 * Cible : com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel
 *   - A03(Context, Bitmap, VM)                               <- traitement image capturée (sûr)
 *   - A01(Context, Bitmap, VM, .., String, String, .., long) <- upload (suspend, risqué)
 *   - A02(Context, Bitmap, VM, File, String, .., int, long)  <- upload (suspend, risqué)
 * Dans les 3, le Bitmap est l'argument n°2 (index 1).
 *
 * Le script ATTEND que l'écran Instants charge la classe (réessaie en boucle),
 * donc on peut l'attacher avant ou après l'ouverture d'Instants.
 *
 * Usage : frida -U -n Instagram -l frida-instants-inject.js
 * Puis : ouvre Instants, écris la légende, CAPTURE, envoie.
 */

const CONFIG = {
  OVERRIDE_PATH: null,
  SCAN_DIRS: ["/sdcard/DCIM/Camera", "/sdcard/Pictures", "/sdcard/DCIM", "/sdcard/Download"],
  // A01/A02 sont des fonctions suspend (coroutines) : risquées à ré-invoquer.
  // On commence par A03 seul (sûr). Passe à true si A03 ne suffit pas.
  HOOK_SUSPEND: false,
  TARGET: "com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel",
};

function newestImage() {
  if (CONFIG.OVERRIDE_PATH) return CONFIG.OVERRIDE_PATH;
  const File = Java.use("java.io.File");
  let best = null, bestT = -1;
  CONFIG.SCAN_DIRS.forEach(function (d) {
    try {
      const arr = File.$new(d).listFiles();
      if (!arr) return;
      for (let i = 0; i < arr.length; i++) {
        const fi = arr[i];
        const name = "" + fi.getName();
        if (/\.(jpe?g|png)$/i.test(name)) {
          const t = fi.lastModified();
          if (t > bestT) { bestT = t; best = "" + fi.getAbsolutePath(); }
        }
      }
    } catch (e) {}
  });
  return best;
}

function loadBitmap() {
  const path = newestImage();
  if (!path) { console.log("[!] aucune image trouvée dans " + CONFIG.SCAN_DIRS.join(", ")); return null; }
  const bmp = Java.use("android.graphics.BitmapFactory").decodeFile(path);
  if (bmp === null) { console.log("[!] échec decode: " + path); return null; }
  console.log("[+] photo injectée: " + path + " (" + bmp.getWidth() + "x" + bmp.getHeight() + ")");
  return bmp;
}

// Vérifie sans risque si la classe cible est chargée (évite le "illegal instruction"
// de Java.use sur une classe absente).
function classLoaded(name) {
  let found = false;
  Java.enumerateLoadedClassesSync().some(function (n) {
    if (n === name) { found = true; return true; }
    return false;
  });
  return found;
}

function installHooks() {
  const VM = Java.use(CONFIG.TARGET);
  let hooked = 0;
  VM.class.getDeclaredMethods().forEach(function (m) {
    const name = m.getName();
    if (name !== "A01" && name !== "A02" && name !== "A03") return;
    if ((name === "A01" || name === "A02") && !CONFIG.HOOK_SUSPEND) return;
    const ptypes = m.getParameterTypes().map(function (t) { return t.getName(); });
    if (ptypes[1] !== "android.graphics.Bitmap") return;

    const overload = VM[name].overload.apply(VM[name], ptypes);
    overload.implementation = function () {
      console.log("\n========== capture Instants -> " + name + "() interceptée ==========");
      const args = [].slice.call(arguments);
      const replacement = loadBitmap();
      if (replacement !== null) {
        args[1] = replacement;
        console.log("[*] Bitmap caméra remplacé par la photo galerie.");
      } else {
        console.log("[*] pas de remplacement -> capture normale.");
      }
      return overload.apply(this, args);
    };
    hooked++;
    console.log("[+] hook QuickSnapCameraViewModel." + name + "(" + ptypes.join(", ") + ")");
  });
  console.log(hooked > 0
    ? "[*] hooks posés. Écris la légende, CAPTURE puis envoie."
    : "[!] aucun overload Bitmap trouvé — signatures à revoir.");
}

Java.perform(function () {
  let done = false;
  function tick() {
    if (done) return;
    Java.perform(function () {
      if (!classLoaded(CONFIG.TARGET)) return;     // pas encore ouvert
      try { installHooks(); done = true; }
      catch (e) { console.log("[!] échec installHooks: " + e); }
    });
  }
  const timer = setInterval(function () { if (done) clearInterval(timer); else tick(); }, 1500);
  tick();
  console.log("[*] En attente de l'écran Instants… ouvre-le sur le téléphone.");
});
