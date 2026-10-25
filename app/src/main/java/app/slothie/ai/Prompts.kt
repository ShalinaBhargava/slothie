package app.slothie.ai

import app.slothie.apps.Category

/**
 * Every prompt hands the model finished numbers and asks for words. The model is a writer and a
 * sorter here, never an accountant: if a figure is not in the JSON it is not allowed to appear.
 */
object Prompts {

    val SYSTEM_SLOTH = """
        You are Slothie, a sleepy, kind sloth who lives inside this person's phone and wants them to
        use it less so they can rest more. You speak in the first person, warmly, with a little dry
        humour, in plain short sentences. Never lecture. Never shame.

        Hard rules:
        - Use ONLY the numbers and app names given in the JSON. Do not invent, round up, or estimate.
        - If the JSON lacks something, say you do not know yet.
        - No markdown headings, no bullet symbols, no emoji except at most one sloth 🦥.
        - Keep it under 110 words.
    """.trimIndent()

    fun dailyNote(statsJson: String): String = """
        Here is what I know about this person's phone habits as JSON:
        $statsJson

        Write three short paragraphs, in this order:
        1. The hour they are most likely to be on the phone, with its chance and the apps that eat it.
        2. One lazy, concrete thing to do about that hour tonight (where to put the phone, what to do
           instead). It should take less effort than picking up the phone.
        3. One honest, encouraging line about today, based on today_minutes versus
           usual_minutes_by_this_time and longest_break_today_minutes.
    """.trimIndent()

    fun napPlan(statsJson: String, hourLabel: String): String = """
        Stats JSON:
        $statsJson

        It is almost $hourLabel, one of their risky hours. In two sentences, tell them what you
        (the sloth) are about to do: hold Do Not Disturb for a bit, and what they could do with the
        hour instead. Be specific about the apps named for that hour. Stay under 50 words.
    """.trimIndent()

    val SYSTEM_SORTER = """
        You sort Android apps into categories. Reply with JSON only: an array of objects with keys
        "package" and "category". Categories: ${Category.entries.joinToString(", ") { it.name }}.
        FEED is endless-scroll social media. VIDEO is streaming and short video. GAME is games.
        CHAT is messaging. TOOL is utilities, maps, payments, shopping, browsers. WORK is email,
        documents, calendars, developer tools. HEALTH is fitness, sleep, meditation. OTHER otherwise.
        No prose, no code fences.
    """.trimIndent()

    fun sortApps(apps: List<Pair<String, String>>): String = buildString {
        appendLine("Apps (label | package):")
        for ((pkg, label) in apps) appendLine("$label | $pkg")
        append("Return the JSON array now.")
    }

    /** Lenient extraction of [{"package":..,"category":..}] from whatever the model wrote. */
    fun parseSort(text: String): Map<String, Category> {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyMap()
        val body = text.substring(start, end + 1)
        val re = Regex("\"package\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"category\"\\s*:\\s*\"([^\"]+)\"")
        val re2 = Regex("\"category\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"package\"\\s*:\\s*\"([^\"]+)\"")
        val out = HashMap<String, Category>()
        for (m in re.findAll(body)) out[m.groupValues[1]] = Category.parse(m.groupValues[2])
        for (m in re2.findAll(body)) out[m.groupValues[2]] = Category.parse(m.groupValues[1])
        return out
    }
}
