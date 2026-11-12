package ca.cgagnier.wlednativeandroid.ui.homeScreen.share

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.net.toUri
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private val QR_SIZE = 220.dp
private const val QR_PIXELS = 512
private const val QR_MARGIN_MODULES = 1

/**
 * "Share Parol": a QR code another phone on the same WiFi scans (Sinag → menu → Scan Parol QR code, or the
 * phone's camera) to add this lantern. It holds the lantern's address as a `wled://` link; no password or
 * account is involved, since anyone on the home WiFi can already reach the lantern.
 */
@Composable
fun ShareDeviceDialog(device: Device, onDismiss: () -> Unit) {
    val link = DeepLinkHandler.shareLink(device.address)
    val qr = remember(link) { qrBitmap(link) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title, deviceName(device))) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qr,
                    contentDescription = stringResource(R.string.share_qr_description, deviceName(device)),
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .size(QR_SIZE),
                )
                Text(
                    text = stringResource(R.string.share_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/** Black-on-white QR code for [text]; the white background comes from the surrounding Modifier. */
private fun qrBitmap(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        QR_PIXELS,
        QR_PIXELS,
        mapOf(EncodeHintType.MARGIN to QR_MARGIN_MODULES),
    )
    val bitmap: Bitmap = createBitmap(matrix.width, matrix.height)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap[x, y] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return bitmap.asImageBitmap()
}

/**
 * Scans a "Share Parol" QR code with Google's code scanner (Play services; the app needs no camera
 * permission) and opens it, which adds the lantern and shows it.
 */
fun scanShareCode(context: Context) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode ->
            val raw = barcode.rawValue.orEmpty()
            if (raw.startsWith("wled://", ignoreCase = true)) {
                context.startActivity(DeepLinkHandler.createViewIntent(context, raw.toUri()))
            } else {
                Toast.makeText(context, R.string.share_scan_not_parol, Toast.LENGTH_LONG).show()
            }
        }
        .addOnFailureListener {
            Toast.makeText(context, R.string.share_scan_unavailable, Toast.LENGTH_LONG).show()
        }
}
