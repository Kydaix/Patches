package app.revanced.patches.instagram.instants

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch

/**
 * Instants : envoyer une photo de la galerie.
 *
 * "Instants" (nom de code interne "QuickSnap") force la caméra in-app : aucun
 * import galerie n'est implémenté. Mais le pipeline d'envoi accepte un Bitmap —
 *   com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel
 *     A03(Context, Bitmap, VM)                       -> vignette 80x80
 *     A01(Context, Bitmap, VM, .., String, String, .., long)  -> upload (suspend)
 *     A02(Context, Bitmap, VM, File, String, .., int, long)   -> upload (suspend)
 * Dans les 3, le Bitmap est le paramètre n°2 (registre p1, méthodes statiques).
 *
 * On injecte à l'ENTRÉE de chaque méthode du smali qui remplace ce Bitmap par
 * une image décodée depuis un chemin fixe sur le téléphone. Pas besoin d'UI ni
 * d'extension : 3 instructions, p1 réutilisé comme scratch.
 *
 * UX : déposer la photo à envoyer dans IMAGE_PATH, puis capturer dans Instants.
 *
 * ⚠️ Reste l'inconnue serveur : Instagram pourrait rejeter une image non-caméra.
 *    Seul le test sur l'appareil tranchera.
 */

private const val IMAGE_PATH = "/sdcard/Pictures/instant.jpg"
private const val TARGET_CLASS = "Lcom/instagram/quicksnap/camera/domain/QuickSnapCameraViewModel;"

// Remplace le Bitmap (p1) par l'image de IMAGE_PATH. p1 sert d'abord de scratch
// pour la String, puis reçoit le Bitmap décodé.
// invoke-static/range pour supporter p1 même quand c'est un registre haut (>v15),
// ce qui est le cas dans A01/A02 (méthodes à nombreux paramètres).
private val replaceBitmapSmali = """
    const-string p1, "$IMAGE_PATH"
    invoke-static/range { p1 .. p1 }, Landroid/graphics/BitmapFactory;->decodeFile(Ljava/lang/String;)Landroid/graphics/Bitmap;
    move-result-object p1
"""

private fun quickSnapMethod(name: String) = fingerprint {
    custom { method, classDef ->
        classDef.type == TARGET_CLASS &&
            method.name == name &&
            method.parameters.size >= 2 &&
            method.parameters[1].type == "Landroid/graphics/Bitmap;"
    }
}

private val a01Fingerprint = quickSnapMethod("A01")
private val a02Fingerprint = quickSnapMethod("A02")
private val a03Fingerprint = quickSnapMethod("A03")

@Suppress("unused")
val instantsGalleryPatch = bytecodePatch(
    name = "Instants : envoyer depuis la galerie",
    description = "Remplace l'image capturée par la photo $IMAGE_PATH lors de l'envoi d'un Instant.",
) {
    compatibleWith("com.instagram.android")

    execute {
        listOf(a01Fingerprint, a02Fingerprint, a03Fingerprint).forEach { fp ->
            fp.method.addInstructions(0, replaceBitmapSmali)
        }
    }
}
