package com.mamvid.kiosko.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.webkit.WebView
import com.mamvid.kiosko.core.utils.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class PrintHandler(private val context: Context) {

    private val tag = "PrintHandler"
    private var activeAdapter: PrintDocumentAdapter? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun warmUp() {
        try {
            val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.printJobs
        } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    fun printWebViewNative(webView: WebView, jobName: String, onFinished: () -> Unit) {
        Logger.i(tag, "Print nativo: $jobName")
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val native = webView.createPrintDocumentAdapter(jobName)
            val wrapped = object : PrintDocumentAdapter() {
                override fun onStart() = native.onStart()
                override fun onLayout(
                    old: PrintAttributes?, new: PrintAttributes,
                    signal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?
                ) = native.onLayout(old, new, signal, callback, extras)
                override fun onWrite(
                    pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                    signal: CancellationSignal, callback: WriteResultCallback
                ) = native.onWrite(pages, destination, signal, callback)
                override fun onFinish() {
                    native.onFinish()
                    mainHandler.post(onFinished)
                }
            }
            printManager.print(jobName, wrapped, null)
            Logger.i(tag, "Trabajo nativo enviado")
        } catch (e: Exception) {
            Logger.e(tag, "Print nativo falló", e)
        }
    }

    /**
     * Captura el contenido del WebView como bitmap, lo embebe en un PDF Letter a 165pt
     * de ancho (= 58mm = 27% de Letter), y abre el diálogo de impresión con ese PDF
     * pre-generado.
     *
     * Por qué funciona con UniversalPrintService (UPS):
     *   • UPS detecta el contenido en el 27% izquierdo de Letter y escala ×1.86
     *   • Resultado: etiqueta de 58mm con el mismo tamaño de letra que Firefox
     *
     * Por qué no usar el adapter nativo del WebView con Handler.post():
     *   • UPS espera el callback de onLayout() en su propio hilo
     *   • Si no respondemos inmediatamente, UPS queda colgado (como se verificó en prueba)
     *
     * Por qué se pre-genera el PDF aquí (no en el callback):
     *   • Evita la re-entrancy en Chromium que causaba crashes al llamar
     *     webViewAdapter.onLayout() dentro de un callback de impresión
     *   • El PDF está listo ANTES de que UPS empiece a comunicarse con nuestro adapter
     */
    @Suppress("DEPRECATION")
    fun printWebView(webView: WebView, jobName: String = "Kiosko") {
        Logger.i(tag, "Print job: $jobName")
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

            // Capturar el contenido actual del WebView (rápido, en UI thread).
            // El WebView debe estar en un tamaño angosto (≈220 CSS px = 58mm) para que
            // el ticket llene todo el viewport y el bitmap tenga el texto al tamaño correcto.
            @Suppress("DEPRECATION")
            val picture = webView.capturePicture()
            val picW = picture?.width ?: 0
            val picH = picture?.height ?: 0
            Logger.i(tag, "capturePicture: ${picW}×${picH}")

            if (picture == null || picW < 10 || picH < 10) {
                Logger.w(tag, "capturePicture vacío — fallback adapter estándar")
                printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), null)
                return
            }

            // El resto (bitmap + PDF) en hilo de fondo para no bloquear el UI thread.
            Thread {
                try {
                    // Renderizar a 2× y luego binarizar a B&W puro.
                    // La binarización elimina el anti-aliasing (píxeles grises) que hace los
                    // trazos aparecer delgados en impresoras térmicas. Resultado: trazos sólidos
                    // como vectores. El PDF B&W también comprime mejor → UPS más rápido.
                    val bmpScale = 2f
                    val bmpW = (picW * bmpScale).toInt().coerceAtLeast(1)
                    val bmpH = (picH * bmpScale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val bmpCanvas = Canvas(bitmap)
                    bmpCanvas.drawColor(Color.WHITE)
                    bmpCanvas.scale(bmpScale, bmpScale)
                    picture.draw(bmpCanvas)

                    // Binarizar: píxeles con luminancia < 70% → negro, resto → blanco.
                    // lum = R*299 + G*587 + B*114 (escala 0–255000); umbral = 180000 ≈ 70%.
                    val pixels = IntArray(bmpW * bmpH)
                    bitmap.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
                    for (i in pixels.indices) {
                        val c = pixels[i]
                        val lum = Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114
                        pixels[i] = if (lum < 210_000) Color.BLACK else Color.WHITE
                    }
                    bitmap.setPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)

                    Logger.i(tag, "Bitmap B&W listo: ${bmpW}×${bmpH}")

                    // PDF a 58mm de ancho exacto → UPS mapea 1:1 al papel térmico de 58mm.
                    // Con Letter (216mm) UPS escalaría ×0.27 → texto ilegible.
                    // 1 mil = 1/1000 pulgada; 58mm = 2283 mils.
                    val widthMils = 2283
                    val heightMils = (bmpH.toFloat() / bmpW * widthMils).toInt().coerceIn(100, 200_000)
                    val ticketAttrs = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize("thermal_58mm", "Ticket 58mm", widthMils, heightMils))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    val pdfDoc = PrintedPdfDocument(context, ticketAttrs)
                    val page = pdfDoc.startPage(0)
                    page.canvas.drawBitmap(
                        bitmap, null,
                        RectF(0f, 0f, page.info.pageWidth.toFloat(), page.info.pageHeight.toFloat()), null
                    )
                    pdfDoc.finishPage(page)
                    bitmap.recycle()

                    val tempFile = File(context.cacheDir, "kiosko_${System.currentTimeMillis()}.pdf")
                    tempFile.outputStream().use { pdfDoc.writeTo(it) }
                    pdfDoc.close()
                    Logger.i(tag, "PDF listo: ${tempFile.length()}B — ${widthMils}×${heightMils} mils")

                    // Try direct BT printing via PrinterService API (no dialog, no UPS dependency).
                    // Falls back to PrintManager if PrinterService is not running.
                    val printedDirect = tryPrintViaPrinterService(tempFile)
                    if (printedDirect) {
                        tempFile.delete()
                        Logger.i(tag, "Impresión directa completada (PrinterService)")
                        return@Thread
                    }

                    Logger.i(tag, "PrinterService no disponible — fallback a PrintManager")
                    mainHandler.post {
                        activeAdapter = object : PrintDocumentAdapter() {
                            override fun onLayout(
                                old: PrintAttributes?, new: PrintAttributes,
                                signal: CancellationSignal, callback: LayoutResultCallback,
                                extras: Bundle?
                            ) {
                                if (signal.isCanceled) { callback.onLayoutCancelled(); return }
                                callback.onLayoutFinished(
                                    PrintDocumentInfo.Builder(jobName)
                                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                        .setPageCount(1).build(), true
                                )
                            }

                            override fun onWrite(
                                pages: Array<out PageRange>,
                                destination: ParcelFileDescriptor,
                                signal: CancellationSignal,
                                callback: WriteResultCallback
                            ) {
                                if (signal.isCanceled) { callback.onWriteCancelled(); return }
                                try {
                                    tempFile.inputStream().use { inp ->
                                        ParcelFileDescriptor.AutoCloseOutputStream(destination)
                                            .use { out -> inp.copyTo(out) }
                                    }
                                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                                    Logger.i(tag, "onWrite completado")
                                } catch (e: Exception) {
                                    Logger.e(tag, "onWrite falló", e)
                                    callback.onWriteFailed(e.message)
                                }
                            }

                            override fun onFinish() {
                                tempFile.delete()
                                activeAdapter = null
                            }
                        }
                        // null attrs → UPS muestra el botón de imprimir inmediatamente
                        // sin buscar impresora compatible con un media custom.
                        // La impresora térmica ya sabe que es 58mm — no necesita que UPS se lo diga.
                        printManager.print(jobName, activeAdapter!!, null)
                        Logger.i(tag, "Trabajo enviado a PrintManager")
                    }
                } catch (e: Exception) {
                    Logger.e(tag, "Error generando PDF en hilo de fondo", e)
                }
            }.start()
        } catch (e: Exception) {
            Logger.e(tag, "Print falló", e)
        }
    }

    // Sends the PDF file to PrinterService's local HTTP API (localhost:8765/print-pdf).
    // PrinterService renders the PDF and sends it directly to the BT thermal printer,
    // bypassing Android's PrintManager and UPS entirely — no dialog, no cold-start delay.
    // Returns true if the print was accepted and completed; false if PrinterService is
    // unavailable (not running), so the caller can fall back to PrintManager.
    private fun tryPrintViaPrinterService(pdfFile: File): Boolean {
        return try {
            val url = URL("http://localhost:8765/print-pdf")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2_000   // fail fast if PrinterService is not running
            conn.readTimeout = 45_000     // BT print can take up to ~15s; leave margin
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Content-Length", pdfFile.length().toString())
            pdfFile.inputStream().use { inp ->
                conn.outputStream.use { out -> inp.copyTo(out) }
            }
            val code = conn.responseCode
            Logger.i(tag, "PrinterService respondió HTTP $code")
            code in 200..299
        } catch (e: Exception) {
            Logger.w(tag, "PrinterService no disponible: ${e.message}")
            false
        }
    }
}
