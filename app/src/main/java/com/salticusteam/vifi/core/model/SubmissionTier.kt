package com.salticusteam.vifi.core.model

// Added in 2.5 — how much new content a submission adds to the archive (shallower novelty = more valuable).
/**
 * The deepest level of the taxonomy at which a submission introduces something not already there. The
 * authoritative tier is decided at approval; the add-exam screen shows the same computation as an estimate.
 * [raw] is the config key under `config/rewards/<platform>/tiers`. The Turkish label is resolved in the UI.
 */
enum class SubmissionTier(val raw: String) {
    /** The university itself is not in the archive: the whole chain is new. */
    NEW_UNIVERSITY("newUniversity"),

    /** The university exists, but this faculty under it is new. */
    NEW_FACULTY("newFaculty"),

    /** The faculty exists, but this department under it is new. */
    NEW_DEPARTMENT("newDepartment"),

    /** The department exists, but this course (lesson) under it is new. */
    NEW_COURSE("newCourse"),

    /** The course exists, but this exam (name + year) is new for it. */
    NEW_EXAM("newExam"),

    /** The course already has an exam with the same name and year. */
    DUPLICATE("duplicate");

    companion object {
        fun fromRaw(raw: String?): SubmissionTier? = entries.firstOrNull { it.raw == raw }

        /**
         * The tier implied by which archive levels already exist. A level can exist only when its parent does,
         * so the first missing level from the root down decides the tier; callers must pass consistent flags.
         */
        fun classify(
            universityExists: Boolean,
            facultyExists: Boolean,
            departmentExists: Boolean,
            courseExists: Boolean,
            examExists: Boolean,
        ): SubmissionTier = when {
            !universityExists -> NEW_UNIVERSITY
            !facultyExists -> NEW_FACULTY
            !departmentExists -> NEW_DEPARTMENT
            !courseExists -> NEW_COURSE
            !examExists -> NEW_EXAM
            else -> DUPLICATE
        }
    }
}
