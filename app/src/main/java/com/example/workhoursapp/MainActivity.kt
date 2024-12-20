package com.example.workhoursapp

import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var textEntrata: TextView
    private lateinit var textUscitaSuggerita: TextView
    private lateinit var textUscita: TextView
    private lateinit var textDiff: TextView
    private lateinit var textTimeElapsed: TextView
    private lateinit var groupReport: Group
    private lateinit var buttonEntrata: Button
    private lateinit var buttonUscita: Button
    private lateinit var buttonReport: Button // Pulsante per il resoconto settimanale
    private lateinit var buttonMonthlyReport: Button // Pulsante per il resoconto mensile
    private var entrataTime: Date? = null
    private var uscitaTime: Date? = null
    private val expectedDailySeconds = ((7 * 60) + 42) * 60 // 7 ore e 42 minuti in secondi
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isTrackingTime = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Associazione delle viste
        textEntrata = findViewById(R.id.text_entrata)
        textUscitaSuggerita = findViewById(R.id.text_uscita_suggerita)
        textUscita = findViewById(R.id.text_uscita)
        textDiff = findViewById(R.id.text_diff)
        textTimeElapsed = findViewById(R.id.text_time_elapsed)
        buttonEntrata = findViewById(R.id.button_entrata)
        buttonUscita = findViewById(R.id.button_uscita)
        buttonReport =
            findViewById(R.id.button_report) // Inizializza il pulsante per il report settimanale
        buttonMonthlyReport =
            findViewById(R.id.button_monthly_report) // Inizializza il pulsante per il report mensile
        groupReport = findViewById(R.id.group_report)

        // Imposta colori dei pulsanti
        buttonEntrata.setBackgroundColor(Color.parseColor("#4CAF50")) // Verde per entrata
        buttonEntrata.setTextColor(Color.WHITE)

        buttonUscita.setBackgroundColor(Color.parseColor("#F44336")) // Rosso per uscita
        buttonUscita.setTextColor(Color.WHITE)

        prefs = getSharedPreferences("work_hours", MODE_PRIVATE)

        // Carica dati salvati
        loadSavedData()

        // Gestione pulsante Entrata
        buttonEntrata.setOnClickListener {
            val currentTime = Date()
            val entrataStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(currentTime)
            val uscitaSuggeritaStr = calculateSuggestedExitTime(currentTime)

            showConfirmationDialog("Vuoi registrare l'entrata alle $entrataStr? L'orario di uscita suggerito è $uscitaSuggeritaStr.") {
                registerEntrance(currentTime, uscitaSuggeritaStr)
            }
        }

        // Gestione pulsante Uscita
        buttonUscita.setOnClickListener {
            if (entrataTime != null) {
                val currentTime = Date()
                val uscitaStr =
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(currentTime)
                val workedTime = calculateWorkedTime(entrataTime!!, currentTime)

                showConfirmationDialog(
                    "Vuoi registrare l'uscita alle $uscitaStr? Tempo effettivo: ${
                        formatTimeDifference(
                            workedTime
                        )
                    }"
                ) {
                    registerExit(currentTime, workedTime)
                }
            } else {
                Toast.makeText(
                    this,
                    "Devi registrare l'entrata prima di uscire",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Gestione pulsante Report
        buttonReport.setOnClickListener {
            showWeeklyReportDialog()
        }

        // Gestione pulsante Report Mensile
        buttonMonthlyReport.setOnClickListener {
            showMonthlyReportDialog()
        }

        // Aggiornamento iniziale report e vista
        updateDailyReport()
    }

    private fun showWeeklyReportDialog() {
        val reportData = generateWeeklyReport()

        // Crea e mostra il dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Resoconto Settimanale")
        builder.setMessage(reportData)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.show()
    }

    private fun generateWeeklyReport(): String {
        val report = StringBuilder()
        val daysOfWeek =
            arrayOf("Domenica", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato")

        var totalDiff = 0L
        for (day in Calendar.MONDAY..Calendar.FRIDAY) {
            val dailyDiff = prefs.getLong("daily_diff_$day", 0)
            totalDiff += dailyDiff
            val dailyExpected = expectedDailySeconds.toLong()
            report.append("${daysOfWeek[day - 1]}:\n")
            report.append("  Orario effettivo: ${formatTimeDifference(dailyDiff)}\n")
            report.append("  Differenza: ${formatTimeDifference(dailyDiff - dailyExpected)}\n")
        }

        val totalExpected = expectedDailySeconds * 5
        report.append("\nTotale settimanale:\n")
        report.append("  Orario effettivo: ${formatTimeDifference(totalDiff)}\n")
        report.append("  Differenza: ${formatTimeDifference(totalDiff - totalExpected)}")

        return report.toString()
    }

    private fun showMonthlyReportDialog() {
        val reportData = generateMonthlyReport()

        // Crea e mostra il dialog
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Resoconto Mensile")
        builder.setMessage(reportData)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.show()
    }

    private fun generateMonthlyReport(): String {
        val report = StringBuilder()
        val calendar = Calendar.getInstance()

        // Ottieni il mese e l'anno corrente
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        // Ottieni il numero di giorni nel mese corrente
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        var totalDiff = 0L

        for (day in 1..daysInMonth) {
            // Imposta il giorno corrente
            calendar.set(currentYear, currentMonth, day)

            // Ottieni il nome del giorno della settimana e il giorno del mese
            val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
            val dayKey =
                "${currentYear}_${currentMonth + 1}_${day}" // Chiave per salvare/recuperare i dati
            val dailyDiff =
                prefs.getLong("daily_diff_$dayKey", 0) // Ottieni i dati dal giorno specifico

            totalDiff += dailyDiff

            // Aggiungi il giorno e il tempo effettivo al report
            report.append("$dayOfWeek $day:\n")
            report.append("  Orario effettivo: ${formatTimeDifference(dailyDiff)}\n")
        }

        report.append("\nTotale mensile:\n")
        report.append("  Orario effettivo: ${formatTimeDifference(totalDiff)}\n")

        return report.toString()
    }

    private fun registerEntrance(currentTime: Date, uscitaSuggeritaStr: String) {
        entrataTime = currentTime
        textEntrata.apply {
            text = "Orario entrata: ${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(currentTime)
            }"
            setTextColor(Color.parseColor("#4CAF50")) // Verde per entrata
        }
        textUscitaSuggerita.text = "Orario Uscita Suggerita: $uscitaSuggeritaStr"

        // Salva l'orario di entrata e l'orario di uscita suggerito
        prefs.edit().apply {
            putLong("entrata_time", currentTime.time)
            putString("uscita_suggerita", uscitaSuggeritaStr)
            putBoolean("is_in_entrata", true) // Stato in entrata
            apply()
        }

        isTrackingTime = true
        startTimer()

        updateDailyReport()
    }

    private fun registerExit(currentTime: Date, workedTime: Long) {
        uscitaTime = currentTime
        textUscita.apply {
            text = "Orario Uscita: ${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(currentTime)
            }"
            setTextColor(Color.parseColor("#F44336")) // Rosso per uscita
        }

        val diffMillis = currentTime.time - (entrataTime?.time ?: 0)
        val diffSeconds = diffMillis / 1000
        val diffFromExpected = diffSeconds - expectedDailySeconds

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val dayKey =
            "${currentYear}_${currentMonth}_${currentDay}" // Nuova chiave per salvare i dati

        prefs.edit().apply {
            putLong("uscita_time", currentTime.time)
            putLong("daily_diff_$dayKey", diffSeconds) // Salva i dati con la nuova chiave
            putBoolean("is_in_entrata", false) // Stato in uscita
            apply()
        }

        isTrackingTime = false
        textTimeElapsed.text = ""

        textDiff.text =
            "Orario effettivo: ${formatTimeDifference(diffSeconds)}\nRispetto al previsto: ${
                formatTimeDifference(diffFromExpected)
            }"

        // Mostra il report settimanale
        showInfoDialog("Uscita registrata! ${generateWeeklyReport()}")
    }

    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (isTrackingTime && entrataTime != null) {
                    val elapsedMillis = Date().time - entrataTime!!.time
                    val elapsedSeconds = elapsedMillis / 1000
                    textTimeElapsed.text =
                        "Tempo trascorso: ${formatTimeDifference(elapsedSeconds)}"
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun calculateSuggestedExitTime(entranceTime: Date): String {
        val calendar = Calendar.getInstance().apply {
            time = entranceTime
            add(Calendar.SECOND, expectedDailySeconds.toInt())
        }
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(calendar.time)
    }

    private fun calculateWorkedTime(entrance: Date, exit: Date): Long {
        return (exit.time - entrance.time) / 1000 // Restituisce il tempo in secondi
    }

    private fun formatTimeDifference(diffSeconds: Long): String {
        val hours = diffSeconds / 3600
        val minutes = (diffSeconds % 3600) / 60
        val seconds = diffSeconds % 60
        return String.format("%d ore, %d minuti, %d secondi", hours, minutes, seconds)
    }

    private fun loadSavedData() {
        val savedEntrataTime = prefs.getLong("entrata_time", 0)
        val isInEntrata = prefs.getBoolean("is_in_entrata", false) // Stato del giorno

        if (savedEntrataTime != 0L) {
            entrataTime = Date(savedEntrataTime)
            textEntrata.text = "Orario entrata: ${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(entrataTime)
            }"

            // Verifica se l'orario di uscita suggerito è memorizzato, altrimenti calcola
            val savedUscitaSuggerita = prefs.getString("uscita_suggerita", null)

            // Imposta il testo completo con la parte "Uscita suggerita: " e l'orario calcolato
            textUscitaSuggerita.text = if (savedUscitaSuggerita != null) {
                "Uscita suggerita: $savedUscitaSuggerita" // Se presente, usa quello memorizzato
            } else {
                "Uscita suggerita: ${calculateSuggestedExitTime(entrataTime!!)}" // Altrimenti calcola
            }

            // Avvia il timer solo se il giorno è ancora in stato "entrata"
            isTrackingTime = isInEntrata
            if (isTrackingTime) startTimer()
        }

        val savedUscitaTime = prefs.getLong("uscita_time", 0)
        if (savedUscitaTime != 0L) {
            uscitaTime = Date(savedUscitaTime)
            textUscita.text = "Orario uscita: ${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(uscitaTime)
            }"
        }

        updateDailyReport()
    }

    private fun updateDailyReport() {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dailyDiff = prefs.getLong("daily_diff_$dayOfWeek", 0)
        val expectedDiff = expectedDailySeconds

        textDiff.text =
            "Orario effettivo: ${formatTimeDifference(dailyDiff)}\nRispetto al previsto: ${
                formatTimeDifference(dailyDiff - expectedDiff)
            }"
    }

    private fun showConfirmationDialog(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Conferma")
            .setMessage(message)
            .setPositiveButton("Sì") { _, _ -> onConfirm() }
            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showInfoDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Informazione")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
