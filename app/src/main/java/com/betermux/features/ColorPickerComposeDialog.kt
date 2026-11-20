package com.betermux.features

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.betermux.compose.ComposeDialogHost
import com.betermux.theme.LocalWarpPalette
import com.termux.R
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val OPAQUE_ALPHA_MASK = 0xFF000000.toInt()
private val HEX_COLOR_REGEX = Regex("^#?([0-9A-Fa-f]{6})$")

/** Compose UI behind the existing Java builder API. */
object ColorPickerComposeDialog {
    @JvmStatic
    fun show(context: Context, initialColor: Int, listener: ColorPickerDialog.OnColorSelectedListener?) {
        ComposeDialogHost.show(context, widthFraction = 0.94f) { dialog ->
            val prefs = remember(context) { context.getSharedPreferences("betermux_theme", Context.MODE_PRIVATE) }
            val initialStyle = remember(prefs) {
                if (prefs.getString("color_picker_style", "hsv") == "hsv") "hsv" else "rgb"
            }
            ColorPickerContent(
                initialColor = initialColor,
                initialStyle = initialStyle,
                onStyleChange = { prefs.edit().putString("color_picker_style", it).apply() },
                onDismiss = dialog::dismiss,
                onConfirm = { picked ->
                    listener?.onColorSelected(picked)
                    dialog.dismiss()
                },
            )
        }
    }
}

@Composable
internal fun ColorPickerContent(
    initialColor: Int,
    initialStyle: String,
    onStyleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val opaqueInitial = initialColor or OPAQUE_ALPHA_MASK
    val initialHsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(opaqueInitial, it) } }

    var style by remember { mutableStateOf(if (initialStyle == "hsv") "hsv" else "rgb") }
    var editing by remember { mutableStateOf(false) }
    var color by remember { mutableIntStateOf(opaqueInitial) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(hexColor(opaqueInitial)) }

    fun applyHsv(nextHue: Float, nextSaturation: Float, nextBrightness: Float) {
        hue = nextHue
        saturation = nextSaturation.coerceIn(0f, 1f)
        brightness = nextBrightness.coerceIn(0f, 1f)
        val nextColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) or OPAQUE_ALPHA_MASK
        color = nextColor
        hex = hexColor(nextColor)
    }

    fun applyColor(nextColor: Int, syncHex: Boolean) {
        val opaque = nextColor or OPAQUE_ALPHA_MASK
        color = opaque
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(opaque, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
        if (syncHex) {
            hex = hexColor(opaque)
        }
    }

    val palette = LocalWarpPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(
                if (!editing) R.string.color_picker_style_title
                else if (style == "hsv") R.string.color_picker_hsv_title
                else R.string.color_picker_rgb_title,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!editing) {
                Column(modifier = Modifier.selectableGroup()) {
                    StyleRow(stringResource(R.string.color_picker_hsv_option), style == "hsv") { style = "hsv" }
                    StyleRow(stringResource(R.string.color_picker_rgb_option), style == "rgb") { style = "rgb" }
                }
            } else {
                val previewDescription = stringResource(R.string.color_picker_preview)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(color))
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = previewDescription },
                )
                if (style == "hsv") {
                    HsvWheel(
                        hue = hue,
                        saturation = saturation,
                        brightness = brightness,
                        onChange = { nextHue, nextSaturation -> applyHsv(nextHue, nextSaturation, brightness) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    val brightnessLabel = stringResource(R.string.color_picker_brightness)
                    Text(brightnessLabel, color = palette.textPrimary)
                    Slider(
                        value = brightness,
                        onValueChange = { value -> applyHsv(hue, saturation, value) },
                        modifier = Modifier.semantics { contentDescription = brightnessLabel },
                    )
                } else {
                    val rgb = intArrayOf(
                        AndroidColor.red(color),
                        AndroidColor.green(color),
                        AndroidColor.blue(color),
                    )
                    val labels = listOf(
                        stringResource(R.string.color_picker_red),
                        stringResource(R.string.color_picker_green),
                        stringResource(R.string.color_picker_blue),
                    )
                    labels.forEachIndexed { index, label ->
                        Text("$label  ${rgb[index]}", color = palette.textPrimary)
                        Slider(
                            value = rgb[index].toFloat(),
                            onValueChange = { value ->
                                val next = rgb.copyOf()
                                next[index] = value.roundToInt().coerceIn(0, 255)
                                applyColor(AndroidColor.rgb(next[0], next[1], next[2]), syncHex = true)
                            },
                            valueRange = 0f..255f,
                            modifier = Modifier.semantics { contentDescription = label },
                        )
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { value ->
                        hex = value
                        parseHexColor(value)?.let { parsed ->
                            applyColor(parsed, syncHex = false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.color_picker_hex_label)) },
                    isError = hex.isNotBlank() && parseHexColor(hex) == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                    ),
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.color_picker_cancel)) }
            if (!editing) {
                Button(onClick = { onStyleChange(style); editing = true }) {
                    Text(stringResource(R.string.color_picker_next))
                }
            } else {
                Button(onClick = { onConfirm(color or OPAQUE_ALPHA_MASK) }) {
                    Text(stringResource(R.string.color_picker_ok))
                }
            }
        }
    }
}

@Composable
private fun StyleRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val palette = LocalWarpPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, color = palette.textPrimary)
    }
}

@Composable
private fun HsvWheel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wheelDescription = stringResource(R.string.color_picker_wheel_description)
    val currentOnChange by rememberUpdatedState(onChange)
    val hues = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map {
            Color(AndroidColor.HSVToColor(floatArrayOf(it, 1f, 1f)))
        }
    }
    Canvas(
        modifier
            .widthIn(max = 240.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
            .semantics { contentDescription = wheelDescription }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    updateWheel(down.position, size.width.toFloat(), size.height.toFloat(), currentOnChange)
                    drag(down.id) { change ->
                        change.consume()
                        updateWheel(change.position, size.width.toFloat(), size.height.toFloat(), currentOnChange)
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Brush.sweepGradient(hues, center = center), radius, center)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, radius), radius, center)
        drawCircle(Color.Black.copy(alpha = 1f - brightness.coerceIn(0f, 1f)), radius, center)
        val angle = Math.toRadians(hue.toDouble())
        val point = Offset(
            center.x + cos(angle).toFloat() * saturation * radius,
            center.y + sin(angle).toFloat() * saturation * radius,
        )
        drawCircle(Color.Black, 10.dp.toPx(), point, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, 8.dp.toPx(), point, style = Stroke(2.dp.toPx()))
    }
}

private fun updateWheel(point: Offset, width: Float, height: Float, onChange: (Float, Float) -> Unit) {
    val radius = minOf(width, height) / 2f
    if (radius <= 0f) return
    val dx = point.x - width / 2f
    val dy = point.y - height / 2f
    val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
    onChange(hue, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
}

internal fun parseHexColor(input: String): Int? {
    val match = HEX_COLOR_REGEX.matchEntire(input.trim()) ?: return null
    return AndroidColor.parseColor("#${match.groupValues[1]}") or OPAQUE_ALPHA_MASK
}

private fun hexColor(color: Int) = "#%06X".format(Locale.US, 0xFFFFFF and color)
