package cc.toolcrate.telos.ui.components

import cc.toolcrate.telos.ui.theme.ScreenTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.toolcrate.telos.ui.theme.Garamond
import cc.toolcrate.telos.ui.theme.LocalPaper

/** A screen's title, in Garamond, marked as a heading. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
        Text(text, style = ScreenTitle, color = LocalPaper.current.ink, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LocalPaper.current.muted) }
    }
}

/** A group heading inside a list ("Earlier", "Today", a section name). */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.8f),
            color = LocalPaper.current.ink,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** A quiet empty state: a line of Garamond italic, no illustrations, no pressure. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            text,
            fontFamily = Garamond, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.titleMedium,
            color = LocalPaper.current.muted, textAlign = TextAlign.Center,
        )
    }
}
