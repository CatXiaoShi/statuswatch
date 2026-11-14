package luolang.status.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val DISCLAIMER_LINES = listOf(
    "1. 本软件涉及 Root 权限和底层系统级操作。使用本软件造成的任何设备损坏（如变砖、死机、硬件烧毁）、数据丢失，均由使用者自行承担。",
    "2. 作者不对任何因使用本软件引起的直接或间接损失负责。",
    "3. 请务必在充分了解风险的情况下使用，建议在备用机上进行测试。",
)

/** 免责声明区块，About 弹窗与 Root 拦截页共用。 */
@Composable
fun DisclaimerBlock(
    modifier: Modifier = Modifier,
    title: String = "免责声明",
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    bodyStyle: TextStyle = MaterialTheme.typography.bodySmall,
    bodyColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        DISCLAIMER_LINES.forEach { line ->
            Text(
                text = line,
                style = bodyStyle,
                color = bodyColor,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
