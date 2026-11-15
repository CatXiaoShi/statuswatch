package luolang.status.data

/**
 * 厂商 / 品牌中英对照。
 *
 * 设备 sysfs 里通常只有英文名（如 MICRON），界面展示时映射成中文，
 * 同时保留英文原名，方便对照。
 */
object BrandNames {

    private val MAP: List<Pair<String, String>> = listOf(
        "SK HYNIX" to "SK 海力士",
        "HYNIX" to "SK 海力士",
        "MICRON" to "美光",
        "SAMSUNG" to "三星",
        "KIOXIA" to "铠侠",
        "TOSHIBA" to "东芝",
        "WESTERN DIGITAL" to "西部数据",
        "WDC" to "西部数据",
        "SANDISK" to "闪迪",
        "KINGSTON" to "金士顿",
        "YMTC" to "长江存储",
        "YANGTZE" to "长江存储",
        "LONGSYS" to "江波龙",
        "FORESEE" to "江波龙",
        "UMIS" to "紫光",
        "UNIC" to "紫光",
        "PHISON" to "群联",
        "SILICON MOTION" to "慧荣",
        "SMI" to "慧荣",
        "ADATA" to "威刚",
        "CRUCIAL" to "英睿达",
        "CORSAIR" to "海盗船",
        "HUAWEI" to "华为",
        "APPLE" to "苹果",
    )

    /** 返回中文品牌名；没有映射时返回空串。 */
    fun cn(raw: String): String {
        val s = raw.trim().uppercase()
        if (s.isEmpty()) return ""
        return MAP.firstOrNull { s.contains(it.first) }?.second ?: ""
    }

    /** 展示用："美光 MICRON"；未知时原样返回。 */
    fun display(raw: String, unknown: String = "未知"): String {
        val name = raw.trim()
        if (name.isEmpty()) return unknown
        val cn = cn(name)
        return if (cn.isEmpty()) name else "$cn $name"
    }
}
