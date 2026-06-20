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
 *   une image, l'extension la copie dans un fichier privé unique et re-déclenche
 *   A03.
 * - A01/A02 : filet de sécurité si un upload caméra part pendant qu'un
 *   sélecteur ouvert par A03 attend encore l'utilisateur : on annule l'upload
 *   courant sans ouvrir un second sélecteur.
 * - A01/A02/A03 (re-déclenché) : on remplace le Bitmap par l'image choisie
 *   depuis l'extension. Sécurité null : si l'image manque, on garde l'original.
 * - ModalActivity.onActivityResult : transmet le résultat du sélecteur à l'extension.
 */

private const val VM_CLASS = "Lcom/instagram/quicksnap/camera/domain/QuickSnapCameraViewModel;"
private const val EXT = "Lapp/revanced/extension/instants/InstantsGallery;"

// Remplace p1 (Bitmap) par l'image choisie si décodable, sinon garde l'original.
private val swapBitmap = """
    invoke-static { }, $EXT->imageBitmap()Landroid/graphics/Bitmap;
    move-result-object v0
    if-eqz v0, :revanced_keep
    move-object/from16 p1, v0
    :revanced_keep
    nop
"""

// A01/A02 retournent Object (suspend). Si un upload caméra démarre pendant que le
// sélecteur ouvert par A03 est encore actif, on retourne Unit pour annuler cet
// upload caméra. Important : A01/A02 ne doivent pas ouvrir le sélecteur eux-mêmes,
// car ces méthodes peuvent être touchées pendant l'initialisation d'Instants.
private val interceptUpload = """
    invoke-static { p0, p2 }, $EXT->requestPickForUpload(Landroid/content/Context;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :revanced_upload_continue
    sget-object v0, LX/07Eb;->A00:LX/07Eb;
    return-object v0
    :revanced_upload_continue
    nop
"""

// A02 : swap du Bitmap (p1) ET du File (p3). Le vrai upload construit un Medium
// depuis le File (chemin runtime), pas seulement depuis le Bitmap.
private val swapBitmapAndFile = """
    invoke-static { }, $EXT->imageBitmap()Landroid/graphics/Bitmap;
    move-result-object v0
    if-eqz v0, :revanced_keep_bmp
    move-object/from16 p1, v0
    :revanced_keep_bmp
    invoke-static { }, $EXT->imageFile()Ljava/io/File;
    move-result-object v0
    if-eqz v0, :revanced_keep_file
    move-object/from16 p3, v0
    :revanced_keep_file
    nop
"""

// A03 : intercepte la capture caméra pour ouvrir le sélecteur ; sinon swap.
private val interceptThenSwap = """
    invoke-static { p0, p1, p2 }, $EXT->requestPickForCapture(Landroid/content/Context;Landroid/graphics/Bitmap;Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :revanced_swap
    return-void
    :revanced_swap
    invoke-static { }, $EXT->imageBitmap()Landroid/graphics/Bitmap;
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
        // Upload (suspend) : intercepter les uploads caméra précoces, puis swap.
        a01Fingerprint.method.addInstructions(0, interceptUpload + swapBitmap)
        a02Fingerprint.method.addInstructions(0, interceptUpload + swapBitmapAndFile)
        // Capture : intercepter -> sélecteur, sinon swap.
        a03Fingerprint.method.addInstructions(0, interceptThenSwap)
        // Résultat du sélecteur -> extension.
        onActivityResultFingerprint.method.addInstructions(
            0,
            "invoke-static { p1, p2, p3 }, $EXT->onActivityResult(IILandroid/content/Intent;)V",
        )
    }
}
