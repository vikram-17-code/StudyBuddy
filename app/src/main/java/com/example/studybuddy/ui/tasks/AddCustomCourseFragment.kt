package com.example.studybuddy.ui.tasks

import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.studybuddy.BuildConfig
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentAddCustomCourseBinding
import com.example.studybuddy.databinding.ItemSubtopicInputBinding
import com.example.studybuddy.model.CoursePlan
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class AddCustomCourseFragment : Fragment() {

    private var _binding: FragmentAddCustomCourseBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userTimeSlots: List<com.example.studybuddy.model.TimeSlot> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddCustomCourseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        addSubtopicRow() // Add one initial subtopic row

        binding.addSubtopicButton.setOnClickListener { addSubtopicRow() }
        binding.addTimeSlotButton.setOnClickListener { showAddTimeSlotDialog() }
        binding.createCourseButton.setOnClickListener { validateAndSaveCourse() }
        binding.generateAiButton.setOnClickListener { generateCourseWithAi() }

        loadTimeSlots()
    }

    private fun generateCourseWithAi() {
        val promptText = binding.aiPromptInput.text.toString().trim()
        if (promptText.isEmpty()) {
            binding.aiPromptInput.error = "Please enter what you want to learn"
            return
        }

        val apiKey = BuildConfig.GROQ_API_KEY.trim()
        if (apiKey.isEmpty() || apiKey.contains("YOUR_GROQ_API_KEY") || apiKey.length < 10) {
            showErrorDialog("Invalid Key", "Groq API Key is missing. Check gradle.properties, add it, and Rebuild Project.")
            return
        }

        binding.aiProgressBar.visibility = View.VISIBLE
        binding.generateAiButton.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val escapedPrompt = promptText.replace("\"", "\\\"").replace("\n", " ")
                val fullPrompt = "Generate a study plan for \\\"$escapedPrompt\\\". Return ONLY valid JSON: {\\\"courseName\\\": \\\"Name\\\", \\\"subtopics\\\": [{\\\"name\\\": \\\"Topic\\\", \\\"days\\\": 2, \\\"materialLink\\\": \\\"https://url-to-docs-or-video\\\"}]}"

                val jsonBody = """
                    {
                        "model": "llama-3.3-70b-versatile",
                        "messages": [
                            {"role": "system", "content": "You are a helpful study planner. Always return raw JSON only, no markdown blocks."},
                            {"role": "user", "content": "$fullPrompt"}
                        ],
                        "response_format": {"type": "json_object"}
                    }
                """.trimIndent()

                OutputStreamWriter(connection.outputStream).use { it.write(jsonBody) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(responseStr)
                    val replyText = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    
                    val jsonString = replyText.trim().removeSurrounding("```json", "```").removeSurrounding("```", "```").trim()

                    withContext(Dispatchers.Main) {
                        parseAndPopulateCourse(jsonString)
                        binding.aiProgressBar.visibility = View.GONE
                        binding.generateAiButton.isEnabled = true
                    }
                } else {
                    val errorStr = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    throw Exception("HTTP $responseCode: $errorStr")
                }
            } catch (e: Exception) {
                Log.e("AI_DEBUG", "Groq API Exception", e)
                withContext(Dispatchers.Main) {
                    showErrorDialog("Connection Failed", "Groq API Error: ${"$"}{e.message}")
                    binding.aiProgressBar.visibility = View.GONE
                    binding.generateAiButton.isEnabled = true
                }
            }
        }
    }

    private fun parseAndPopulateCourse(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            binding.courseNameInput.setText(json.optString("courseName", "New AI Course"))
            binding.subtopicsContainer.removeAllViews()

            val subtopicsArray = json.getJSONArray("subtopics")
            for (i in 0 until subtopicsArray.length()) {
                val topicJson = subtopicsArray.getJSONObject(i)
                addGeneratedSubtopicRow(
                    topicJson.getString("name"), 
                    topicJson.optInt("days", 1),
                    topicJson.optString("materialLink", "")
                )
            }
            Toast.makeText(context, "Plan generated!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AI_PARSE", "JSON Error: $jsonString", e)
            Toast.makeText(context, "AI returned invalid format. Try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun addGeneratedSubtopicRow(name: String, days: Int, link: String = "") {
        val rowBinding = ItemSubtopicInputBinding.inflate(layoutInflater, binding.subtopicsContainer, false)
        rowBinding.subtopicNameInput.setText(name)
        rowBinding.requiredDaysInput.setText(days.toString())
        rowBinding.materialLinkInput.setText(link)
        rowBinding.removeSubtopicButton.setOnClickListener { binding.subtopicsContainer.removeView(rowBinding.root) }
        binding.subtopicsContainer.addView(rowBinding.root)
    }

    private fun addSubtopicRow() {
        val rowBinding = ItemSubtopicInputBinding.inflate(layoutInflater, binding.subtopicsContainer, false)
        rowBinding.removeSubtopicButton.setOnClickListener {
            if (binding.subtopicsContainer.childCount > 1) binding.subtopicsContainer.removeView(rowBinding.root)
        }
        binding.subtopicsContainer.addView(rowBinding.root)
    }

    private fun loadTimeSlots() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("time_slots").whereEqualTo("userId", userId).addSnapshotListener { snapshot, _ ->
            if (_binding == null || !isAdded) return@addSnapshotListener
            val slots = snapshot?.mapNotNull { it.toObject(com.example.studybuddy.model.TimeSlot::class.java) } ?: emptyList()
            userTimeSlots = slots
            val names = userTimeSlots.map { "${it.name} (${it.timeString})" }
            binding.timeSlotSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, if (names.isNotEmpty()) names else listOf("No slots available"))
        }
    }

    private fun validateAndSaveCourse() {
        val userId = auth.currentUser?.uid ?: return
        val courseName = binding.courseNameInput.text.toString().trim()
        if (courseName.isEmpty()) {
            binding.courseNameInput.error = "Course name required"
            return
        }

        val subtopics = mutableListOf<TopicDetail>()
        val planId = UUID.randomUUID().toString()
        for (i in 0 until binding.subtopicsContainer.childCount) {
            val view = binding.subtopicsContainer.getChildAt(i)
            val name = view.findViewById<TextInputEditText>(R.id.subtopicNameInput).text.toString().trim()
            val days = view.findViewById<TextInputEditText>(R.id.requiredDaysInput).text.toString().toIntOrNull() ?: 0
            val materialLink = view.findViewById<TextInputEditText>(R.id.materialLinkInput).text.toString().trim()
            if (name.isEmpty() || days <= 0) return
            subtopics.add(TopicDetail(UUID.randomUUID().toString(), planId, name, days, materialLink, i + 1))
        }

        val selectedSlotPos = binding.timeSlotSpinner.selectedItemPosition
        if (userTimeSlots.isEmpty() || selectedSlotPos < 0) return
        
        binding.createCourseButton.isEnabled = false
        val batch = db.batch()
        batch.set(db.collection("course_plans").document(planId), CoursePlan(planId, courseName, "", userId))
        subtopics.forEach { batch.set(db.collection("topics").document(it.topicId), it) }
        val userCourseId = UUID.randomUUID().toString()
        batch.set(db.collection("user_courses").document(userCourseId), UserCourse(userCourseId, userId, planId, subtopics[0].topicId, 1, userTimeSlots[selectedSlotPos].slotId))
        batch.commit().addOnSuccessListener { 
            Toast.makeText(context, "Course Created!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.homeFragment) 
        }
    }

    private fun showAddTimeSlotDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_time_slot, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.slotNameInput)
        val cbSun = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSun)
        val cbMon = dialogView.findViewById<android.widget.CheckBox>(R.id.cbMon)
        val cbTue = dialogView.findViewById<android.widget.CheckBox>(R.id.cbTue)
        val cbWed = dialogView.findViewById<android.widget.CheckBox>(R.id.cbWed)
        val cbThu = dialogView.findViewById<android.widget.CheckBox>(R.id.cbThu)
        val cbFri = dialogView.findViewById<android.widget.CheckBox>(R.id.cbFri)
        val cbSat = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSat)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Time Slot")
            .setView(dialogView)
            .setPositiveButton("Next") { _, _ ->
                val slotName = nameInput.text.toString().trim()
                val selectedDays = mutableListOf<Int>()
                if (cbSun.isChecked) selectedDays.add(Calendar.SUNDAY)
                if (cbMon.isChecked) selectedDays.add(Calendar.MONDAY)
                if (cbTue.isChecked) selectedDays.add(Calendar.TUESDAY)
                if (cbWed.isChecked) selectedDays.add(Calendar.WEDNESDAY)
                if (cbThu.isChecked) selectedDays.add(Calendar.THURSDAY)
                if (cbFri.isChecked) selectedDays.add(Calendar.FRIDAY)
                if (cbSat.isChecked) selectedDays.add(Calendar.SATURDAY)

                if (slotName.isNotEmpty() && selectedDays.isNotEmpty()) {
                    pickTimeForSlot(slotName, selectedDays)
                } else {
                    Toast.makeText(requireContext(), "Name and at least one day required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTimeForSlot(slotName: String, selectedDays: List<Int>) {
        val calendar = Calendar.getInstance()
        android.app.TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
            saveTimeSlot(slotName, hourOfDay, minute, selectedDays)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun saveTimeSlot(name: String, hour: Int, minute: Int, selectedDays: List<Int>) {
        val userId = auth.currentUser?.uid ?: return
        val slotId = UUID.randomUUID().toString()
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        val timeSlot = com.example.studybuddy.model.TimeSlot(
            slotId = slotId,
            userId = userId,
            name = name,
            timeString = timeString,
            hour = hour,
            minute = minute,
            selectedDays = selectedDays
        )
        db.collection("time_slots").document(slotId).set(timeSlot)
            .addOnSuccessListener {
                if (isAdded) {
                    Toast.makeText(context, "Time Slot Added", Toast.LENGTH_SHORT).show()
                    com.example.studybuddy.notification.NotificationHelper.scheduleTimeSlotAlarm(requireContext(), timeSlot)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}