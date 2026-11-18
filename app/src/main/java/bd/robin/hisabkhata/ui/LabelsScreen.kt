package bd.robin.hisabkhata.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bd.robin.hisabkhata.R
import bd.robin.hisabkhata.data.BarcodeArt
import bd.robin.hisabkhata.logic.formatTaka

@Composable
fun LabelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val products by container.repo.observeProducts().collectAsStateWithLifecycle(emptyList())
    val labeled = products.filter { it.barcode.isNotBlank() }
    Scaffold(topBar = { SimpleBar(stringResource(R.string.label_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).tag("label_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.label_help), style = MaterialTheme.typography.bodyMedium)
            if (labeled.isEmpty()) {
                EmptyHint(stringResource(R.string.label_empty))
            }
            labeled.forEach { product ->
                val bitmap = BarcodeArt.label(product.name, formatTaka(product.price), product.barcode) ?: return@forEach
                Column(Modifier.fillMaxWidth().tag("label_${product.id}")) {
                    Image(bitmap.asImageBitmap(), product.name, modifier = Modifier.fillMaxWidth().height(150.dp))
                    BigOutlined(stringResource(R.string.label_print)) {
                        BarcodeArt.share(context, bitmap, "label-${product.barcode}.png")
                    }
                }
            }
        }
    }
}
