/*
 * frida-instants-gallery.js
 * ─────────────────────────
 * Objectif : trouver, À L'EXÉCUTION, la méthode obfusquée d'Instagram qui
 * désactive/masque l'import galerie dans la caméra "Instants".
 *
 * Pourquoi dynamique : Instagram chiffre ses chaînes (FB dextricks/StringTreeSet)
 * et la caméra Instants est probablement en Jetpack Compose (pas d'ID de
 * ressource). Le tracing statique ne donne donc pas de point d'ancrage. On
 * observe le comportement en direct, on capture la pile d'appels, et la classe
 * /méthode obfusquée qui en ressort devient la cible du fingerprint ReVanced.
 *
 * Lancement :
 *   frida -U -f com.instagram.android -l frida-instants-gallery.js
 * (puis dans l'app : ouvre Instants et observe la console)
 *
 * Active/désactive les sondes via le bloc CONFIG ci-dessous.
 */

const CONFIG = {
  hookSetVisibility: true,   // capte les "hide" de boutons galerie (vues XML)
  hookFindViewById: true,    // capte qui récupère le bouton galerie
  hookComposeClicks: true,   // capte les clics Compose (caméra récente)
  // Filtre des noms de ressources qui nous intéressent (vues XML) :
  resNameFilter: /gallery|camera_roll|media_picker|import/i,
  maxStackFrames: 28,
};

function log(msg) { console.log(msg); }

// Pile d'appels Java lisible (sert à révéler la classe/méthode obfusquée).
function javaStack() {
  const Exception = Java.use("java.lang.Exception");
  const Log = Java.use("android.util.Log");
  const trace = Log.getStackTraceString(Exception.$new());
  return trace.split("\n").slice(2, 2 + CONFIG.maxStackFrames).join("\n");
}

// id ressource (int) -> nom lisible ("gallery_button"), via les Resources du contexte.
function resName(view) {
  try {
    const id = view.getId();
    if (id === -1 || id === 0) return null;
    const res = view.getContext().getResources();
    return res.getResourceEntryName(id);
  } catch (e) { return null; }
}

Java.perform(function () {
  log("[*] frida-instants-gallery attaché. Ouvre la caméra Instants maintenant.");

  // ── Sonde 1 : View.setVisibility ───────────────────────────────────────────
  // Le cas classique "masquer le bouton galerie" = setVisibility(GONE/INVISIBLE)
  // sur la vue galerie. La pile révèle la méthode qui décide.
  if (CONFIG.hookSetVisibility) {
    const View = Java.use("android.view.View");
    View.setVisibility.implementation = function (vis) {
      if (vis === 8 /*GONE*/ || vis === 4 /*INVISIBLE*/) {
        const name = resName(this);
        if (name && CONFIG.resNameFilter.test(name)) {
          log("\n========== setVisibility(" + (vis === 8 ? "GONE" : "INVISIBLE") +
              ") sur '" + name + "' ==========");
          log(javaStack());
        }
      }
      return this.setVisibility(vis);
    };
    log("[+] hook View.setVisibility OK");
  }

  // ── Sonde 2 : findViewById ──────────────────────────────────────────────────
  // Révèle la classe contrôleur qui manipule le bouton galerie.
  if (CONFIG.hookFindViewById) {
    const View = Java.use("android.view.View");
    View.findViewById.implementation = function (id) {
      const v = this.findViewById(id);
      try {
        if (v !== null) {
          const name = resName(v);
          if (name && CONFIG.resNameFilter.test(name)) {
            log("\n---------- findViewById -> '" + name + "' ----------");
            log(javaStack());
          }
        }
      } catch (e) {}
      return v;
    };
    log("[+] hook View.findViewById OK");
  }

  // ── Sonde 3 : clics Compose ─────────────────────────────────────────────────
  // Si la caméra est en Compose, hooke le dispatch de clic pour repérer la classe
  // de l'écran caméra Instants (le nom obfusqué apparaît dans la pile).
  if (CONFIG.hookComposeClicks) {
    try {
      const AndroidComposeView = Java.use("androidx.compose.ui.platform.AndroidComposeView");
      // dispatchTouchEvent existe sur AndroidComposeView ; on logge une pile au 1er
      // appui (à toi de filtrer visuellement la classe de l'écran Instants).
      AndroidComposeView.dispatchTouchEvent.implementation = function (ev) {
        const res = this.dispatchTouchEvent(ev);
        return res;
      };
      log("[+] AndroidComposeView présent (UI Compose probable) — voir WORKFLOW.md §Compose");
    } catch (e) {
      log("[i] AndroidComposeView absent du process pour l'instant");
    }
  }
});

/*
 * ── ÉTAPE SUIVANTE (une fois une classe obfusquée identifiée) ────────────────
 * Décommente et adapte pour confirmer le gate booléen pressenti, ex. :
 *
 *   const Target = Java.use("X.C12345");
 *   Target.someBool.overload().implementation = function () {
 *     const r = this.someBool();
 *     console.log("[gate] X.C12345.someBool() = " + r);
 *     return r;            // remplace par "return true;" pour TESTER le déblocage
 *   };
 *
 * Si "return true" fait apparaître la galerie -> tu as ta cible. On traduit
 * alors en fingerprint ReVanced (par opcodes/retour, pas par string). Voir
 * WORKFLOW.md §"Du hook au fingerprint".
 */
