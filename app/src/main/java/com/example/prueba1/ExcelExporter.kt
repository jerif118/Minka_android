package com.minka.app

import android.widget.Toast

import android.content.Context
import androidx.core.content.FileProvider
import com.minka.app.NotificationData
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import android.net.Uri

fun exportToExcel(activity: Context, notificaciones: List<NotificationData>) {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Resumen de Ingresos")

    val headerRow = sheet.createRow(0)
    headerRow.createCell(0).setCellValue("app")
    headerRow.createCell(1).setCellValue("Nombre")
    headerRow.createCell(2).setCellValue("Monto")

    notificaciones.forEachIndexed { index, notification ->
        if (notification.amount > 0) {
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(notification.appName)
            row.createCell(1).setCellValue(notification.senderName)
            row.createCell(2).setCellValue(notification.amount)
        }
    }

    val fileName = "ResumenIngresos.xlsx"
    val file = File(activity.getExternalFilesDir(null), fileName)
    FileOutputStream(file).use { workbook.write(it) }
    Toast.makeText(activity, "Archivo guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    workbook.close()

    val uri: Uri = FileProvider.getUriForFile(
        activity,
        "${activity.packageName}.provider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    activity.startActivity(Intent.createChooser(shareIntent, "Guardar archivo en"))
}
