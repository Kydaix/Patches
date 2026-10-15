package app.revanced.patches.chatgpt.region

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableClassDef
import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.booleanOption
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil
import java.util.TimeZone
import java.util.logging.Logger

/**
 * Localisation : faire croire à ChatGPT que l'appareil est aux États-Unis.
 *
 * ChatGPT est obfusqué et ses classes changent à chaque version. Plutôt que de viser
 * une méthode interne, le patch parcourt tout le bytecode et remplace chaque appel
 * aux API système qui révèlent le pays (locale par défaut, pays de la SIM et du
 * réseau, opérateur, et en option le fuseau horaire) par un appel statique vers
 * l'extension `RegionSpoof`. Ces API publiques Android/Java ne changent pas d'une
 * version de ChatGPT à l'autre, d'où la compatibilité avec toutes les versions.
 *
 * Le remplacement est 1:1 : `invoke-virtual {vX, vY}, TelephonyManager->m(I)`
 * devient `invoke-static {vX, vY}, RegionSpoof->m(TelephonyManager, I)`, avec les
 * mêmes registres et la même taille d'instruction. Aucun registre n'est ajouté et les
 * labels / blocs try restent valides.
 */

private const val EXTENSION = "Lapp/revanced/extension/chatgpt/RegionSpoof;"

private const val LOCALE = "Ljava/util/Locale;"
private const val LOCALE_LIST = "Landroid/os/LocaleList;"
private const val TELEPHONY = "Landroid/telephony/TelephonyManager;"
private const val STRING = "Ljava/lang/String;"

/** Préfixes de classes jamais réécrites. */
private val excludedPackages = listOf(
    // L'extension appelle les vraies API : la réécrire provoquerait une récursion infinie.
    "Lapp/revanced/extension/",
    // AndroidX (AppCompat, Core) gère la langue de l'interface : on n'y touche pas.
    "Landroidx/",
)

/**
 * Un appel système à rediriger vers [EXTENSION].
 *
 * @param static `true` pour un `invoke-static`, `false` pour un `invoke-virtual`
 * (le récepteur devient alors le premier paramètre de la méthode d'extension).
 */
internal class CallSiteHook(
    val definingClass: String,
    val name: String,
    val parameters: List<String>,
    val returnType: String,
    val static: Boolean,
    extensionName: String,
) {
    val key = signature(definingClass, name, parameters, returnType)

    val replacement = ImmutableMethodReference(
        EXTENSION,
        extensionName,
        if (static) parameters else listOf(definingClass) + parameters,
        returnType,
    )
}

private fun signature(definingClass: String, name: String, parameters: List<CharSequence>, returnType: String) =
    "$definingClass->$name(${parameters.joinToString("")})$returnType"

private fun static(
    definingClass: String,
    name: String,
    parameters: List<String>,
    returnType: String,
    extensionName: String = name,
) = CallSiteHook(definingClass, name, parameters, returnType, true, extensionName)

private fun virtual(definingClass: String, name: String, parameters: List<String>, returnType: String) =
    CallSiteHook(definingClass, name, parameters, returnType, false, name)

internal val localeHooks = listOf(
    static(LOCALE, "getDefault", emptyList(), LOCALE),
    static(LOCALE, "getDefault", listOf("Ljava/util/Locale\$Category;"), LOCALE),
    static(LOCALE_LIST, "getDefault", emptyList(), LOCALE_LIST, "getDefaultLocaleList"),
    static(LOCALE_LIST, "getAdjustedDefault", emptyList(), LOCALE_LIST, "getAdjustedDefaultLocaleList"),
)

internal val telephonyHooks = listOf(
    virtual(TELEPHONY, "getSimCountryIso", emptyList(), STRING),
    virtual(TELEPHONY, "getNetworkCountryIso", emptyList(), STRING),
    virtual(TELEPHONY, "getNetworkCountryIso", listOf("I"), STRING),
    virtual(TELEPHONY, "getSimOperator", emptyList(), STRING),
    virtual(TELEPHONY, "getNetworkOperator", emptyList(), STRING),
    virtual(TELEPHONY, "getSimOperatorName", emptyList(), STRING),
    virtual(TELEPHONY, "getNetworkOperatorName", emptyList(), STRING),
)

internal val timeZoneHooks = listOf(
    static("Ljava/util/TimeZone;", "getDefault", emptyList(), "Ljava/util/TimeZone;", "getDefaultTimeZone"),
    static("Ljava/time/ZoneId;", "systemDefault", emptyList(), "Ljava/time/ZoneId;", "systemDefaultZone"),
)

/**
 * Remplace tous les appels correspondant à [hooks] dans [classes].
 *
 * @param mutableClassOf Retourne la version modifiable d'une classe (et la substitue
 * dans le dex).
 * @return Le nombre d'appels remplacés.
 */
internal fun rewriteCallSites(
    classes: Collection<ClassDef>,
    hooks: List<CallSiteHook>,
    mutableClassOf: (ClassDef) -> MutableClassDef,
): Int {
    val hooksByKey = hooks.associateBy { it.key }
    val names = hooks.mapTo(HashSet()) { it.name }

    fun hookFor(instruction: Instruction): CallSiteHook? {
        val static = when (instruction.opcode) {
            Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE -> true
            Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE -> false
            else -> return null
        }
        val reference = (instruction as ReferenceInstruction).reference as? MethodReference ?: return null
        // Filtre rapide avant de construire la signature complète.
        if (reference.name !in names) return null

        val hook = hooksByKey[
            signature(reference.definingClass, reference.name, reference.parameterTypes, reference.returnType),
        ] ?: return null

        return hook.takeIf { it.static == static }
    }

    fun Method.hasCallSite() = implementation?.instructions?.any { hookFor(it) != null } == true

    // Lister d'abord : la collection de classes ne doit pas être modifiée pendant le parcours.
    val targets = classes
        .filter { classDef -> excludedPackages.none { classDef.type.startsWith(it) } }
        .mapNotNull { classDef ->
            classDef.methods.filter { it.hasCallSite() }.takeIf { it.isNotEmpty() }?.let { classDef to it }
        }

    var count = 0
    targets.forEach { (classDef, methods) ->
        val mutableClass = mutableClassOf(classDef)

        methods.forEach { method ->
            val mutableMethod = mutableClass.methods.first { MethodUtil.methodSignaturesMatch(it, method) }
            count += mutableMethod.rewriteCallSites(::hookFor)
        }
    }

    return count
}

private fun MutableMethod.rewriteCallSites(hookFor: (Instruction) -> CallSiteHook?): Int {
    val instructions = implementation?.instructions ?: return 0

    var count = 0
    instructions.indices.forEach { index ->
        val instruction = instructions[index]
        val hook = hookFor(instruction) ?: return@forEach

        val replacement: BuilderInstruction = when (instruction) {
            is RegisterRangeInstruction -> BuilderInstruction3rc(
                Opcode.INVOKE_STATIC_RANGE,
                instruction.startRegister,
                instruction.registerCount,
                hook.replacement,
            )

            is FiveRegisterInstruction -> BuilderInstruction35c(
                Opcode.INVOKE_STATIC,
                instruction.registerCount,
                instruction.registerC,
                instruction.registerD,
                instruction.registerE,
                instruction.registerF,
                instruction.registerG,
                hook.replacement,
            )

            else -> return@forEach
        }

        replaceInstruction(index, replacement)
        count++
    }

    return count
}

private fun MutableClassDef.optionMethod(name: String) =
    methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
        ?: throw PatchException("Méthode $name introuvable dans l'extension RegionSpoof")

private val countryCodeRegex = Regex("^[A-Za-z]{2}$")

@Suppress("unused")
val spoofRegionPatch = bytecodePatch(
    name = "Localisation : États-Unis",
    description = "Fait croire à ChatGPT que l'appareil se trouve aux États-Unis (langue, SIM, réseau " +
        "et opérateur ; fuseau horaire en option) pour débloquer les fonctionnalités réservées à ce pays.",
) {
    // Aucune version listée : le patch ne dépend que d'API publiques Android/Java.
    compatibleWith("com.openai.chatgpt")

    extendWith("extensions/chatgpt.rve")

    val country by stringOption(
        name = "Pays",
        default = "US",
        values = mapOf(
            "États-Unis" to "US",
            "Canada" to "CA",
            "Royaume-Uni" to "GB",
            "Australie" to "AU",
            "Japon" to "JP",
        ),
        description = "Code pays ISO 3166-1 à deux lettres présenté à l'application.",
        required = true,
    ) { it != null && countryCodeRegex.matches(it) }

    val keepLanguage by booleanOption(
        name = "Conserver la langue",
        default = true,
        description = "Garde la langue de l'appareil (ex. fr-FR devient fr-US). Désactivé, la locale " +
            "présentée devient en-<pays>.",
        required = true,
    )

    val timeZone by stringOption(
        name = "Fuseau horaire",
        default = null,
        values = mapOf(
            "Désactivé (fuseau réel)" to null,
            "New York (Est)" to "America/New_York",
            "Chicago (Centre)" to "America/Chicago",
            "Denver (Rocheuses)" to "America/Denver",
            "Los Angeles (Pacifique)" to "America/Los_Angeles",
        ),
        description = "Facultatif. Fuseau horaire IANA présenté à l'application. Attention : les heures " +
            "affichées et les tâches planifiées utiliseront ce fuseau.",
    ) { it.isNullOrBlank() || it in TimeZone.getAvailableIDs() }

    execute {
        val extension = classDefs[EXTENSION]
            ?.let(classDefs::getOrReplaceMutable)
            ?: throw PatchException("Extension RegionSpoof absente")

        // Injection des options dans l'extension.
        val countryCode = country!!.uppercase()
        val zone = timeZone?.trim().orEmpty()
        extension.optionMethod("countryOption").addInstructions(
            0,
            """
                const-string v0, "$countryCode"
                return-object v0
            """,
        )
        extension.optionMethod("keepLanguageOption").addInstructions(
            0,
            """
                const/4 v0, ${if (keepLanguage != false) "0x1" else "0x0"}
                return v0
            """,
        )
        extension.optionMethod("timeZoneOption").addInstructions(
            0,
            """
                const-string v0, "$zone"
                return-object v0
            """,
        )

        val hooks = localeHooks + telephonyHooks + if (zone.isNotEmpty()) timeZoneHooks else emptyList()
        val count = rewriteCallSites(classDefs.toList(), hooks, classDefs::getOrReplaceMutable)

        Logger.getLogger("SpoofRegionPatch").info("$count appels système redirigés vers RegionSpoof ($countryCode)")
    }
}
