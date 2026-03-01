package com.examshield.ai.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.examshield.ai.domain.model.ClassificationResult
import com.examshield.ai.domain.model.RiskLevel
import com.examshield.ai.domain.repository.OrbitalData
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MissionReportGenerator(private val context: Context) {

    fun generateReport(
        threats: List<ClassificationResult>,
        orbitalData: OrbitalData
    ): File? {
        val criticalThreats = threats.filter { it.riskLevel == RiskLevel.LEVEL_4_CONFIRMED_THREAT || it.isNexusVerified }
        if (criticalThreats.isEmpty()) return null

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 24f
            color = Color.rgb(200, 0, 0) // Cyber Red
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 14f
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 12f
            color = Color.DKGRAY
        }

        // Draw Title
        var yPos = 50f
        canvas.drawText("EXAMSHIELD - AFTER-ACTION INTELLIGENCE REPORT", 50f, yPos, titlePaint)
        yPos += 40f

        // Draw Orbital Stamp
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("MISSION UPLINK TIME: $timeStamp", 50f, yPos, headerPaint)
        yPos += 20f
        val gpsStamp = if (orbitalData.isSecure) {
            "LAT: ${orbitalData.latitude} | LNG: ${orbitalData.longitude} | SAT LOCK: ${orbitalData.satelliteCount}"
        } else {
            "GLOBAL POSITIONING: OFFLINE (NO SATELLITE LOCK)"
        }
        canvas.drawText("ORBITAL STAMP: $gpsStamp", 50f, yPos, textPaint)
        yPos += 40f

        // Draw Divider
        paint.color = Color.BLACK
        paint.strokeWidth = 2f
        canvas.drawLine(50f, yPos, 545f, yPos, paint)
        yPos += 30f

        canvas.drawText("CONFIRMED HIGH-RISK THREATS (LEVEL 4 / NEXUS VERIFIED):", 50f, yPos, headerPaint)
        yPos += 30f

        // Draw Threats
        criticalThreats.forEach { threat ->
            if (yPos > 800f) {
                // Ignore pagination for now, simple 1-pager
                return@forEach
            }
            
            val deviceType = threat.deviceType.name
            val mac = threat.rawObject.macAddress
            val rssi = threat.rawObject.signalStrengthRssi
            val name = threat.rawObject.name ?: "UNKNOWN_SIGNATURE"
            
            canvas.drawText("TARGET MAC : $mac", 50f, yPos, headerPaint)
            yPos += 15f
            canvas.drawText("- CLASSIFICATION : $deviceType", 50f, yPos, textPaint)
            yPos += 15f
            canvas.drawText("- BROADCAST ID   : $name", 50f, yPos, textPaint)
            yPos += 15f
            canvas.drawText("- SIGNAL INTENSITY: $rssi dBm (Z-ZONE: ${threat.distanceZone.name})", 50f, yPos, textPaint)
            yPos += 15f
            canvas.drawText("- CONFIDENCE     : ${threat.confidenceScore}%", 50f, yPos, textPaint)
            yPos += 15f
            canvas.drawText("- DISCOVERY LOG  : ${threat.discoveryReason}", 50f, yPos, textPaint)
            yPos += 30f
        }

        document.finishPage(page)

        // Save PDF
        val fileName = "ExamShield_Intel_Report_${System.currentTimeMillis()}.pdf"
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (directory != null && !directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, fileName)

        try {
            document.writeTo(FileOutputStream(file))
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            return null
        }
        
        document.close()
        return file
    }
}
