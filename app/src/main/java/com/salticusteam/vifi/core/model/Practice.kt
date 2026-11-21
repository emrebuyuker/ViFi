package com.salticusteam.vifi.core.model

import androidx.annotation.Keep
import java.util.Locale

// The practice mode's ("Sınava gir", 4.2.0) read side of the "PRACTICE MODE" block of the shared schema.js
// (iOS `Practice.swift`): the index doc `questionPoolIndex/<subjectId>`, the callable `nextPracticeQuestions` and its
// limits. Keep names and limits in sync with it.

// Added in 4.2.0 — which pool a practice session draws from; [raw] is the callable's `scope`.
// Kept: travels in the type-safe `ViFiRoute.PracticeSession` (its serializer must survive R8).
@Keep
enum class PracticeScope(val raw: String) {
    /** This course only (`c-<courseId>`). */
    COURSE("course"),

    /** The user's university (from the profile), same subject (`u-<universityId>`). */
    UNIVERSITY("university"),

    /** All universities, same subject (`tr`). */
    TURKEY("tr");

    /** The key of this scope in an index document's per-type map, or `null` when its id is unknown. */
    fun poolKey(courseId: String, universityId: String?): String? = when (this) {
        COURSE -> courseId.takeIf { it.isNotEmpty() }?.let { "c-$it" }
        UNIVERSITY -> universityId?.takeIf { it.isNotEmpty() }?.let { "u-$it" }
        TURKEY -> "tr"
    }

    companion object {
        fun fromRaw(raw: String?): PracticeScope? = entries.firstOrNull { it.raw == raw }
    }
}

// Added in 4.2.0 — limits shared with the server (`PRACTICE_*` in schema.js).
object PracticeLimits {
    /** The most seen ids the app sends as `exclude`. */
    const val MAX_EXCLUDE = 300

    /** The most questions one call may ask for. */
    const val MAX_BATCH = 10
}

// Added in 4.2.0 — `questionPoolIndex/<subjectId>`: practice question counts per exam type and scope (iOS `PracticePoolIndex`).
data class PoolIndex(
    val subjectId: String,
    val name: String?,
    /** Counts per practice-eligible exam type, keyed by pool scope key (`tr`, `u-<id>`, `c-<id>`); all positive. */
    val types: Map<ExamType, Map<String, Int>>,
) {
    /** Whether any scope of any type has a question (P4: one is enough). Gates the "Sınava gir" entry. */
    val hasPracticeQuestions: Boolean
        get() = types.values.any { counts -> counts.values.any { it > 0 } }

    /** The question count of [type] in [scope]; 0 when the scope is unknown. */
    fun count(type: ExamType, scope: PracticeScope, courseId: String, universityId: String?): Int {
        val key = scope.poolKey(courseId, universityId) ?: return 0
        return types[type]?.get(key) ?: 0
    }
}

// Added in 4.2.0 — what the user picked in the setup: one pool of one subject.
data class PracticeSelection(
    val subjectId: String,
    val examType: ExamType,
    val scope: PracticeScope,
    val courseId: String,
    val universityId: String?,
    /** How many questions the pool holds (shown in the setup; informational). */
    val poolCount: Int,
)

// Added in 4.2.0 — one `nextPracticeQuestions` call.
data class PracticeRequest(
    val selection: PracticeSelection,
    /** Ids already seen in this session (the newest [PracticeLimits.MAX_EXCLUDE] are sent). */
    val exclude: List<String>,
    /** 1…[PracticeLimits.MAX_BATCH]; free users ask for 1 (no prefetch). */
    val count: Int,
)

// Added in 4.2.0 — a question handed out by the server, with where it came from.
data class PracticeQuestion(
    val question: ExamQuestion,
    /** "2019 FİNAL"; `null` when the server did not send it. */
    val examTitle: String?,
    val universityName: String?,
) {
    val id: String get() = question.id

    /** "BOZOK ÜNİVERSİTESİ · 2019 FİNAL" (whatever parts are known), shown above the card. */
    val sourceLine: String?
        get() = listOfNotNull(universityName?.uppercase(TURKISH), examTitle)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")

    private companion object {
        val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
    }
}

// Added in 4.2.0 — the server's answer to a `nextPracticeQuestions` call.
data class PracticeBatch(
    val questions: List<PracticeQuestion>,
    /** How many questions the pool holds in total. */
    val poolSize: Int,
    /** The free questions left today; `null` for Premium (unlimited). */
    val remainingToday: Int?,
    /** The free daily limit stopped the call (no, or fewer, questions were delivered) → paywall. */
    val limitReached: Boolean,
)

// Added in 4.2.0 — the Premium proof sent with every `nextPracticeQuestions` call (`subscriptionProof`).
/**
 * The active Play subscription: `{platform: "android", purchaseToken, productId}`. The server verifies it (`DDEC-03`);
 * a missing or invalid proof means the free rules. The token never reaches a log ([toString] redacts it).
 */
data class SubscriptionProof(
    val purchaseToken: String,
    val productId: String,
) {
    val platform: String get() = PLATFORM

    /** The callable's `subscriptionProof` value. */
    fun payload(): Map<String, String> =
        mapOf("platform" to PLATFORM, "purchaseToken" to purchaseToken, "productId" to productId)

    override fun toString(): String = "SubscriptionProof(platform=$PLATFORM, productId=$productId, purchaseToken=…)"

    companion object {
        const val PLATFORM = "android"
    }
}
