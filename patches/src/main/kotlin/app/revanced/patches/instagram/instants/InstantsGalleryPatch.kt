package app.revanced.patches.instagram.instants

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch

/**
 * Instants : importer une photo de la galerie (avec sélecteur).
 *
 * "Instants" (interne "QuickSnap") force la caméra. On détourne le pipeline :
 *   com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel
 *     A03(Context, Bitmap, VM)  -> traitement de l'image capturée (point d'entrée)
 *     A01/A02(Context, Bitmap, VM, ...) -> upload (suspend)
 *
 * - A03 : à la capture caméra, on appelle l'extension qui lance le SÉLECTEUR
 *   photo système ; la capture caméra est annulée. Quand l'utilisateur choisit
 *   une image, l'extension la copie dans IMAGE_PATH et re-déclenche A03.
 * - A01/A02/A03 (re-déclenché) : on remplace le Bitmap par l'image de IMAGE_PATH
 *   (lecture fiable depuis le dossier privé d'Instagram). Sécurité null : si
 *   l'image manque, on garde le Bitmap d'origine.
 * - ModalActivity.onActivityResult : transmet le résultat du sélecteur à l'extension.
 */

private const val IMAGE_PATH = "/sdcard/Android/data/com.instagram.android/files/instant.jpg"
private const val VM_CLASS = "Lcom/instagram/quicksnap/camera/domain/QuickSnapCameraViewModel;"
private const val EXT = "Lapp/revanced/extension/instants/InstantsGallery;"

// Remplace p1 (Bitmap) par l'image de IMAGE_PATH si décodable, sinon garde l'original.
private val swapBitmap = """
    const-string v0, "$IMAGE_PATH"
    invoke-static { v0 }, Landroid/graphics/BitmapFactory;->decodeFile(Ljava/lang/String;)Landroid/graphics/Bitmap;
    move-result-object v0
    if-eqz v0, :revanced_keep
    move-object/from16 p1, v0
    :revanced_keep
    nop
"""

// A03 : intercepte la capture caméra pour ouvrir le sélecteur ; sinon swap.
private val interceptThenSwap = """
    invoke-static { }, $EXT->shouldIntercept()Z
    move-result v0
    if-eqz v0, :revanced_swap
    invoke-static { p0, p2 }, $EXT->startPick(Landroid/content/Context;Ljava/lang/Object;)V
    return-void
    :revanced_swap
    const-string v0, "$IMAGE_PATH"
    invoke-static { v0 }, Landroid/graphics/BitmapFactory;->decodeFile(Ljava/lang/String;)Landroid/graphics/Bitmap;
    move-result-object v0
    if-eqz v0, :revanced_keep
    move-object/from16 p1, v0
    :revanced_keep
    nop
"""

private fun quickSnapMethod(name: String) = fingerprint {
    custom { method, classDef ->
        classDef.type == VM_CLASS &&
            method.name == name &&
            method.parameters.size >= 2 &&
            method.parameters[1].type == "Landroid/graphics/Bitmap;"
    }
}

private val a01Fingerprint = quickSnapMethod("A01")
private val a02Fingerprint = quickSnapMethod("A02")
private val a03Fingerprint = quickSnapMethod("A03")

private val onActivityResultFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/instagram/modal/ModalActivity;" &&
            method.name == "onActivityResult" &&
            method.parameters.size == 3
    }
}

@Suppress("unused")
val instantsGalleryPatch = bytecodePatch(
    name = "Instants : importer depuis la galerie",
    description = "Ouvre un sélecteur de galerie lors de la capture d'un Instant et envoie la photo choisie.",
) {
    compatibleWith("com.instagram.android")

    extendWith("extensions/instants.rve")

    execute {
        // Upload (suspend) : remplacer le bitmap par l'image choisie.
        a01Fingerprint.method.addInstructions(0, swapBitmap)
        a02Fingerprint.method.addInstructions(0, swapBitmap)
        // Capture : intercepter -> sélecteur, sinon swap.
        a03Fingerprint.method.addInstructions(0, interceptThenSwap)
        // Résultat du sélecteur -> extension.
        onActivityResultFingerprint.method.addInstructions(
            0,
            "invoke-static { p1, p2, p3 }, $EXT->onActivityResult(IILandroid/content/Intent;)V",
        )
    }
}
