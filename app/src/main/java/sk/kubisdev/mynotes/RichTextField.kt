package sk.kubisdev.mynotes

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView

/**
 * EditText subclass that exposes the one native hook Compose's key-event APIs can't reliably
 * give us: Backspace pressed while the cursor already sits at offset 0 (nothing local left to
 * delete). Most soft keyboards still dispatch a real KeyEvent for this specific case even
 * though ordinary typing/deleting goes through InputConnection instead - this is the standard
 * Android pattern apps use to implement "backspace merges into the previous block".
 */
class RichEditText(context: Context) : AppCompatEditText(
    // AppCompat widgets refuse to run against a non-AppCompat theme (they only log
    // "...can only be used with a Theme.AppCompat theme", but the tint/emoji helpers
    // are silently degraded). The app itself is pure Compose on a framework Material
    // theme, so the widget gets its own AppCompat context here instead.
    ContextThemeWrapper(context, R.style.Theme_MyNotes_AppCompatWidget)
) {
    var onBackspaceAtStart: (() -> Boolean)? = null
    var onSelectionChanged: ((Int, Int) -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL &&
            selectionStart == 0 && selectionEnd == 0 &&
            text?.isNotEmpty() == true &&
            onBackspaceAtStart?.invoke() == true
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChanged?.invoke(selStart, selEnd)
    }
}

/** Lets the caller command focus on a specific field without Compose's FocusRequester, which
 *  doesn't reliably reach into an AndroidView-hosted native View. */
class RichTextFieldController {
    internal var editText: RichEditText? = null

    fun requestFocus(cursorAtEnd: Boolean = true) {
        val et = editText ?: return
        et.requestFocus()
        if (cursorAtEnd) et.setSelection(et.text?.length ?: 0)
        val imm = et.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
    }

    // Applies a formatting change (from the toolbar) directly onto the live Editable via
    // setSpan/removeSpan, instead of going through the declarative line -> setText() path.
    // setText() replaces the whole native buffer, which resets the IME's composing region and
    // is exactly what must NOT happen on a field the user is actively typing in - targeted span
    // mutation on the SAME Editable object leaves the cursor/IME state completely undisturbed.
    fun refreshSpansFrom(spans: List<SerializableSpanStyle>, scaledDensity: Float) {
        val et = editText ?: return
        val editable = et.text ?: return
        editable.getSpans(0, editable.length, Any::class.java)
            .filter {
                it is StyleSpan || it is UnderlineSpan || it is StrikethroughSpan ||
                        it is ForegroundColorSpan || it is AbsoluteSizeSpan
            }
            .forEach { editable.removeSpan(it) }

        val flags = Spanned.SPAN_EXCLUSIVE_INCLUSIVE
        spans.forEach { span ->
            if (span.start < 0 || span.end > editable.length || span.start >= span.end) return@forEach
            if ((span.fontWeight ?: 0) >= 700) {
                editable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), span.start, span.end, flags)
            }
            if (span.fontStyle == "italic") {
                editable.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), span.start, span.end, flags)
            }
            if (span.textDecoration == "underline") {
                editable.setSpan(UnderlineSpan(), span.start, span.end, flags)
            } else if (span.textDecoration == "linethrough") {
                editable.setSpan(StrikethroughSpan(), span.start, span.end, flags)
            }
            span.color?.let { color ->
                editable.setSpan(ForegroundColorSpan(androidx.compose.ui.graphics.Color(color).toArgb()), span.start, span.end, flags)
            }
            span.fontSize?.let { size ->
                editable.setSpan(AbsoluteSizeSpan((size * scaledDensity).toInt()), span.start, span.end, flags)
            }
        }
    }
}

/**
 * A single line's editable text, backed by a real Android EditText instead of Compose's
 * BasicTextField. Formatting is stored as genuine android.text spans (via NoteLine.toSpannable /
 * Editable.toSerializableSpanStyles) - Android's native text stack has always handled mixed
 * formatting within one field correctly (including mixed font sizes), which is exactly what
 * Compose's AnnotatedString-backed BasicTextField got wrong.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RichTextField(
    line: NoteLine,
    textColor: Color,
    strikeThrough: Boolean,
    cursorColor: Color,
    baseFontSizeSp: Float,
    scaledDensity: Float,
    controller: RichTextFieldController,
    bold: Boolean = false,
    italic: Boolean = false,
    onTextChange: (String, Int) -> Unit,
    onSelectionChange: (Int, Int) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onEnterPressed: () -> Unit,
    onBackspaceOnEmptyLine: () -> Unit,
    onBackspaceAtStart: () -> Boolean,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    val onTextChangeState = rememberUpdatedState(onTextChange)
    val onEnterPressedState = rememberUpdatedState(onEnterPressed)
    val onBackspaceOnEmptyLineState = rememberUpdatedState(onBackspaceOnEmptyLine)
    val onBackspaceAtStartState = rememberUpdatedState(onBackspaceAtStart)
    val onSelectionChangeState = rememberUpdatedState(onSelectionChange)
    val onFocusChangeState = rememberUpdatedState(onFocusChange)
    val lineTypeState = rememberUpdatedState(line.type)

    // The TEXT (not spans - see below) we most recently pushed out of the field via
    // onTextChange, so the next `update` pass (which will see that same content come back in
    // through `line`) knows this is its own edit echoing back, not genuinely external content
    // (remote sync, undo, markdown auto-convert) that needs a full setText().
    val lastEmittedText = remember { mutableStateOf<String?>(null) }
    val suppressWatcher = remember { mutableStateOf(false) }

    // Without this, a field below the fold when the keyboard opens (or one that grows past
    // the visible area while typing) stays hidden behind the IME - the user has to manually
    // scroll the list to even see what they're typing.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // bringIntoView() with no explicit rect only scrolls the MINIMUM amount needed for this
    // field's own bounds to be technically inside the viewport - so a field one line tall
    // ends up scrolled to sit exactly flush with the top of the keyboard, cursor included,
    // which still reads as "hidden under the keyboard" to the user. Requesting a taller rect
    // than the field's actual bounds (extending below it) forces the extra scroll needed to
    // leave real breathing room above the IME.
    var fieldSizePx by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val extraBottomPx = with(LocalDensity.current) { 64.dp.toPx() }
    fun scrollIntoView() {
        coroutineScope.launch {
            // Target the CURSOR's line, not the whole field: a paragraph taller than the
            // space left above the keyboard can never fully fit, in which case bringIntoView
            // aligns its top edge and leaves the cursor (at the bottom) still covered by the
            // IME. The single line the cursor sits on always fits.
            val et = controller.editText
            val layout = et?.layout
            val rect = if (layout != null) {
                val cursorLine = layout.getLineForOffset(et.selectionEnd.coerceAtLeast(0))
                val top = layout.getLineTop(cursorLine).toFloat() + et.paddingTop
                val bottom = layout.getLineBottom(cursorLine).toFloat() + et.paddingTop
                androidx.compose.ui.geometry.Rect(
                    left = 0f,
                    top = top,
                    right = fieldSizePx.width,
                    bottom = bottom + extraBottomPx
                )
            } else {
                androidx.compose.ui.geometry.Rect(
                    left = 0f,
                    top = 0f,
                    right = fieldSizePx.width,
                    bottom = fieldSizePx.height + extraBottomPx
                )
            }
            bringIntoViewRequester.bringIntoView(rect)
        }
    }

    // A fixed delay-then-scroll after focus is unreliable: it can fire before the IME has
    // actually finished resizing the window, so bringIntoView() computes against the OLD
    // (pre-keyboard) viewport and thinks the field is already visible. Reacting to the real
    // WindowInsets IME-visibility change gets the timing roughly right, but isImeVisible
    // flips true as soon as the show animation STARTS, not once it's done - the IME inset
    // height (and therefore imePadding()'s resize of this column) keeps growing for another
    // ~250-300ms after that. A single bringIntoView() shortly after the flag flips was
    // landing mid-animation, so the field looked scrolled into view for a moment and then
    // got covered again as the keyboard kept rising. Waiting out the full show animation
    // (and re-checking once more after) fixes that without needing to track the live inset.
    var isFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, isFocused) {
        if (imeVisible && isFocused) {
            delay(300)
            scrollIntoView()
            delay(150)
            scrollIntoView()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { fieldSizePx = androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) }
            .bringIntoViewRequester(bringIntoViewRequester),
        factory = { context ->
            RichEditText(context).apply {
                // Constructed directly (not inflated from XML), so none of the default
                // EditText style attributes (focusableInTouchMode, clickable, ...) are
                // guaranteed to already be set - without these, taps land but never actually
                // grant focus or open the keyboard.
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                isLongClickable = true
                isEnabled = true
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                background = null
                setPadding(0, 0, 0, 0)
                setLineSpacing(0f, 1.15f)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI

                addTextChangedListener(object : TextWatcher {
                    var wasEmpty = false
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        wasEmpty = s?.isEmpty() == true
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(editable: Editable) {
                        if (suppressWatcher.value) return

                        if (lineTypeState.value != LineType.TEXT && editable.contains('\n')) {
                            val cleaned = editable.toString().replace("\n", "")
                            suppressWatcher.value = true
                            editable.replace(0, editable.length, cleaned)
                            suppressWatcher.value = false
                            onEnterPressedState.value()
                            return
                        }

                        if (editable.isEmpty() && wasEmpty) {
                            // Deferred: the callback typically removes this line (and thus this
                            // very EditText) from the Compose state list. Doing that
                            // synchronously here, while we're still inside the native
                            // TextWatcher dispatch for this view, tears the view down
                            // mid-callback and crashes. Posting lets the current dispatch
                            // finish first.
                            post { onBackspaceOnEmptyLineState.value() }
                            return
                        }

                        val text = editable.toString()
                        lastEmittedText.value = text
                        onTextChangeState.value(text, selectionStart.coerceAtLeast(0))
                        scrollIntoView()
                    }
                })

                setOnFocusChangeListener { _, hasFocus ->
                    onFocusChangeState.value(hasFocus)
                    isFocused = hasFocus
                    if (hasFocus) scrollIntoView()
                }
                onSelectionChanged = { start, end ->
                    onSelectionChangeState.value(start, end)
                    // Tapping deeper into an already-focused tall paragraph moves only the
                    // cursor - no focus change, no IME-visibility flip, no text change - so
                    // none of the other scroll triggers fire and the tapped spot can sit
                    // under the keyboard. Only react while focused: setText() during initial
                    // load also moves the selection, and scrolling then would yank the list.
                    if (isFocused) scrollIntoView()
                }
            }
        },
        update = { editText ->
            editText.onBackspaceAtStart = onBackspaceAtStartState.value
            controller.editText = editText

            editText.setTextColor(textColor.toArgb())
            editText.setHintTextColor(textColor.copy(alpha = 0.4f).toArgb())
            editText.hint = hint
            editText.highlightColor = cursorColor.copy(alpha = 0.35f).toArgb()
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseFontSizeSp)
            editText.setTypeface(
                android.graphics.Typeface.defaultFromStyle(
                    when {
                        bold && italic -> android.graphics.Typeface.BOLD_ITALIC
                        bold -> android.graphics.Typeface.BOLD
                        italic -> android.graphics.Typeface.ITALIC
                        else -> android.graphics.Typeface.NORMAL
                    }
                )
            )

            if (line.content == lastEmittedText.value) {
                // Our own edit (or a spans-only change from the toolbar) echoing back through
                // recomposition - the text itself is already correct in the live Editable, so
                // only refresh spans in place. This is deliberately NOT gated on spans actually
                // having changed: comparing span lists for equality is order-sensitive
                // (cleanupOverlappingSpans/mergeAdjacentSpans reorder them), so that check was
                // almost always "different" even for the field's own untouched formatting -
                // triggering a full setText() on every keystroke, which resets the native
                // buffer and broke the IME's composing state (typing/selecting looked totally
                // broken). Reapplying the same spans is cheap and touches nothing IME-related.
                controller.refreshSpansFrom(line.spanStyles, scaledDensity)
            } else {
                // Genuinely external content: initial load, remote collaborative sync, undo,
                // or a markdown auto-convert ("__", "- ", "[]") rewriting this line's content.
                val preservedSelection = editText.selectionStart.coerceIn(0, line.content.length)
                suppressWatcher.value = true
                editText.setText(line.toSpannable(scaledDensity))
                editText.setSelection(preservedSelection)
                suppressWatcher.value = false
                lastEmittedText.value = line.content
            }

            if (strikeThrough) {
                editText.paintFlags = editText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                editText.paintFlags = editText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }
    )
}
