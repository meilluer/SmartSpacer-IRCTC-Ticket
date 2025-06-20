package com.example.trainticket

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.test2.R
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import java.util.*

object TicketData {
    var trainNumber: String = ""
    var trainName: String = ""
    var boardingFrom: String = ""
    var destination: String = ""
    var departureDate: String = ""
    var departureTime: String = ""
    var arrivalDate: String = ""
    var arrivalTime: String = ""
    var bookingStatus: String = ""
}

class MainActivity : AppCompatActivity() {

    private lateinit var outputTextView: TextView
    private val PICK_PDF_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PDFBoxResourceLoader.init(applicationContext)

        val pickButton = findViewById<Button>(R.id.pickPdfButton)
        outputTextView = findViewById(R.id.outputTextView)

        pickButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
            }
            startActivityForResult(Intent.createChooser(intent, "Select PDF"), PICK_PDF_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_PDF_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val file = savePdfToFile(uri)
                val extracted = extractTicketDetails(file.absolutePath)
                runOnUiThread {
                    outputTextView.text = extracted.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                }
            }
        }
    }

    private fun savePdfToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)!!
        val file = File(cacheDir, getFileName(uri))
        FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        return file
    }

    private fun getFileName(uri: Uri): String {
        var result = "temp.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                result = cursor.getString(nameIndex)
            }
        }
        return result
    }

    private fun extractTicketDetails(pdfPath: String): Map<String, Any> {
        val extractedInfo = mutableMapOf<String, Any>()
        try {
            val doc = PDDocument.load(File(pdfPath))
            val pdfText = PDFTextStripper().getText(doc)
            doc.close()

            val lines = pdfText.lines()

            // --- Train Number and Name ---
            val trainLine = lines.firstOrNull { it.contains(Regex("""\d{5}/""")) }
            trainLine?.let {
                val match = Regex("""(\d{5})/([A-Z0-9\s]+)""").find(it)
                if (match != null) {
                    val num = match.groupValues[1].trim()
                    val name = match.groupValues[2].trim()
                    extractedInfo["Train Number"] = num
                    extractedInfo["Train Name"] = name

                    TicketData.trainNumber = num
                    TicketData.trainName = name
                }
            }

            // --- Boarding and Destination Station ---
            val bookedLineIndex = lines.indexOfFirst { it.contains("Booked from To", ignoreCase = true) }
            if (bookedLineIndex != -1 && bookedLineIndex + 1 < lines.size) {
                val stationLine = lines[bookedLineIndex + 1]
                val codesInLine = Regex("""\(([A-Z]{3,5})\)""").findAll(stationLine).map { it.groupValues[1] }.toList()
                val boarding = codesInLine.getOrNull(1) ?: "N/A"
                val destination = codesInLine.getOrNull(2) ?: "N/A"

                extractedInfo["Boarding From"] = boarding
                extractedInfo["To"] = destination

                TicketData.boardingFrom = boarding
                TicketData.destination = destination
            }

            // --- Departure Time & Date ---
            Regex("""Departure\*\s+(\d{2}:\d{2})\s+(\d{2}-[A-Za-z]{3}-\d{4})""")
                .find(pdfText)?.let {
                    extractedInfo["Departure Time"] = it.groupValues[1]
                    extractedInfo["Departure Date"] = it.groupValues[2]
                    TicketData.departureTime = it.groupValues[1]
                    TicketData.departureDate = it.groupValues[2]
                }

            // --- Arrival Time & Date ---
            Regex("""Arrival\*\s+(\d{2}:\d{2})\s+(\d{2}-[A-Za-z]{3}-\d{4})""")
                .find(pdfText)?.let {
                    extractedInfo["Arrival Time"] = it.groupValues[1]
                    extractedInfo["Arrival Date"] = it.groupValues[2]
                    TicketData.arrivalTime = it.groupValues[1]
                    TicketData.arrivalDate = it.groupValues[2]
                }

            // --- Booking Status (First Match Only) ---
            val bookingRegex = Regex("""(CNF|RAC|WL)/([A-Z0-9]*)/?([0-9]*)/?([A-Z]*)""", RegexOption.IGNORE_CASE)
            bookingRegex.find(pdfText)?.let { match ->
                val status = match.groupValues[1].uppercase(Locale.ROOT)
                val coach = match.groupValues[2].ifEmpty { "-" }
                val berthNo = match.groupValues[3].ifEmpty { "-" }
                val berthType = match.groupValues[4].ifEmpty { "-" }

                val statusStr = "$status / Coach: $coach / Berth: $berthNo / Type: $berthType"
                extractedInfo["Booking Status"] = statusStr
                TicketData.bookingStatus = statusStr
            } ?: run {
                extractedInfo["Booking Status"] = "Not Found"
                TicketData.bookingStatus = "Not Found"
            }

        } catch (e: Exception) {
            extractedInfo["Error"] = e.localizedMessage ?: "Unknown error"
        }

        return extractedInfo
    }
}



