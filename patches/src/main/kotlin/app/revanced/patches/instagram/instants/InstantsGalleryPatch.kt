package app.revanced.patches.instagram.instants

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Instants : importer une photo de la galerie (avec sélecteur).
 *
 * "Instants" (interne "QuickSnap") force la caméra. On détourne le pipeline :
 *   com.instagram.quicksnap.camera.domain.QuickSnapCameraViewModel
 *     A03(Context, Bitmap, VM) en 434 / A04(...) en 438
 *         -> traitement de l'image capturée (point d'entrée)
 *     A01/A02(Context, Bitmap, VM, ...) -> upload (suspend)
 *
 * - Capture : on appelle l'extension qui lance le SÉLECTEUR
 *   photo système ; la capture caméra est annulée. Quand l'utilisateur choisit
 *   une image, l'extension la copie dans un fichier privé unique et re-déclenche
 *   la méthode de capture.
 * - A01/A02 : filet de sécurité si un upload caméra part pendant qu'un
 *   sélecteur ouvert par la capture attend encore l'utilisateur : on annule l'upload
 *   courant sans ouvrir un second sélecteur.
 * - A01/A02/capture (re-déclenchée) : on remplace le Bitmap par l'image choisie
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
// sélecteur ouvert par la capture est encore actif, on retourne Unit pour annuler cet
// upload caméra. Important : A01/A02 ne doivent pas ouvrir le sélecteur eux-mêmes,
// car ces méthodes peuvent être touchées pendant l'initialisation d'Instants.
private fun interceptUploadA01(unitSingletonField: String) = """
    move-object/from16 v0, p0
    move-object/from16 v1, p1
    move-object/from16 v2, p2
    move-object/from16 v3, p3
    move-object/from16 v4, p4
    move-object/from16 v5, p5
    move-wide/from16 v6, p7
    invoke-static/range { v0 .. v7 }, $EXT->requestPickForUploadA01(Landroid/content/Context;Landroid/graphics/Bitmap;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;J)Z
    move-result v0
    if-eqz v0, :revanced_upload_continue
    sget-object v0, $unitSingletonField
    return-object v0
    :revanced_upload_continue
    nop
"""

private fun interceptUploadA02(unitSingletonField: String) = """
    move-object/from16 v0, p0
    move-object/from16 v1, p1
    move-object/from16 v2, p2
    move-object/from16 v3, p3
    move-object/from16 v4, p4
    move/from16 v5, p6
    move-wide/from16 v6, p7
    invoke-static/range { v0 .. v7 }, $EXT->requestPickForUploadA02(Landroid/content/Context;Landroid/graphics/Bitmap;Ljava/lang/Object;Ljava/io/File;Ljava/lang/String;IJ)Z
    move-result v0
    if-eqz v0, :revanced_upload_continue
    sget-object v0, $unitSingletonField
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

// Intercepte la capture caméra pour ouvrir le sélecteur ; sinon swap.
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

private fun Method.isStatic() = AccessFlags.STATIC.isSet(accessFlags)

private val a01Fingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == VM_CLASS &&
            method.isStatic() &&
            method.returnType == "Ljava/lang/Object;" &&
            method.parameters.size == 8 &&
            method.parameters[0].type == "Landroid/content/Context;" &&
            method.parameters[1].type == "Landroid/graphics/Bitmap;" &&
            method.parameters[2].type == VM_CLASS &&
            method.parameters[4].type == "Ljava/lang/String;" &&
            method.parameters[5].type == "Ljava/lang/String;" &&
            method.parameters[7].type == "J"
    }
}

private val a02Fingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == VM_CLASS &&
            method.isStatic() &&
            method.returnType == "Ljava/lang/Object;" &&
            method.parameters.size == 8 &&
            method.parameters[0].type == "Landroid/content/Context;" &&
            method.parameters[1].type == "Landroid/graphics/Bitmap;" &&
            method.parameters[2].type == VM_CLASS &&
            method.parameters[3].type == "Ljava/io/File;" &&
            method.parameters[4].type == "Ljava/lang/String;" &&
            method.parameters[6].type == "I" &&
            method.parameters[7].type == "J"
    }
}

// Le nom est obfusqué et change entre versions (A03 en 434, A04 en 438).
// La signature complète est le point d'ancrage stable du pipeline de capture.
private val captureFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == VM_CLASS &&
            method.isStatic() &&
            method.returnType == "V" &&
            method.parameters.size == 3 &&
            method.parameters[0].type == "Landroid/content/Context;" &&
            method.parameters[1].type == "Landroid/graphics/Bitmap;" &&
            method.parameters[2].type == VM_CLASS
    }
}

/**
 * Récupère le singleton retourné naturellement par la fonction suspend.
 *
 * La classe Kotlin Unit est obfusquée par Instagram et change à chaque build
 * (`LX/07Eb;` en 434, `LX/4FC;` en 438). La lire dans la méthode cible évite
 * d'injecter une référence périmée et un VerifyError au chargement de la classe.
 */
private fun Method.returnSingletonField(): String {
    val instructions = implementation?.instructions?.toList()
        ?: throw PatchException("QuickSnap $name n'a pas d'implémentation")
    val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
    val returnRegister = (instructions.getOrNull(returnIndex) as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("Retour Object introuvable dans QuickSnap $name")

    val field = instructions
        .subList(0, returnIndex)
        .asReversed()
        .firstNotNullOfOrNull { instruction ->
            if (
                instruction.opcode != Opcode.SGET_OBJECT ||
                (instruction as? OneRegisterInstruction)?.registerA != returnRegister
            ) {
                return@firstNotNullOfOrNull null
            }

            (instruction as? ReferenceInstruction)?.reference as? FieldReference
        }
        ?.takeIf { it.definingClass == it.type }
        ?: throw PatchException("Singleton de retour introuvable dans QuickSnap $name")

    return "${field.definingClass}->${field.name}:${field.type}"
}

private val onActivityResultFingerprint = fingerprint {
    custom { method, classDef ->
        classDef.type == "Lcom/instagram/modal/ModalActivity;" &&
            method.name == "onActivityResult" &&
            !method.isStatic() &&
            method.returnType == "V" &&
            method.parameters.size == 3 &&
            method.parameters[0].type == "I" &&
            method.parameters[1].type == "I" &&
            method.parameters[2].type == "Landroid/content/Intent;"
    }
}

@Suppress("unused")
val instantsGalleryPatch = bytecodePatch(
    name = "Instants : importer depuis la galerie",
    description = "Ouvre un sélecteur de galerie lors de la capture d'un Instant et envoie la photo choisie.",
) {
    compatibleWith(
        "com.instagram.android"(
            "434.0.0.44.74",
            "438.0.0.28.88",
        ),
    )

    extendWith("extensions/instants.rve")

    execute {
        // Upload (suspend) : intercepter les uploads caméra précoces, puis swap.
        val a01Method = a01Fingerprint.method
        val a02Method = a02Fingerprint.method
        a01Method.addInstructions(0, interceptUploadA01(a01Method.returnSingletonField()) + swapBitmap)
        a02Method.addInstructions(0, interceptUploadA02(a02Method.returnSingletonField()) + swapBitmapAndFile)
        // Capture : intercepter -> sélecteur, sinon swap.
        captureFingerprint.method.addInstructions(0, interceptThenSwap)
        // Résultat du sélecteur -> extension.
        onActivityResultFingerprint.method.addInstructions(
            0,
            "invoke-static { p1, p2, p3 }, $EXT->onActivityResult(IILandroid/content/Intent;)V",
        )
    }
}
