package com.ffmpegplaydownloader.app.data

import com.ffmpegplaydownloader.app.model.MediaItem
import com.ffmpegplaydownloader.app.model.MediaPlatform
import com.ffmpegplaydownloader.app.model.SearchSourceFilter
import java.text.Normalizer
import java.util.Locale

data class SearchPlan(
    val originalQuery: String,
    val isNaturalLanguage: Boolean,
    val isDirectUrl: Boolean,
    val effectiveSource: SearchSourceFilter,
    val inferredMusicIntent: Boolean,
    val inferredVideoIntent: Boolean,
    val youtubeQuery: String,
    val youtubeMusicQuery: String,
    val intentBadges: List<String>,
    val explanation: String,
    val coreTokens: List<String>,
    val autoPlayFirstResult: Boolean = false
)

/**
 * On-device Czech & English natural-language query understanding engine.
 * Converts conversational prompts into optimized per-platform queries (YouTube & YouTube Music)
 * and ranks results across platforms by semantic alignment.
 */
object NaturalLanguageSearchEngine {

    private data class SemanticRule(
        val triggers: List<String>,
        val addition: String,
        val badge: String,
        val musicBias: Int = 0,
        val videoBias: Int = 0,
        val stripTrigger: Boolean = false
    )

    private val conversationalFillers = listOf(
        "chci si pustit",
        "chtel bych si pustit",
        "chtela bych si pustit",
        "rad bych si pustil",
        "rada bych si pustila",
        "chtel bych poslouchat",
        "chci poslouchat",
        "poslechnout si",
        "podivat se na",
        "najdi mi",
        "vyhledej mi",
        "pust mi",
        "zahraj mi",
        "ukaz mi",
        "doporuc mi",
        "najit",
        "vyhledat",
        "prosim te",
        "prosim",
        "nejakou",
        "nejakeho",
        "nejakym",
        "nejaky",
        "nejake",
        "cokoliv",
        "neco na",
        "neco o",
        "kde najdu",
        "can you play",
        "i want to listen to",
        "play me",
        "find me",
        "search for",
        "show me"
    )

    private val semanticRules = listOf(
        SemanticRule(
            triggers = listOf("youtube music", "yt music", "ytm", "spotify"),
            addition = "",
            badge = "🎧 Preferuje YT Music",
            musicBias = 4,
            stripTrigger = true
        ),
        SemanticRule(
            triggers = listOf("na youtube", "z youtube"),
            addition = "",
            badge = "▶ Preferuje YouTube",
            videoBias = 3,
            stripTrigger = true
        ),
        SemanticRule(
            triggers = listOf("soustredeni", "uceni", "studium", "praci", "prace", "kodu", "programovani"),
            addition = "focus study ambient",
            badge = "🧠 Soustředění",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("spani", "usinani", "spanek", "na noc"),
            addition = "relaxing sleep calm",
            badge = "🌙 Spaní & relax",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("fitko", "fitka", "posilovny", "posilovani", "behani", "trenink", "cviceni", "workout"),
            addition = "workout energy mix",
            badge = "💪 Trénink & energie",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("do auta", "rizeni", "na cestu", "cestovani"),
            addition = "driving hits mix",
            badge = "🚗 Do auta",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("bez slov", "bez zpevu", "instrumentalni", "instrumental"),
            addition = "instrumental",
            badge = "🎹 Instrumentální",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("klidna", "klidnou", "klidne", "pohodova", "pohodovou", "relaxacni"),
            addition = "chill relaxing",
            badge = "☕ Pohoda",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("party", "tanecni", "oslava", "vecirek"),
            addition = "party dance mix",
            badge = "🎉 Párty",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("koncert", "zivak", "zive", "live"),
            addition = "live",
            badge = "🎤 Živě",
            musicBias = 1,
            videoBias = 1,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("akusticky", "akusticka", "unplugged"),
            addition = "acoustic",
            badge = "🎸 Akusticky",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("ceske titulky", "ceskymi titulky", "s titulky", "titulky cz"),
            addition = "česky titulky",
            badge = "💬 České titulky",
            videoBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("devadesatky", "devadesatek", "90."),
            addition = "90s hits",
            badge = "📼 90. léta",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("osmdesatky", "osmdesatek", "80."),
            addition = "80s hits",
            badge = "📻 80. léta",
            musicBias = 2,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("dokument", "recenze", "tutorial", "navod", "prednaska", "podcast", "rozhovor", "gameplay", "trailer", "film", "videa", "video"),
            addition = "",
            badge = "🎬 Video obsah",
            videoBias = 3,
            stripTrigger = false
        ),
        SemanticRule(
            triggers = listOf("hudba", "hudbu", "pisnicky", "pisnicka", "skladby", "skladba", "album", "song", "playlist", "mix", "mp3", "rock", "pop", "jazz", "lofi", "techno", "rap", "metal"),
            addition = "",
            badge = "🎵 Hudba",
            musicBias = 3,
            stripTrigger = false
        )
    )

    fun analyze(
        rawQuery: String,
        userSourceFilter: SearchSourceFilter,
        smartEnabled: Boolean
    ): SearchPlan {
        val trimmed = rawQuery.trim()
        val isUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)

        if (isUrl || !smartEnabled) {
            return SearchPlan(
                originalQuery = trimmed,
                isNaturalLanguage = false,
                isDirectUrl = isUrl,
                effectiveSource = userSourceFilter,
                inferredMusicIntent = userSourceFilter == SearchSourceFilter.YOUTUBE_MUSIC,
                inferredVideoIntent = userSourceFilter == SearchSourceFilter.YOUTUBE,
                youtubeQuery = trimmed,
                youtubeMusicQuery = trimmed,
                intentBadges = emptyList(),
                explanation = "",
                coreTokens = tokenizeCore(trimmed)
            )
        }

        val normalized = normalizeAscii(trimmed)
        var cleaned = trimmed
        var hadFillerOrNlp = false
        var autoPlayFirstResult = false

        // Strip voice-spoken leading commands safely at sentence start without touching song titles.
        val czechLeadMatch = czechLeadingCommandRegex.find(cleaned)
        if (czechLeadMatch != null) {
            if (czechLeadMatch.groupValues[1].isNotEmpty() || czechLeadMatch.groupValues[2].isNotEmpty()) {
                autoPlayFirstResult = true
            }
            cleaned = cleaned.substring(czechLeadMatch.range.last + 1).trimStart()
            hadFillerOrNlp = true
        } else {
            val englishLeadMatch = englishLeadingCommandRegex.find(cleaned)
            if (englishLeadMatch != null) {
                if (englishLeadMatch.groupValues[1].isNotEmpty() || englishLeadMatch.groupValues[2].isNotEmpty()) {
                    autoPlayFirstResult = true
                }
                cleaned = cleaned.substring(englishLeadMatch.range.last + 1).trimStart()
                hadFillerOrNlp = true
            }
        }

        // Detect standalone immediate-play voice triggers (e.g. "rovnou", "okamžitě", "ihned", "pusť hned").
        if (standaloneImmediateTriggerRegex.containsMatchIn(cleaned)) {
            autoPlayFirstResult = true
            cleaned = cleaned.replace(standaloneImmediateTriggerRegex, " ")
            hadFillerOrNlp = true
        }

        // Remove conversational filler phrases carefully (match diacritic-insensitive spans).
        for (filler in conversationalFillers) {
            val regex = buildAccentInsensitiveRegex(filler)
            if (regex.containsMatchIn(cleaned)) {
                cleaned = cleaned.replace(regex, " ")
                hadFillerOrNlp = true
            }
        }

        var musicScore = 0
        var videoScore = 0
        val additions = linkedSetOf<String>()
        val badges = linkedSetOf<String>()
        if (autoPlayFirstResult) {
            badges += "🎙️ Okamžité spuštění"
        }

        for (rule in semanticRules) {
            val matched = rule.triggers.any { trigger ->
                Regex("""\b${Regex.escape(trigger)}\b""").containsMatchIn(normalized)
            }
            if (matched) {
                musicScore += rule.musicBias
                videoScore += rule.videoBias
                if (rule.addition.isNotBlank()) {
                    additions += rule.addition
                    hadFillerOrNlp = true
                }
                badges += rule.badge
                if (rule.stripTrigger) {
                    for (t in rule.triggers) {
                        cleaned = cleaned.replace(buildAccentInsensitiveRegex(t), " ")
                    }
                    hadFillerOrNlp = true
                }
            }
        }

        // Collapse whitespace and punctuation debris
        cleaned = cleaned
            .replace(Regex("""[,;!?]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.isBlank()) {
            cleaned = trimmed
        }

        // Strip trailing prepositions left orphaned ("o", "pro", "na", "od", "v", "s")
        val cleanedWords = cleaned.split(" ").filter { it.isNotBlank() }
        val wordCount = trimmed.split(Regex("""\s+""")).size
        val isNatural = hadFillerOrNlp || wordCount >= 4 || musicScore > 0 || videoScore > 0

        val additionStr = additions
            .flatMap { it.split(" ") }
            .distinct()
            .filter { addWord ->
                val normAdd = normalizeAscii(addWord)
                cleanedWords.none { normalizeAscii(it) == normAdd }
            }
            .take(4)
            .joinToString(" ")

        val baseCore = cleaned
        val enrichedQuery = if (additionStr.isNotBlank()) "$baseCore $additionStr".trim() else baseCore

        val musicIntent = musicScore > videoScore
        val videoIntent = videoScore > musicScore

        val effectiveSource = when {
            userSourceFilter != SearchSourceFilter.ALL -> userSourceFilter
            // When user explicitly wrote "na YouTube Music" without conflicting video terms, prioritize both with heavy YTM or YTM focus
            badges.contains("🎧 Preferuje YT Music") && videoScore == 0 -> SearchSourceFilter.YOUTUBE_MUSIC
            badges.contains("▶ Preferuje YouTube") && musicScore == 0 -> SearchSourceFilter.YOUTUBE
            else -> SearchSourceFilter.ALL
        }

        val ytQuery = if (musicIntent && !normalizeAscii(enrichedQuery).contains("music") && !normalizeAscii(enrichedQuery).contains("hudb")) {
            "$enrichedQuery audio"
        } else {
            enrichedQuery
        }

        val ytmQuery = enrichedQuery

        val platformText = when (effectiveSource) {
            SearchSourceFilter.YOUTUBE_MUSIC -> "YouTube Music"
            SearchSourceFilter.YOUTUBE -> "YouTube"
            SearchSourceFilter.ALL -> when {
                musicIntent -> "YT Music + YouTube (priorita hudba)"
                videoIntent -> "YouTube + YT Music (priorita video)"
                else -> "YouTube + YT Music"
            }
        }

        val explanation = if (isNatural) {
            "Cíl: $platformText • Hledám: \"$ytmQuery\""
        } else {
            ""
        }

        return SearchPlan(
            originalQuery = trimmed,
            isNaturalLanguage = isNatural,
            isDirectUrl = false,
            effectiveSource = effectiveSource,
            inferredMusicIntent = musicIntent,
            inferredVideoIntent = videoIntent,
            youtubeQuery = ytQuery,
            youtubeMusicQuery = ytmQuery,
            intentBadges = badges.take(4),
            explanation = explanation,
            coreTokens = tokenizeCore(baseCore),
            autoPlayFirstResult = autoPlayFirstResult
        )
    }

    /**
     * Merges, deduplicates, scores, and interleaves results from YouTube and YouTube Music
     * so platforms stay clearly distinguishable and relevant to the user's intent.
     */
    fun scoreAndMergeResults(
        youtubeItems: List<MediaItem>,
        youtubeMusicItems: List<MediaItem>,
        plan: SearchPlan
    ): List<MediaItem> {
        fun scoreItem(item: MediaItem, positionIndex: Int): MediaItem {
            var score = (30 - positionIndex).coerceAtLeast(1) * 3
            val titleNorm = normalizeAscii(item.title)
            val channelNorm = normalizeAscii(item.channel)

            for (token in plan.coreTokens) {
                if (token.length < 2) continue
                if (titleNorm.contains(token)) score += 24
                if (channelNorm.contains(token)) score += 16
            }

            if (plan.inferredMusicIntent) {
                if (item.platform == MediaPlatform.YOUTUBE_MUSIC) score += 22
                if (item.contentKind.equals("Skladba", true)) score += 10
            } else if (plan.inferredVideoIntent) {
                if (item.platform == MediaPlatform.YOUTUBE) score += 22
                if (item.platform == MediaPlatform.YOUTUBE_MUSIC && item.contentKind == "Video") score += 8
            }

            if ((item.viewCount ?: 0L) >= 100_000L) score += 6
            return item.copy(relevanceScore = score)
        }

        val scoredYt = youtubeItems.mapIndexed { idx, item -> scoreItem(item, idx) }
        val scoredYtm = youtubeMusicItems.mapIndexed { idx, item -> scoreItem(item, idx) }

        if (scoredYt.isEmpty()) return scoredYtm
        if (scoredYtm.isEmpty()) return scoredYt

        // Deduplicate shared video IDs while preferring the platform matching the query's intent
        val byId = LinkedHashMap<String, MediaItem>()
        val firstPass = if (plan.inferredVideoIntent) scoredYt + scoredYtm else scoredYtm + scoredYt
        for (item in firstPass) {
            val existing = byId[item.id]
            if (existing == null || item.relevanceScore > existing.relevanceScore) {
                byId[item.id] = item
            }
        }

        val ytmPool = byId.values
            .filter { it.platform == MediaPlatform.YOUTUBE_MUSIC }
            .sortedByDescending { it.relevanceScore }
            .toMutableList()
        val ytPool = byId.values
            .filter { it.platform != MediaPlatform.YOUTUBE_MUSIC }
            .sortedByDescending { it.relevanceScore }
            .toMutableList()

        // Interleave platforms cleanly so the user immediately sees both platforms distinguished
        val merged = mutableListOf<MediaItem>()
        val ytmLead = plan.inferredMusicIntent || ytmPool.size >= ytPool.size
        while (ytmPool.isNotEmpty() || ytPool.isNotEmpty()) {
            if (ytmLead) {
                if (ytmPool.isNotEmpty()) merged += ytmPool.removeAt(0)
                if (ytPool.isNotEmpty()) merged += ytPool.removeAt(0)
            } else {
                if (ytPool.isNotEmpty()) merged += ytPool.removeAt(0)
                if (ytmPool.isNotEmpty()) merged += ytmPool.removeAt(0)
            }
        }
        return merged
    }

    private fun tokenizeCore(text: String): List<String> {
        val stopWords = setOf("a", "i", "v", "s", "z", "o", "u", "k", "na", "do", "od", "pro", "bez", "nebo", "the", "and", "for", "with", "of", "to", "in")
        return normalizeAscii(text)
            .split(Regex("""\s+"""))
            .map { it.trim(',', '.', ';', '!', '?', '"', '\'') }
            .filter { it.isNotBlank() && it !in stopWords }
            .distinct()
    }

    private fun normalizeAscii(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
    }

    private val accentMap = mapOf(
        'a' to "[aá]",
        'c' to "[cč]",
        'd' to "[dď]",
        'e' to "[eéě]",
        'i' to "[ií]",
        'n' to "[nň]",
        'o' to "[oó]",
        'r' to "[rř]",
        's' to "[sš]",
        't' to "[tť]",
        'u' to "[uúů]",
        'y' to "[yý]",
        'z' to "[zž]"
    )

    private val czechLeadingCommandRegex: Regex by lazy {
        val verbs = listOf(
            "pust",
            "prehraj",
            "zahraj",
            "zapni",
            "hraj",
            "vyhledej",
            "najdi",
            "ukaz",
            "dej"
        ).joinToString("|") { toAccentPattern(it) } +
            "|${toAccentPattern("chci")}\\s+${toAccentPattern("slyset")}"
        val prosim = toAccentPattern("prosim")
        val pronouns = "(?:${toAccentPattern("mi")}|${toAccentPattern("si")}|$prosim)"
        val immediate = "(?:${toAccentPattern("rovnou")}|${toAccentPattern("hned")}|${toAccentPattern("okamzite")}|${toAccentPattern("ihned")})"
        Regex("""(?i)^(?:$prosim\s+)?(?:($immediate)\s+)?(?:$verbs)(?:\s+$pronouns){0,2}(?:\s+($immediate))?\s+(?=\S)""")
    }

    private val englishLeadingCommandRegex: Regex by lazy {
        val immediate = """(?:right\s+now|immediately)"""
        Regex(
            """(?i)^(?:(?:can|could|would)\s+you\s+)?""" +
                """(?:please\s+(?:play|find|search(?:\s+for)?|show|stream|put\s+on)|""" +
                """play\s+me|play\s+($immediate)|put\s+on|find\s+me|search\s+for|show\s+me|""" +
                """i\s+(?:want|would\s+like)\s+to\s+listen\s+to)""" +
                """(?:\s+(?:me|please)){0,2}(?:\s+($immediate))?\s+(?=\S)"""
        )
    }

    private val standaloneImmediateTriggerRegex: Regex by lazy {
        val triggers = listOf(
            toAccentPattern("rovnou"),
            toAccentPattern("okamzite"),
            toAccentPattern("ihned"),
            "${toAccentPattern("pust")}\\s+${toAccentPattern("hned")}",
            "${toAccentPattern("prehraj")}\\s+${toAccentPattern("hned")}",
            "${toAccentPattern("zahraj")}\\s+${toAccentPattern("hned")}"
        ).joinToString("|")
        Regex("""(?i)(?<!\p{L})(?:$triggers)(?!\p{L})""")
    }

    private fun toAccentPattern(asciiPhrase: String): String =
        asciiPhrase.map { ch ->
            accentMap[ch] ?: Regex.escape(ch.toString())
        }.joinToString("")

    private fun buildAccentInsensitiveRegex(asciiPhrase: String): Regex {
        val pattern = toAccentPattern(asciiPhrase)
        return Regex("""(?i)(?<!\p{L})$pattern(?!\p{L})""")
    }
}
