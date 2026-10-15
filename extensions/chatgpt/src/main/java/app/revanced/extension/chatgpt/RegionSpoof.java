package app.revanced.extension.chatgpt;

import android.os.LocaleList;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.time.ZoneId;
import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Localisation simulée (par défaut : États-Unis) pour ChatGPT.
 *
 * Le patch remplace, dans tout le bytecode de l'application, les appels aux API
 * Android/Java qui révèlent le pays de l'appareil par des appels statiques vers
 * cette classe. Chaque méthode reçoit les mêmes registres que l'appel d'origine
 * (le récepteur en premier pour les appels d'instance), ce qui permet un
 * remplacement 1:1 de l'instruction.
 *
 *  - Locale.getDefault(), Locale.getDefault(Category), LocaleList.getDefault(),
 *    LocaleList.getAdjustedDefault() : la région devient le pays cible ; la langue
 *    (et l'écriture) de l'utilisateur sont conservées sauf option contraire.
 *  - TelephonyManager : pays ISO de la SIM et du réseau, code MCC/MNC et nom de
 *    l'opérateur.
 *  - TimeZone.getDefault() / ZoneId.systemDefault() : uniquement si un fuseau
 *    horaire a été choisi dans les options du patch.
 *
 * Les méthodes *Option() sont réécrites par le patch avec les valeurs choisies dans
 * ReVanced Manager. Les valeurs ci-dessous sont les valeurs par défaut.
 */
@SuppressWarnings("unused")
public final class RegionSpoof {
    private static final String TAG = "RevancedRegion";

    private static final String COUNTRY = normalizeCountry(countryOption());
    private static final String COUNTRY_ISO = COUNTRY.toLowerCase(Locale.ROOT);
    private static final boolean KEEP_LANGUAGE = keepLanguageOption();
    private static final String TIME_ZONE = timeZoneOption();
    private static final String[] OPERATOR = operatorFor(COUNTRY);

    // Locale.getDefault() est appelé très souvent : on mémorise la dernière conversion.
    private static volatile Locale[] lastLocale;
    private static volatile Object[] lastLocaleList;

    private RegionSpoof() {
    }

    // region Options (corps remplacés par le patch)

    private static String countryOption() {
        return "US";
    }

    private static boolean keepLanguageOption() {
        return true;
    }

    private static String timeZoneOption() {
        return "";
    }

    // endregion

    // region java.util.Locale / android.os.LocaleList

    /** Remplace {@code Locale.getDefault()}. */
    public static Locale getDefault() {
        return spoof(Locale.getDefault());
    }

    /** Remplace {@code Locale.getDefault(Locale.Category)}. */
    public static Locale getDefault(Locale.Category category) {
        return spoof(Locale.getDefault(category));
    }

    /** Remplace {@code LocaleList.getDefault()}. */
    public static LocaleList getDefaultLocaleList() {
        return spoof(LocaleList.getDefault());
    }

    /** Remplace {@code LocaleList.getAdjustedDefault()}. */
    public static LocaleList getAdjustedDefaultLocaleList() {
        return spoof(LocaleList.getAdjustedDefault());
    }

    static Locale spoof(Locale locale) {
        if (locale == null) return null;

        Locale[] cached = lastLocale;
        if (cached != null && cached[0] == locale) return cached[1];

        Locale spoofed = convert(locale);
        lastLocale = new Locale[]{locale, spoofed};
        return spoofed;
    }

    static LocaleList spoof(LocaleList list) {
        if (list == null) return null;

        Object[] cached = lastLocaleList;
        if (cached != null && cached[0] == list) return (LocaleList) cached[1];

        LocaleList spoofed;
        try {
            // LocaleList refuse les doublons (fr-FR et fr-BE deviennent tous deux fr-US).
            Set<Locale> locales = new LinkedHashSet<>();
            if (!KEEP_LANGUAGE) locales.add(new Locale("en", COUNTRY));
            for (int i = 0; i < list.size(); i++) {
                locales.add(convert(list.get(i)));
            }
            spoofed = locales.isEmpty() ? list : new LocaleList(locales.toArray(new Locale[0]));
        } catch (Throwable t) {
            Log.w(TAG, "Unable to spoof locale list " + list, t);
            spoofed = list;
        }

        lastLocaleList = new Object[]{list, spoofed};
        return spoofed;
    }

    private static Locale convert(Locale locale) {
        if (!KEEP_LANGUAGE) return new Locale("en", COUNTRY);
        if (COUNTRY.equals(locale.getCountry())) return locale;

        try {
            Locale.Builder builder = new Locale.Builder().setLocale(locale).setRegion(COUNTRY);
            // zh-TW / zh-HK impliquent le chinois traditionnel : sans écriture explicite,
            // zh-US serait interprété comme du chinois simplifié.
            if ("zh".equals(locale.getLanguage()) && locale.getScript().isEmpty()) {
                String region = locale.getCountry();
                boolean traditional = "TW".equals(region) || "HK".equals(region) || "MO".equals(region);
                builder.setScript(traditional ? "Hant" : "Hans");
            }
            return builder.build();
        } catch (IllformedLocaleException e) {
            return new Locale(locale.getLanguage(), COUNTRY, locale.getVariant());
        }
    }

    // endregion

    // region android.telephony.TelephonyManager

    /** Remplace {@code TelephonyManager.getSimCountryIso()}. */
    public static String getSimCountryIso(TelephonyManager manager) {
        return COUNTRY_ISO;
    }

    /** Remplace {@code TelephonyManager.getNetworkCountryIso()}. */
    public static String getNetworkCountryIso(TelephonyManager manager) {
        return COUNTRY_ISO;
    }

    /** Remplace {@code TelephonyManager.getNetworkCountryIso(int)}. */
    public static String getNetworkCountryIso(TelephonyManager manager, int slotIndex) {
        return COUNTRY_ISO;
    }

    /** Remplace {@code TelephonyManager.getSimOperator()}. */
    public static String getSimOperator(TelephonyManager manager) {
        return OPERATOR != null ? OPERATOR[0] : manager.getSimOperator();
    }

    /** Remplace {@code TelephonyManager.getNetworkOperator()}. */
    public static String getNetworkOperator(TelephonyManager manager) {
        return OPERATOR != null ? OPERATOR[0] : manager.getNetworkOperator();
    }

    /** Remplace {@code TelephonyManager.getSimOperatorName()}. */
    public static String getSimOperatorName(TelephonyManager manager) {
        return OPERATOR != null ? OPERATOR[1] : manager.getSimOperatorName();
    }

    /** Remplace {@code TelephonyManager.getNetworkOperatorName()}. */
    public static String getNetworkOperatorName(TelephonyManager manager) {
        return OPERATOR != null ? OPERATOR[1] : manager.getNetworkOperatorName();
    }

    // endregion

    // region java.util.TimeZone / java.time.ZoneId (seulement si l'option est active)

    /** Remplace {@code TimeZone.getDefault()}. */
    public static TimeZone getDefaultTimeZone() {
        TimeZone real = TimeZone.getDefault();
        if (TIME_ZONE.isEmpty() || TIME_ZONE.equals(real.getID())) return real;
        return TimeZone.getTimeZone(TIME_ZONE);
    }

    /** Remplace {@code ZoneId.systemDefault()}. */
    public static ZoneId systemDefaultZone() {
        if (TIME_ZONE.isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(TIME_ZONE);
        } catch (Throwable t) {
            return ZoneId.systemDefault();
        }
    }

    // endregion

    private static String normalizeCountry(String country) {
        if (country == null) return "US";
        String trimmed = country.trim().toUpperCase(Locale.ROOT);
        return trimmed.length() == 2 ? trimmed : "US";
    }

    /** Code MCC/MNC et nom d'un opérateur plausible ; null = valeurs réelles conservées. */
    private static String[] operatorFor(String country) {
        switch (country) {
            case "US":
                return new String[]{"310260", "T-Mobile"};
            case "CA":
                return new String[]{"302720", "Rogers"};
            case "GB":
                return new String[]{"23415", "Vodafone UK"};
            case "AU":
                return new String[]{"50501", "Telstra"};
            case "JP":
                return new String[]{"44010", "NTT DOCOMO"};
            default:
                return null;
        }
    }
}
