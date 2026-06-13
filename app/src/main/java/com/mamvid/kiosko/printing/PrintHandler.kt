package com.mamvid.kiosko.printing

import android.content.Context
import android.print.PrintAttributes
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
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(
                    PrintAttributes.Resolution("default", "Default", 300, 300)
                )
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(jobName, printAdapter, printAttributes)
            Logger.i(tag, "Print job submitted")
        } catch (e: Exception) {
            Logger.e(tag, "Print failed", e)
        }
    }
}
