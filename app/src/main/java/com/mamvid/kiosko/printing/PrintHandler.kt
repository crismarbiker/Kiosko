package com.mamvid.kiosko.printing

import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import com.mamvid.kiosko.core.utils.Logger

class PrintHandler(private val context: Context) {

    private val tag = "PrintHandler"

    fun printWebView(webView: WebView, jobName: String = "Kiosko") {
        Logger.i(tag, "Starting print job: $jobName")
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            // No forzamos tamaño de papel ni márgenes: el servicio de impresión
            // instalado en el dispositivo (POS, Bluetooth, 58mm) usa sus propios
            // ajustes, igual que hace Firefox.
            printManager.print(jobName, printAdapter, null)
            Logger.i(tag, "Print job submitted")
        } catch (e: Exception) {
            Logger.e(tag, "Print failed", e)
        }
    }
}
