package io.github.korppujauho.pocketts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.korppujauho.pocketts.IdentityRaise
import io.github.korppujauho.pocketts.proto.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The highest level offered by hand: past this, raising takes days even on a fast phone. */
private const val MAX_LEVEL = 40

/**
 * Raising the identity's security level, with its progress and Stop. With [needed] (a server's), it aims there;
 * without, the level is chosen first. The dialog stays open while it works, and the screen stays on.
 */
@Composable
fun RaiseLevelDialog(current: Int, needed: Int? = null, onReached: () -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val progress by IdentityRaise.progress.collectAsState()
    var target by remember { mutableIntStateOf(needed ?: maxOf(current + 1, 24).coerceAtMost(MAX_LEVEL)) }
    var rate by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { rate = withContext(Dispatchers.Default) { IdentityRaise.triesPerSecond() } }
    DisposableEffect(Unit) { onDispose { IdentityRaise.clear() } }
    val p = progress
    val running = p?.running == true
    if (running) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }
    val close = { IdentityRaise.clear(); onClose() }
    TsDialog(
        // Not closed by a tap outside while it works: Stop first
        onDismiss = { if (!running) close() },
        title = if (needed != null) "Security level $needed needed" else "Raise security level",
        icon = Icons.Rounded.Shield,
        subtitle = if (needed != null) "This server needs it. Yours is $current." else "Yours is $current",
        buttons = {
            when {
                running -> PillButton("Stop", PillKind.DANGER) { IdentityRaise.stop() }
                p != null -> PillButton("Close", PillKind.PRIMARY) { close() }
                else -> {
                    PillButton("Cancel") { close() }
                    PillButton("Raise", PillKind.PRIMARY, enabled = target > current) {
                        IdentityRaise.start(context, target) { reached -> if (reached && needed != null) onReached() }
                    }
                }
            }
        },
    ) {
        when {
            p == null -> {
                if (needed == null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        PillButton("−", enabled = target > current + 1) { target-- }
                        Text(
                            "$target", color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 28.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.width(88.dp),
                        )
                        PillButton("+", enabled = target < MAX_LEVEL) { target++ }
                    }
                }
                Desc(
                    rate?.let { "On this phone that takes ${duration(Identity.secondsFor(target, it))} on average. It can be quicker or slower." }
                        ?: "Working out how long it takes on this phone…"
                )
                Desc(
                    "Raising is a one-time job. Your identity stays the same: your groups and permissions stay, and the " +
                        "higher level works on every server. Keep the app open while it works; Stop keeps the level reached so far."
                )
            }
            running -> {
                Text("Level ${p.level} of ${p.target}", color = Ts.bright, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ts.accent, trackColor = Ts.card)
                Desc("Keep the app open. Each level takes about twice as long as the one before.")
            }
            p.level >= p.target -> Desc(
                "Done: your security level is ${p.level}." + if (needed != null) " Connecting again…" else ""
            )
            else -> Desc("Stopped at level ${p.level}. Raising again goes on from there.")
        }
    }
}

/** A rough time in words: "about 3 minutes". */
private fun duration(seconds: Double): String {
    fun about(n: Int, one: String, many: String) = if (n <= 1) "about $one" else "about $n $many"
    return when {
        seconds < 60 -> "under a minute"
        seconds < 3600 -> about((seconds / 60).toInt(), "a minute", "minutes")
        seconds < 48 * 3600 -> about((seconds / 3600).toInt(), "an hour", "hours")
        else -> about((seconds / 86_400).toInt(), "a day", "days")
    }
}
