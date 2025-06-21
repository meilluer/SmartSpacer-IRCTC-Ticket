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
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Properties
import javax.mail.*
import javax.mail.internet.MimeBodyPart
import javax.mail.search.FlagTerm
import android.content.Context
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.test2.EmailFetcher

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
var title=""
var subtitl=""
class MainActivity : AppCompatActivity() {
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    lateinit var appContext: Context


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
        emailField = findViewById(R.id.emailInput)
        passwordField = findViewById(R.id.passwordInput)


        emailField = findViewById(R.id.emailInput)
        passwordField = findViewById(R.id.passwordInput)
        val fetchBtn = findViewById<Button>(R.id.fetchEmailButton)

        fetchBtn.setOnClickListener {
            val email = emailField.text.toString()
            val password = passwordField.text.toString()
            val data = workDataOf(
                "email" to email,
                "password" to password
            )
            val fetchWork = PeriodicWorkRequestBuilder<EmailPdfWorker>(24, java.util.concurrent.TimeUnit.HOURS)
                .setInputData(data)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "FetchEmailPdfDaily",
                ExistingPeriodicWorkPolicy.KEEP,
                fetchWork
            )



            CoroutineScope(Dispatchers.IO).launch {
                fetchLatestPdfEmail(email, password)
            }
        }
        Toast.makeText(this, "Daily email check scheduled.", Toast.LENGTH_SHORT).show()
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

    fun extractTicketDetails(pdfPath: String): Map<String, Any> {
        val extractedInfo = mutableMapOf<String, Any>()
        try {
            val doc = PDDocument.load(File(pdfPath))
            val pdfText = PDFTextStripper().getText(doc)
            doc.close()

            val lines = pdfText.lines()

            // --- Train Number and Name ---
            val trainLine = lines.firstOrNull { it.contains(Regex("""\d{5}/""")) }
            trainLine?.let {
                val match = Regex("""(\d{5})/((?:\S+\s+){0,2}\S+)""").find(it)
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
            val bookingRegex = Regex("""(CNF|RAC|WL)/([A-Z0-9]*)/?([0-9]*)/?([A-Z\s\n\r]*)""", RegexOption.IGNORE_CASE)

            bookingRegex.find(pdfText)?.let { match ->
                val status = match.groupValues[1].uppercase(Locale.ROOT)
                val coach = match.groupValues[2].ifEmpty { "-" }
                val berthNo = match.groupValues[3].ifEmpty { "-" }
                val rawType = match.groupValues[4].trim()
                val berthType =  shortenBerthType(rawType)

                val statusStr = "$coach/$berthNo/$berthType"
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
    fun fetchLatestPdfEmail(email: String, password: String) {
        try {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
            }

            val session = Session.getInstance(props, null)
            val store = session.getStore()
            store.connect("imap.gmail.com", email, password)

            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)


            val messages = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            val latestMsg = messages.lastOrNull {
                val from = it.from?.joinToString()?.lowercase(Locale.ROOT) ?: ""
                val subject = it.from?.joinToString()?.lowercase(Locale.ROOT) ?: ""
                from.contains("ticketadmin@irctc.co.in")
            } ?: return

            val multipart = latestMsg.content as? Multipart ?: return

            for (i in 0 until multipart.count) {
                val part = multipart.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) && part.fileName.endsWith(".pdf")) {
                    val file = File(cacheDir, "email_ticket.pdf")
                    (part as MimeBodyPart).saveFile(file)

                    val pdfText = PDFTextStripper().getText(PDDocument.load(file))

// 📌 Skip if it doesn't look like a train ticket
                    if (!pdfText.contains("Quota", ignoreCase = true) &&
                        !pdfText.contains("Age", ignoreCase = true) &&
                        !pdfText.contains("To", ignoreCase = true)) {
                        runOnUiThread {
                            outputTextView.text = "Skipped: PDF is not a valid IRCTC ticket."
                        }
                        return
                    }

                    val extracted = extractTicketDetails(file.absolutePath)

                    runOnUiThread {
                        outputTextView.text = extracted.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    }
                    latestMsg.setFlag(Flags.Flag.SEEN, true)
                    break
                }
            }

            inbox.close(false)
            store.close()

        } catch (e: Exception) {
            runOnUiThread {
                outputTextView.text = "Error: ${e.localizedMessage}"
            }
        }
    }

    private fun shortenBerthType(type: String): String {
        return when (type.replace("\n", " ").trim().uppercase(Locale.ROOT)) {
            "SIDE LOWER" -> "SL"
            "SIDE UPPER" -> "SU"
            "LOWER"      -> "LB"
            "MIDDLE"     -> "MB"
            "UPPER"      -> "UB"
            else         -> type.take(2) // fallback
        }
    }

}


class EmailPdfWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val email = inputData.getString("email") ?: return Result.failure()
        val password = inputData.getString("password") ?: return Result.failure()

        EmailFetcher.fetch(applicationContext, email, password)



        return Result.success()
    }
}

fun checkForUpcomingTrainAndDoWork(context: Context) {
    val depDate = TicketData.departureDate // e.g. "22-Jun-2025"
    val depTime = TicketData.departureTime // e.g. "18:45"

    if (depDate.isBlank() || depTime.isBlank()) return

    try {
        val dateTimeStr = "$depDate $depTime" // e.g. "22-Jun-2025 18:45"
        val formatter = java.text.SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.ENGLISH)
        val depDateTime = formatter.parse(dateTimeStr) ?: return

        val triggerTime = Calendar.getInstance().apply {
            time = depDateTime
            add(Calendar.HOUR_OF_DAY, -4)
        }.time

        val now = Date()

        if (now.after(triggerTime) && now.before(depDateTime)) {
          title= "${TicketData.trainName}/${TicketData.trainNumber}"
            subtitl="${TicketData.bookingStatus} ${TicketData.boardingFrom}(${TicketData.departureTime})->${TicketData.destination}(${TicketData.arrivalTime}) plt "
            Toast.makeText(context, "Train departs soon! Doing scheduled work.", Toast.LENGTH_LONG).show()
            // e.g., start notification, alarm, sync etc.
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

