package com.minka.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
//import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor

/**
 * Crea un archivo Excel con las notificaciones y abre el diálogo del sistema para guardarlo/compartirlo.
 * Se ejecuta en un hilo de fondo para no bloquear la UI.
 *
 * @param context El contexto de la aplicación.
 * @param notifications La lista de datos de notificación a exportar.
 * @param fileName El nombre dinámico para el archivo Excel (ej. "ResumenIngresos_10_04_2025.xlsx").
 */
suspend fun exportToExcel(
    context: Context,
    notifications: List<NotificationData>,
    fileName: String,
    targetUri: Uri? = null,          // ubicación elegida por el usuario (opcional)
    shareAfterSave: Boolean = false  // si true, abre el panel “Compartir” tras guardar
) {
    // Nos aseguramos de que esta operación intensiva se ejecute en segundo plano
    withContext(Dispatchers.IO) {
        // Evita que Apache POI busque clases AWT en Android
        System.setProperty("org.apache.poi.java.awt.headless", "true")

        if (notifications.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        // 1. Crear el libro de trabajo y la hoja de Excel
        val workbook = XSSFWorkbook()   // streaming, usa menos memoria
        val sheet = workbook.createSheet("Resumen de Ingresos")

        // ─── Estilos ───────────────────────────────────────────────
        // Estilo para el título en azul #2035DB
        val titleStyle = (workbook.createCellStyle() as XSSFCellStyle).apply {
            alignment = HorizontalAlignment.CENTER
            val blue = XSSFColor(byteArrayOf(0x20.toByte(), 0x35.toByte(), 0xDB.toByte()), null)
            setFillForegroundColor(blue)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 16
                color = IndexedColors.WHITE.index
            })
        }
        val headerStyle = workbook.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
            fillForegroundColor = IndexedColors.GREY_80_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
        }

        // ─── Fila título (Checkealo.Ya) ────────────────────────────
        val titleRow = sheet.createRow(0)
        titleRow.createCell(0).apply {
            setCellValue("Checkealo.Ya - Reporte de Ingresos")
            cellStyle = titleStyle
        }
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 5))

        // 2. Crear la fila de encabezado con más detalles
        val headerRow = sheet.createRow(1)
        headerRow.createCell(0).setCellValue("Fecha")
        headerRow.createCell(1).setCellValue("Hora")
        headerRow.createCell(2).setCellValue("Aplicación")
        headerRow.createCell(3).setCellValue("Remitente")
        headerRow.createCell(4).setCellValue("Monto (S/)")
        headerRow.createCell(5).setCellValue("Nota")
        (0..5).forEach { col ->
            headerRow.getCell(col).cellStyle = headerStyle
        }

        // 3. Llenar los datos de las notificaciones
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        notifications.forEachIndexed { index, notification ->
            // Filtramos por si acaso, aunque la lógica principal ya lo haría
            if (notification.amount > 0) {
                val row = sheet.createRow(index + 2)   // desplazado por título y encabezado
                val zonedDateTime = Instant.ofEpochMilli(notification.date).atZone(ZoneId.systemDefault())

                row.createCell(0).setCellValue(zonedDateTime.format(dateFormatter))
                row.createCell(1).setCellValue(zonedDateTime.format(timeFormatter))
                row.createCell(2).setCellValue(notification.appName)
                row.createCell(3).setCellValue(notification.senderName)
                row.createCell(4).setCellValue(notification.amount)
                row.createCell(5).setCellValue(notification.message ?: "") // Maneja notas nulas
            }
        }

        sheet.setColumnWidth(0, 15 * 256)
        sheet.setColumnWidth(1, 12 * 256)
        sheet.setColumnWidth(2, 25 * 256)
        sheet.setColumnWidth(3, 25 * 256)
        sheet.setColumnWidth(4, 12 * 256)
        sheet.setColumnWidth(5, 40 * 256)

        // 4. Determinar destino y OutputStream
        val outputStream: OutputStream
        val finalUri: Uri

        if (targetUri != null) {
            // El usuario escogió ubicación mediante SAF
            outputStream = context.contentResolver
                .openOutputStream(targetUri)
                ?: throw IOException("No se pudo abrir el URI proporcionado")
            finalUri = targetUri
        } else {
            // Carpeta cache/exports y FileProvider
            val exportsDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val file = File(exportsDir, fileName)
            outputStream = FileOutputStream(file)
            finalUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        }

        // 5. Escribir el workbook
        workbook.use { wb ->
            outputStream.use { os ->
                wb.write(os)
            }
        }

        // 6. Notificar y/o compartir
        withContext(Dispatchers.Main) {
            if (shareAfterSave) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, finalUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        "Compartir archivo Excel"
                    )
                )
            } else {
                Toast.makeText(
                    context,
                    "Archivo ${if (targetUri == null) "creado y listo para compartir" else "guardado correctamente"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}