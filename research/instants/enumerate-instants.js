/*
 * enumerate-instants.js — liste les classes chargées qui sentent la caméra/Instants.
 * Lance APRÈS avoir ouvert l'écran Instants (les classes restent chargées en mémoire).
 *   frida -U -n Instagram -l enumerate-instants.js
 */
Java.perform(function () {
  console.log("[*] énumération des classes chargées...");
  const hitsInstant = [];
  const hitsCam = [];
  const reInstant = /instant/i;
  // indices caméra/capture/galerie qui survivent parfois à l'obfuscation
  const reCam = /(capture|camera|gallery|medialibrary|mediapicker|cameracore|clipscamera|reelscamera|composer)/i;
  Java.enumerateLoadedClasses({
    onMatch: function (name) {
      if (reInstant.test(name)) hitsInstant.push(name);
      else if (reCam.test(name) && !/^android|^androidx|^kotlin|^com\.google|^java/.test(name)) hitsCam.push(name);
    },
    onComplete: function () {
      console.log("\n===== classes contenant 'instant' (" + hitsInstant.length + ") =====");
      hitsInstant.sort().forEach(function (n) { console.log("  " + n); });
      console.log("\n===== classes app caméra/galerie/capture (" + hitsCam.length + ") =====");
      hitsCam.sort().slice(0, 200).forEach(function (n) { console.log("  " + n); });
      console.log("\n[*] terminé.");
    },
  });
});
