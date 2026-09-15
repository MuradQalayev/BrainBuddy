package com.muradgalayev.brainbuddy.ui.onboarding

// why a survey submit or save didn't go through. typed rather than a bare string because the
// three kinds want three different screens: a missing answer is the user's turn to act, a taken
// username is a fixable conflict, and a dead server is nobody's fault and must never leave the
// user trapped on the page
data class SurveyError(
    val kind: Kind,
    // the specific detail, when there is one worth showing
    val detail: com.muradgalayev.brainbuddy.ui.utils.UiText? = null,
) {
    enum class Kind {
        // required answers are missing, and detail says which
        MissingAnswers,

        // the chosen username belongs to someone else
        UsernameTaken,

        // the server refused or couldn't be reached, and the local save also failed. rare: normally an
        // offline save succeeds locally and syncs later
        SaveFailed,
    }
}
