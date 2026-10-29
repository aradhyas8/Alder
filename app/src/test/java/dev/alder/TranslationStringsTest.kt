package dev.alder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class TranslationStringsTest {

    private fun parseStringKeys(xmlFile: File): Map<String, String> {
        val content = xmlFile.readText()
        val map = mutableMapOf<String, String>()
        val pattern = Pattern.compile("""<string\s+name="([^"]+)">([^<]*)</string>""")
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            map[matcher.group(1)] = matcher.group(2)
        }
        return map
    }

    private fun extractPlaceholders(text: String): List<String> {
        val placeholders = mutableListOf<String>()
        val pattern = Pattern.compile("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            placeholders.add(matcher.group())
        }
        return placeholders.sorted()
    }

    @Test
    fun allLocalesHaveEveryStringAndMatchingPlaceholders() {
        val baseDir = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val defaultStringsFile = File(baseDir, "values/strings.xml")
        assertTrue("Base strings.xml must exist", defaultStringsFile.exists())

        val defaultMap = parseStringKeys(defaultStringsFile)
        assertTrue("Base strings should contain keys", defaultMap.isNotEmpty())

        val locales = listOf("es", "pt", "fr", "de", "hi", "it")
        for (locale in locales) {
            val localeFile = File(baseDir, "values-$locale/strings.xml")
            assertTrue("values-$locale/strings.xml must exist", localeFile.exists())

            val localeMap = parseStringKeys(localeFile)
            for ((key, englishValue) in defaultMap) {
                if (key == "app_name") continue // app_name stays universal or localized

                assertTrue("Locale '$locale' is missing string key: $key", localeMap.containsKey(key))
                val translated = localeMap[key].orEmpty()
                assertTrue("Locale '$locale' has blank string for key: $key", translated.isNotBlank())

                val defaultPlaceholders = extractPlaceholders(englishValue)
                val translatedPlaceholders = extractPlaceholders(translated)
                assertEquals(
                    "Placeholder mismatch in '$locale' for key '$key' ('$englishValue' vs '$translated')",
                    defaultPlaceholders,
                    translatedPlaceholders
                )
            }
        }
    }

    @Test
    fun localesConfigListsAllSupportedLocales() {
        val baseDir = File("src/main/res").takeIf { it.exists() } ?: File("app/src/main/res")
        val configFile = File(baseDir, "xml/locales_config.xml")
        assertTrue("locales_config.xml must exist", configFile.exists())
        val content = configFile.readText()
        listOf("en", "es", "pt-BR", "fr", "de", "hi", "it").forEach { lang ->
            assertTrue("locales_config.xml must declare $lang", content.contains("""<locale android:name="$lang"/>"""))
        }
    }
}
