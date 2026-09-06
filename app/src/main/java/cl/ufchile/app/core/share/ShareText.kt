package cl.ufchile.app.core.share

import android.content.Context
import android.content.Intent

/**
 * Hands plain text to the system share sheet.
 *
 * Text rather than an image so it stays selectable, quotable and searchable
 * wherever it lands. The *asterisk* emphasis the callers use renders as bold in
 * WhatsApp and Telegram and is harmless everywhere else.
 *
 * Nothing is sent from here: the user picks the app and presses send there.
 */
fun shareText(context: Context, subject: String, message: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
