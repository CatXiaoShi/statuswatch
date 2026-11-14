package luolang.status.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import luolang.status.R

private const val APP_NAME = "洛水流汐"
private const val AUTHOR = "洛狼LewoWoff"
private const val QQ = "1504273583"
private const val EMAIL = "lewowoff@foxmail.com"
private const val GITHUB = "https://github.com/CatXiaoShi"
private const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(16.dp))
                SoftDivider()
                Spacer(Modifier.height(12.dp))

                InfoRow("作者", AUTHOR)
                InfoRow("QQ", QQ)
                InfoRow("E-Mail", EMAIL, "mailto:$EMAIL")
                InfoRow("Github", GITHUB, GITHUB)

                Spacer(Modifier.height(10.dp))
                val uriHandler = LocalUriHandler.current
                Text(
                    text = "开源协议 · Apache License 2.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching { uriHandler.openUri(LICENSE_URL) }
                        },
                )

                Spacer(Modifier.height(16.dp))
                SoftDivider()
                Spacer(Modifier.height(12.dp))

                DisclaimerBlock(
                    title = "《免责声明》",
                    titleStyle = MaterialTheme.typography.titleMedium,
                    bodyStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SoftDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@Composable
private fun InfoRow(label: String, value: String, uri: String? = null) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (uri != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (uri != null) TextDecoration.Underline else null,
            modifier = Modifier.then(
                if (uri != null) {
                    Modifier.clickable { runCatching { uriHandler.openUri(uri) } }
                } else {
                    Modifier
                },
            ),
        )
    }
}
