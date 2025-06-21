package com.example.test2

import android.content.Context
import android.widget.Toast
import com.example.trainticket.MainActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.*
import javax.mail.*
import javax.mail.internet.MimeBodyPart
import javax.mail.search.FlagTerm

object EmailFetcher {
    fun fetch(context: Context, email: String, password: String): Boolean {
        return try {
            PDFBoxResourceLoader.init(context)

            val props = Properties().apply { put("mail.store.protocol", "imaps") }
            val session = Session.getInstance(props, null)
            val store = session.getStore()
            store.connect("imap.gmail.com", email, password)

            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_WRITE)

            val messages = inbox.search(FlagTerm(Flags(Flags.Flag.SEEN), false))
            val latestMsg = messages.lastOrNull {
                val from = it.from?.joinToString()?.lowercase(Locale.ROOT) ?: ""
                from.contains("mehulmathur907@gmail.com")
            } ?: return false


            val multipart = latestMsg.content as? Multipart ?: return false

            for (i in 0 until multipart.count) {
                val part = multipart.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true) && part.fileName.endsWith(".pdf")) {
                    val file = File(context.cacheDir, "email_ticket.pdf")
                    (part as MimeBodyPart).saveFile(file)

                    val pdfText = PDFTextStripper().getText(PDDocument.load(file))
                    if (!pdfText.contains("Quota", true) &&
                        !pdfText.contains("Age", true) &&
                        !pdfText.contains("To", true)) {
                        return false
                    }

                    MainActivity().extractTicketDetails(file.absolutePath)
                    latestMsg.setFlag(Flags.Flag.SEEN, true)
                    Toast.makeText(context,"worker scheduled", Toast.LENGTH_SHORT).show()
                    break
                }
            }

            inbox.close(false)
            store.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}