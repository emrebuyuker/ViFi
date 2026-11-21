package com.salticusteam.vifi.feature.structuredexam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salticusteam.vifi.R
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.math.QuestionRichText
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.designsystem.theme.ViFiThemePreviews
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.util.MathTextTokenizer

/**
 * One question as a card (4.2.0, iOS `QuestionCardView` + `QuestionContentView`): the heading, the question text,
 * figures and choices, the "Cevabı Göster" button and, once revealed, the answer, the worked solution and the AI note.
 *
 * Reusable on purpose: the exam's question list shows every card, "Sınava gir" shows one at a time. It owns no
 * state: the caller decides whether the answer is shown ([isAnswerRevealed], kept on the device only) and handles the
 * button ([onToggleAnswer]). Draw it inside the screen's `ScreenCaptureProtection`, never into a bitmap.
 *
 * TalkBack reads two elements per card, in words instead of LaTeX: the question (`speech`, "Şekil var.", the
 * choices) and — only while revealed — the answer (`answer.speech`, the steps, the note). The heading is a heading.
 *
 * @param title the heading; `null` is the exam's own numbering ("4. Soru"). Practice passes its running number.
 * @param sourceLine an optional line under the heading (practice: "BOZOK ÜNİVERSİTESİ · 2019 FİNAL").
 * @param headerAction a slot above the card, outside its content, aligned to the end (the admin "Yönetici" menu).
 */
@Composable
fun QuestionCard(
    question: ExamQuestion,
    isAnswerRevealed: Boolean,
    onToggleAnswer: () -> Unit,
    figureImages: QuestionFigureImages,
    modifier: Modifier = Modifier,
    title: String? = null,
    sourceLine: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (headerAction != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { headerAction() }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("questions.card.${question.order}"),
            shape = CARD_SHAPE,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title ?: stringResource(R.string.structured_exam_question_title, question.label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("questions.title.${question.order}"),
                    )
                    if (!sourceLine.isNullOrBlank()) {
                        Text(
                            text = sourceLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("questions.source.${question.order}"),
                        )
                    }
                }
                QuestionSection(question = question, isAnswerRevealed = isAnswerRevealed, figureImages = figureImages)
                if (isAnswerRevealed) AnswerSection(question = question)
                RevealButton(order = question.order, isAnswerRevealed = isAnswerRevealed, onClick = onToggleAnswer)
            }
        }
    }
}

/** The question text, its figures and its choices: one TalkBack element reading the question's speech. */
@Composable
private fun QuestionSection(question: ExamQuestion, isAnswerRevealed: Boolean, figureImages: QuestionFigureImages) {
    val speech = questionSpeech(question)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = speech }
            .testTag("questions.question.${question.order}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuestionRichText(text = question.text, style = MaterialTheme.typography.bodyLarge)
        question.figures.forEach { figure ->
            QuestionFigureImage(figure = figure, images = figureImages)
        }
        question.choices.forEach { choice ->
            val isCorrect = isAnswerRevealed && choice.label == question.answer.choice
            MarkedRow(
                marker = if (isCorrect) {
                    stringResource(R.string.structured_exam_correct_choice_marker, choice.label)
                } else {
                    choice.label
                },
                markerWeight = FontWeight.SemiBold,
                text = choice.text,
                modifier = Modifier.testTag("questions.choice.${question.order}.${choice.label}"),
            )
        }
    }
}

/** "Cevap", "Doğru seçenek: X", the answer, "Çözüm" with numbered steps and the AI note: one TalkBack element. */
@Composable
private fun AnswerSection(question: ExamQuestion) {
    val speech = answerSpeech(question)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = speech }
            .testTag("questions.answer.${question.order}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionHeading(stringResource(R.string.structured_exam_answer_heading))
        val choice = question.answer.choice
        if (!choice.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.structured_exam_correct_choice, choice),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("questions.correctChoice.${question.order}"),
            )
        }
        QuestionRichText(text = question.answer.text, style = MaterialTheme.typography.bodyLarge)
        if (question.solution.isNotEmpty()) {
            SectionHeading(stringResource(R.string.structured_exam_solution_heading))
            question.solution.forEachIndexed { index, step ->
                MarkedRow(
                    marker = stringResource(R.string.structured_exam_solution_step_marker, index + 1),
                    markerWeight = FontWeight.Normal,
                    text = step,
                )
            }
        }
        Text(
            text = stringResource(R.string.structured_exam_ai_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("questions.aiNote.${question.order}"),
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

/** A marker ("A", "1.") beside its Markdown + LaTeX text; the marker column keeps a minimum width so rows line up. */
@Composable
private fun MarkedRow(marker: String, markerWeight: FontWeight, text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyLarge
    // iOS: at least 1.6 × the font size, so "A" … "D" and "1." … "9." share one column.
    val minMarkerWidth: Dp = with(LocalDensity.current) { (style.fontSize * MARKER_WIDTH_EMS).toDp() }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = marker,
            style = style,
            fontWeight = markerWeight,
            maxLines = 1,
            modifier = Modifier.widthIn(min = minMarkerWidth),
        )
        QuestionRichText(text = text, style = style, modifier = Modifier.weight(1f))
    }
}

/** "Cevabı Göster" in brand orange with black text (white on orange falls below 3:1), "Cevabı Gizle" outlined. */
@Composable
private fun RevealButton(order: Int, isAnswerRevealed: Boolean, onClick: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .testTag("questions.reveal.$order")
    if (isAnswerRevealed) {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.structured_exam_hide_answer))
        }
    } else {
        // The primary button's content colour is onPrimary: black on the brand orange in both themes.
        Button(onClick = onClick, modifier = modifier) {
            Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.structured_exam_reveal_answer))
        }
    }
}

/** The question for TalkBack: the stored speech (or the plain text), "Şekil var." when it has figures, the choices. */
@Composable
private fun questionSpeech(question: ExamQuestion): String {
    val figure = stringResource(R.string.structured_exam_speech_figure)
    val choices = question.choices.map { choice ->
        stringResource(R.string.structured_exam_speech_choice, choice.label, MathTextTokenizer.plainText(choice.text))
    }
    return remember(question, figure, choices) {
        buildList {
            add(question.speech.ifEmpty { MathTextTokenizer.plainText(question.text) })
            if (question.figures.isNotEmpty()) add(figure)
            addAll(choices)
        }.joinToString(SPEECH_SEPARATOR)
    }
}

/** The answer for TalkBack: "Doğru seçenek: X.", "Cevap: …" (speech or plain text), "Çözüm: …" and the AI note. */
@Composable
private fun answerSpeech(question: ExamQuestion): String {
    val choice = question.answer.choice
    val correct = if (!choice.isNullOrEmpty()) stringResource(R.string.structured_exam_speech_correct_choice, choice) else null
    val answer = stringResource(
        R.string.structured_exam_speech_answer,
        question.answer.speech ?: MathTextTokenizer.plainText(question.answer.text),
    )
    val solution = if (question.solution.isNotEmpty()) {
        stringResource(
            R.string.structured_exam_speech_solution,
            question.solution.joinToString(SPEECH_SEPARATOR) { MathTextTokenizer.plainText(it) },
        )
    } else {
        null
    }
    val note = stringResource(R.string.structured_exam_ai_note)
    return listOfNotNull(correct, answer, solution, note).joinToString(SPEECH_SEPARATOR)
}

/** "3 soru": the count shown by the question list's header and the Browse row. */
@Composable
fun questionCountText(count: Int): String = pluralStringResource(R.plurals.structured_exam_question_count, count, count)

private val CARD_SHAPE = RoundedCornerShape(16.dp)
private const val MARKER_WIDTH_EMS = 1.6f
private const val SPEECH_SEPARATOR = " "

@ViFiThemePreviews
@Composable
private fun QuestionCardPreview() {
    ViFiTheme {
        var revealed by remember { mutableStateOf(true) }
        QuestionCard(
            question = SampleQuestions.questions()[1],
            isAnswerRevealed = revealed,
            onToggleAnswer = { revealed = !revealed },
            figureImages = QuestionFigureImages.Unavailable,
            modifier = Modifier.padding(16.dp),
        )
    }
}
