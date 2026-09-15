package com.muradgalayev.brainbuddy.ui.together

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.TogetherRepository
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MyQrState(
    val qr: ImageBitmap? = null,
    val loading: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

// the QR is just the invite link drawn as a square. scanning it opens the same confirmation a tapped link does
@HiltViewModel
class MyQrCodeViewModel @Inject constructor(
    private val repository: TogetherRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val username: String? = authRepository.getCurrentUserUsername()?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(MyQrState())
    val state: StateFlow<MyQrState> = _state.asStateFlow()

    // minted once per sheet: the token is single-use, so a fresh open gets a fresh code
    fun load() {
        val current = _state.value
        if (current.loading || current.qr != null) return
        _state.value = MyQrState(loading = true)
        viewModelScope.launch {
            repository.createInviteLink(ConnectionRelation.FRIEND)
                .onSuccess { link ->
                    val qr = withContext(Dispatchers.Default) { qrBitmap(link.url) }
                    _state.value = MyQrState(qr = qr)
                }
                .onFailure { _state.value = MyQrState(errorRes = R.string.together_invite_create_failed) }
        }
    }

    // false when the code isn't one of ours
    fun onScanned(raw: String?): Boolean {
        val opened = raw != null && repository.openPastedInvite(raw)
        if (!opened) _state.update { it.copy(errorRes = R.string.together_qr_not_invite) }
        return opened
    }

    fun onScannerFailed() {
        _state.update { it.copy(errorRes = R.string.together_qr_scanner_failed) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyQrCodeSheet(
    onDismiss: () -> Unit,
    viewModel: MyQrCodeViewModel = hiltViewModel(),
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    // drag-down-to-dismiss comes from the sheet itself
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { viewModel.load() }

    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.together_qr_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = {
                        startQrScan(
                            context = context,
                            onResult = { raw -> if (viewModel.onScanned(raw)) close() },
                            onFailure = viewModel::onScannerFailed,
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.together_qr_scan), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(24.dp))

            // always black on white, whatever the theme, or some scanners won't read it
            Box(
                modifier = Modifier
                    .size(248.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val qr = state.qr
                when {
                    qr != null -> Image(
                        bitmap = qr,
                        contentDescription = stringResource(R.string.together_qr_image),
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.loading -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black,
                    )
                    else -> TextButton(onClick = viewModel::load) {
                        Text(stringResource(R.string.common_try_again), color = Color.Black)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            viewModel.username?.let {
                Text(
                    "@$it",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.together_qr_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            state.errorRes?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Google's scanner UI runs in Play services, so no camera permission of our own. cancelling is silent
private fun startQrScan(context: Context, onResult: (String?) -> Unit, onFailure: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { onResult(it.rawValue) }
        .addOnFailureListener { onFailure() }
}

private fun qrBitmap(content: String, size: Int = 720): ImageBitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 0,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}
