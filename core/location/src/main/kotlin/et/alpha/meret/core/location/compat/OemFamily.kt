package et.alpha.meret.core.location.compat

/**
 * Phone makers grouped by the battery-management skin they ship, because that, not the name
 * on the box, decides which settings page keeps a tracking service alive.
 *
 * Tecno, Infinix and Itel are one family: all three are Transsion, all three ship HiOS / XOS /
 * itel OS with the same Phone Master app. Redmi and POCO are Xiaomi (MIUI / HyperOS). Realme
 * and OnePlus run ColorOS-derived builds, but OnePlus keeps its own settings layout, so it
 * stays separate. iQOO is Vivo. Honor split from Huawei in 2020 and kept EMUI's app-launch
 * manager.
 */
public enum class OemFamily {
    TRANSSION,
    XIAOMI,
    OPPO,
    VIVO,
    HUAWEI,
    SAMSUNG,
    ONEPLUS,

    /** Stock Android (Pixel). Nothing beyond the battery-optimisation exemption is needed. */
    STOCK,

    /** Anything else. Gets generic manual steps, never a guessed deep link. */
    OTHER,
}

/**
 * Maps `Build.MANUFACTURER` (and, optionally, `Build.BRAND`) to an [OemFamily].
 *
 * Both values come from the firmware and are not normalised: "TECNO MOBILE LIMITED",
 * "Infinix", " xiaomi ", "HONOR" and "realme" are all real. Matching is case-insensitive,
 * trims, and looks for the maker's name anywhere in the string. The brand is consulted
 * because some sub-brands report the parent as manufacturer (POCO reports "Xiaomi") and
 * some report themselves (Redmi on older builds).
 */
public fun oemFamilyFor(
    manufacturer: String,
    brand: String = "",
): OemFamily {
    val names = listOf(manufacturer, brand).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    return FAMILY_KEYWORDS
        .firstOrNull { (_, keywords) ->
            names.any { name -> keywords.any { it in name } }
        }?.first ?: OemFamily.OTHER
}

// Order matters only where names could overlap; none of these keywords is a substring of
// another family's, so the first match is the only match.
private val FAMILY_KEYWORDS: List<Pair<OemFamily, List<String>>> =
    listOf(
        OemFamily.TRANSSION to listOf("tecno", "infinix", "itel", "transsion"),
        OemFamily.XIAOMI to listOf("xiaomi", "redmi", "poco"),
        OemFamily.ONEPLUS to listOf("oneplus"),
        OemFamily.OPPO to listOf("oppo", "realme"),
        OemFamily.VIVO to listOf("vivo", "iqoo"),
        OemFamily.HUAWEI to listOf("huawei", "honor"),
        OemFamily.SAMSUNG to listOf("samsung"),
        OemFamily.STOCK to listOf("google"),
    )
