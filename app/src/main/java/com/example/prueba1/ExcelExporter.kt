package com.minka.app

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Crea un archivo Excel con las notificaciones y abre el diálogo del sistema para guardarlo/compartirlo.
 * Se ejecuta en un hilo de fondo para no bloquear la UI.
 *
 * @param context El contexto de la aplicación.
 * @param notifications La lista de datos de notificación a exportar.
 * @param fileName El nombre dinámico para el archivo Excel (ej. "ResumenIngresos_10_04_2025.xlsx").
 */
suspend fun exportToExcel(context: Context, notifications: List<NotificationData>, fileName: String) {
    // Nos aseguramos de que esta operación intensiva se ejecute en segundo plano
    withContext(Dispatchers.IO) {
        if (notifications.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        // 1. Crear el libro de trabajo y la hoja de Excel
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Resumen de Ingresos")

        // 2. Crear la fila de encabezado con más detalles
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("Fecha")
        headerRow.createCell(1).setCellValue("Hora")
        headerRow.createCell(2).setCellValue("Aplicación")
        headerRow.createCell(3).setCellValue("Remitente")
        headerRow.createCell(4).setCellValue("Monto (S/)")
        headerRow.createCell(5).setCellValue("Nota")

        // 3. Llenar los datos de las notificaciones
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        notifications.forEachIndexed { index, notification ->
            // Filtramos por si acaso, aunque la lógica principal ya lo haría
            if (notification.amount > 0) {
                val row = sheet.createRow(index + 1)
                val zonedDateTime = Instant.ofEpochMilli(notification.date).atZone(ZoneId.systemDefault())

                row.createCell(0).setCellValue(zonedDateTime.format(dateFormatter))
                row.createCell(1).setCellValue(zonedDateTime.format(timeFormatter))
                row.createCell(2).setCellValue(notification.appName)
                row.createCell(3).setCellValue(notification.senderName)
                row.createCell(4).setCellValue(notification.amount)
                row.createCell(5).setCellValue(notification.message ?: "") // Maneja notas nulas
            }
        }

        try {
            // 4. Guardar el archivo en una ubicación temporal (cache)
            val exportsDir = File(context.cacheDir, "exports")
            if (!exportsDir.exists()) {
                exportsDir.mkdirs()
            }
            val file = File(exportsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()

            // 5. Usar FileProvider para obtener una URI segura y compartirla
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Permiso importante
            }

            // Volvemos al hilo principal para iniciar la actividad
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Guardar o Compartir Archivo"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al crear el archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}