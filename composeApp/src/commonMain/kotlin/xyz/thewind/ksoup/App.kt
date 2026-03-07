package xyz.thewind.ksoup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
@Preview
fun App() {
    val sampleHtml = remember {
        """
        <html>
          <head><title>Ksoup Demo</title></head>
          <body>
            <section id="news">
              <article class="card featured" data-kind="release">
                <h2>Ksoup milestone</h2>
                <p>Parse HTML on Android, iOS and Desktop.</p>
              </article>
              <article class="card" data-kind="note">
                <h2>Selector support</h2>
                <p>Tag, id, class, attribute, descendant and child are ready.</p>
              </article>
            </section>
          </body>
        </html>
        """.trimIndent()
    }
    val document = remember(sampleHtml) { Jsoup.parse(sampleHtml) }
    val featured = remember(document) { document.select("#news > article.featured").first() }

    MaterialTheme {
        Surface(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Ksoup", style = MaterialTheme.typography.headlineMedium)
                Text("Title: ${document.title()}", style = MaterialTheme.typography.titleMedium)
                Text("Featured: ${featured?.select("h2")?.text().orEmpty()}")
                Text("Summary: ${featured?.select("p")?.text().orEmpty()}")
                Text("Cards found: ${document.select("article.card").size}")
                Text("Body text: ${document.body().text()}")
            }
        }
    }
}
