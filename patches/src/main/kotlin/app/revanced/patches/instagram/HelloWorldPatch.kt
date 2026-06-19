package app.revanced.patches.instagram

import app.revanced.patcher.patch.bytecodePatch

/**
 * Patch de démarrage pour Instagram.
 *
 * Il ne modifie rien pour l'instant : c'est le squelette à partir duquel
 * construire de vrais patchs. Le flux de travail est toujours le même :
 *   1. Déclarer un (ou des) Fingerprint pour localiser une méthode dans le
 *      bytecode obfusqué d'Instagram (par chaîne de caractères, opcodes,
 *      signature, etc.).
 *   2. Dans execute { }, retrouver la méthode via le fingerprint et y injecter
 *      des instructions smali (addInstructions / replaceInstruction ...).
 *
 * Le package cible est "com.instagram.android". Sans version dans
 * compatibleWith, le patch est proposé pour n'importe quelle version de l'APK.
 */
@Suppress("unused")
val helloWorldPatch = bytecodePatch(
    name = "Hello World (Instagram)",
    description = "Patch de démarrage qui ne modifie rien. À utiliser comme base.",
) {
    // Limite ce patch à l'application Instagram. Ajoute une version si besoin :
    //   compatibleWith("com.instagram.android"("123.0.0.0.0"))
    compatibleWith("com.instagram.android")

    // Le code Java/Kotlin injecté dans l'APK vit dans le module extensions/.
    // Décommente la ligne suivante quand tu auras du code d'extension à appeler.
    // extendWith("extensions/extension.rve")

    execute {
        // TODO: localiser une méthode via un Fingerprint puis injecter du smali.
        //
        // Exemple de structure (à adapter) :
        //
        //   someFingerprint.method.apply {
        //       addInstructions(
        //           0,
        //           "const/4 v0, 0x1",
        //       )
        //   }
    }
}
