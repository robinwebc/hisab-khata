package bd.robin.hisabkhata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.Boot
import bd.robin.hisabkhata.data.ShopBook
import bd.robin.hisabkhata.data.booksToString
import bd.robin.hisabkhata.data.parseBooks
import kotlinx.coroutines.launch
import java.util.UUID

val HeaderRed = Color(0xFFE53935)
val SheetGrey = Color(0xFFF3F4F6)

@Composable
fun RedHeader(badge: Int, onInbox: () -> Unit, onSupport: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val boot by container.settings.boot.collectAsStateWithLifecycle()
    val settings = (boot as? Boot.Ready)?.settings
    val fallback = settings?.shopName?.ifBlank { stringResource(R.string.shop_fallback) } ?: stringResource(R.string.shop_fallback)
    val books = parseBooks(settings?.businessBook.orEmpty(), fallback)
    val active = books.firstOrNull { it.id == settings?.activeBusinessId } ?: books.first()
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(HeaderRed).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Surface(
                    onClick = { open = true },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    modifier = Modifier.tag("business_switcher"),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(active.name, color = Color(0xFF222222), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Rounded.ArrowDropDown, null, tint = Color(0xFF444444))
                    }
                }
                Text(active.plan, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(start = 16.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
            Box {
                IconButton(onClick = onInbox, modifier = Modifier.tag("open_inbox")) {
                    Icon(Icons.Rounded.Mail, stringResource(R.string.inbox_title), tint = Color.White)
                }
                if (badge > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(badge.coerceAtMost(9).toString(), color = HeaderRed, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            IconButton(onClick = onSupport, modifier = Modifier.tag("open_support")) {
                Icon(Icons.Rounded.HeadsetMic, stringResource(R.string.support_title), tint = Color.White)
            }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.business_pick)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    books.forEach { book ->
                        TextButton(onClick = {
                            scope.launch {
                                container.repo.bindBusiness(book.id)
                                container.settings.update(notify = false) {
                                    it.copy(activeBusinessId = book.id, shopName = book.name)
                                }
                            }
                            open = false
                        }, modifier = Modifier.tag("biz_${book.id}")) {
                            Text(book.name + if (book.id == active.id) "  ✓" else "")
                        }
                    }
                    TextButton(onClick = { adding = true }) { Text(stringResource(R.string.business_add)) }
                    if (adding) {
                        OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.profile_shop)) }, modifier = Modifier.fillMaxWidth())
                        TextButton(onClick = {
                            if (newName.isBlank()) return@TextButton
                            val id = UUID.randomUUID().toString().take(8)
                            val next = books + ShopBook(id, newName.trim(), "স্ট্যান্ডার্ড")
                            scope.launch {
                                container.repo.bindBusiness(id)
                                container.settings.update(notify = false) {
                                    it.copy(businessBook = booksToString(next), activeBusinessId = id, shopName = newName.trim())
                                }
                            }
                            open = false
                        }) { Text(stringResource(R.string.save)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
fun QrHubScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(SheetGrey)) {
        RedHeader(badge = 0, onInbox = { onOpen("inbox") }, onSupport = { onOpen("support") })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.qr_hub_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.qr_hub_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton(stringResource(R.string.quick_sell), modifier = Modifier.tag("qr_quick_sell")) { onOpen("quicksell") }
            BigOutlined(stringResource(R.string.face_scan_title), modifier = Modifier.tag("qr_face")) { onOpen("scan/face?partyId=") }
            BigOutlined(stringResource(R.string.share_app), modifier = Modifier.tag("qr_share")) { onOpen("share") }
        }
    }
}

@Composable
fun InfoScreen(title: String, body: String, onBack: () -> Unit) {
    Scaffold(topBar = { SimpleBar(title, onBack) }) { padding ->
        Text(body, modifier = Modifier.padding(padding).padding(20.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FeatureGlyph(icon: ImageVector, label: String, tint: Color, tag: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.tag(tag)) {
        Surface(onClick = onClick, shape = CircleShape, color = tint.copy(alpha = 0.12f), modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
    }
}
