package com.minka.app
import android.content.Intent
import androidx.activity.ComponentActivity
import com.google.zxing.integration.android.IntentIntegrator

class QrScanner(private val activity: ComponentActivity) {
    fun initiateQrScan() {
        IntentIntegrator(activity)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setBeepEnabled(true)
            .setOrientationLocked(true)
            .initiateScan()
    }

    fun handleResult(requestCode: Int, resultCode: Int, data: Intent?) =
        IntentIntegrator.parseActivityResult(requestCode, resultCode, data)?.contents
}
