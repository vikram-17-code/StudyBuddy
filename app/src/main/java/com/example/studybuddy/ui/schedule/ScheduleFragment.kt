package com.example.studybuddy.ui.schedule

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import android.app.TimePickerDialog
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.applandeo.materialcalendarview.CalendarDay
import com.applandeo.materialcalendarview.listeners.OnCalendarDayClickListener
import com.example.studybuddy.R
import com.example.studybuddy.databinding.FragmentScheduleBinding
import com.example.studybuddy.model.ImportantEvent
import com.example.studybuddy.model.TopicDetail
import com.example.studybuddy.model.UserCourse
import com.example.studybuddy.ui.home.TaskAdapter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ScheduleFragment : Fragment() {
    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var eventAdapter: ImportantEventAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedDate = Calendar.getInstance()
    private var timeSlots: List<com.example.studybuddy.model.TimeSlot> = emptyList()

    private val availableTopics = com.example.studybuddy.data.CourseData.availableTopics

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclers()
        loadAllEventsForCalendar()
        loadTimeSlots()
        
        selectedDate.set(Calendar.HOUR_OF_DAY, 0)
        selectedDate.set(Calendar.MINUTE, 0)
        selectedDate.set(Calendar.SECOND, 0)
        selectedDate.set(Calendar.MILLISECOND, 0)
        
        loadActivitiesForDate(selectedDate)

        binding.calendarView.setOnCalendarDayClickListener(object : OnCalendarDayClickListener {
            override fun onClick(calendarDay: CalendarDay) {
                selectedDate = calendarDay.calendar
                loadActivitiesForDate(selectedDate)
            }
        })

        binding.addEventFab.setOnClickListener {
            showAddEventDialog()
        }

        binding.addTimeSlotFab.setOnClickListener {
            showAddTimeSlotDialog()
        }
        binding.addTimeSlotFab.setOnLongClickListener {
            showManageTimeSlotsDialog()
            true
        }
    }

    private fun setupRecyclers() {
        taskAdapter = TaskAdapter(
            showCompleteButton = false,
            showDayInfo = false,
            onTaskClick = { userCourse ->
                val bundle = Bundle().apply {
                    putString("userCourseId", userCourse.userCourseId)
                }
                findNavController().navigate(R.id.action_scheduleFragment_to_courseDetailFragment, bundle)
            }
        )
        binding.scheduleRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }

        eventAdapter = ImportantEventAdapter { event ->
            showEventOptionsDialog(event)
        }
        binding.eventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }
    }

    private fun loadAllEventsForCalendar() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("important_events")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                val events = snapshot?.toObjects(ImportantEvent::class.java) ?: emptyList()
                val calendarDays = events.map { event ->
                    val cal = Calendar.getInstance().apply { time = event.eventDate.toDate() }
                    val calendarDay = CalendarDay(cal)
                    calendarDay.imageDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_badge_locked)
                    calendarDay.labelColor = R.color.green_primary
                    calendarDay
                }
                binding.calendarView.setCalendarDays(calendarDays)
            }
    }

    private fun loadActivitiesForDate(date: Calendar) {
        val userId = auth.currentUser?.uid ?: return
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        binding.selectedDateTextView.text = if (isToday(date)) "Today's Schedule" else dateFormat.format(date.time)

        val startOfDay = date.time
        val endOfDay = Calendar.getInstance().apply {
            time = date.time
            add(Calendar.DAY_OF_YEAR, 1)
        }.time

        // 1. Load Important Events
        db.collection("important_events")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("eventDate", Timestamp(startOfDay))
            .whereLessThan("eventDate", Timestamp(endOfDay))
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                val events = snapshot?.toObjects(ImportantEvent::class.java) ?: emptyList()
                eventAdapter.submitList(events)
                binding.eventsHeader.visibility = if (events.isNotEmpty()) View.VISIBLE else View.GONE
                updateEmptyState()
            }

        // 2. Load Study Tasks
        db.collection("user_courses")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val taskList = mutableListOf<Pair<UserCourse, TopicDetail>>()
                val currentDayOfWeek = date.get(Calendar.DAY_OF_WEEK)
                
                for (doc in snapshot) {
                    val userCourse = doc.toObject(UserCourse::class.java).copy(userCourseId = doc.id)
                    val timeSlot = timeSlots.find { it.slotId == userCourse.preferredSlot }
                    
                    if (timeSlot != null && timeSlot.selectedDays.isNotEmpty() && !timeSlot.selectedDays.contains(currentDayOfWeek)) {
                        continue
                    }

                    if (userCourse.lastStudyDate != null) {
                        val studyCal = Calendar.getInstance().apply { time = userCourse.lastStudyDate.toDate() }
                        if (studyCal.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
                            studyCal.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)) {
                            continue
                        }
                    }

                    if (userCourse.currentTopicId != "COMPLETED") {
                        val expectedTopic = calculateExpectedTopic(userCourse, date)
                        if (expectedTopic != null) {
                            taskList.add(userCourse to expectedTopic)
                        }
                    }
                }
                taskAdapter.submitList(taskList)
                updateEmptyState()
            }
    }

    private fun updateEmptyState() {
        val hasTasks = taskAdapter.itemCount > 0
        val hasEvents = eventAdapter.itemCount > 0
        binding.noTasksTextView.visibility = if (!hasTasks && !hasEvents) View.VISIBLE else View.GONE
        binding.tasksHeader.visibility = if (hasTasks) View.VISIBLE else View.GONE
    }

    private fun isToday(date: Calendar): Boolean {
        val today = Calendar.getInstance()
        return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }

    private fun showAddEventDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.eventTitleInput)
        val descInput = dialogView.findViewById<EditText>(R.id.eventDescInput)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.eventTypeSpinner)

        val types = arrayOf("Exam", "Assignment", "Presentation", "Meeting")
        typeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        
        AlertDialog.Builder(requireContext())
            .setTitle("Add Event for ${dateFormat.format(selectedDate.time)}")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                val type = typeSpinner.selectedItem.toString()

                if (title.isNotEmpty()) {
                    saveEvent(title, desc, type)
                } else {
                    Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveEvent(title: String, desc: String, type: String) {
        val userId = auth.currentUser?.uid ?: return
        val eventId = UUID.randomUUID().toString()
        val event = ImportantEvent(
            eventId = eventId,
            userId = userId,
            title = title,
            description = desc,
            eventDate = Timestamp(selectedDate.time),
            type = type
        )

        db.collection("important_events").document(eventId).set(event)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Event added!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(requireContext(), "Failed to add event", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEventOptionsDialog(event: ImportantEvent) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle(event.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditEventDialog(event)
                    1 -> confirmDeleteEvent(event)
                }
            }
            .show()
    }

    private fun showEditEventDialog(event: ImportantEvent) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_event, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.eventTitleInput)
        val descInput = dialogView.findViewById<EditText>(R.id.eventDescInput)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.eventTypeSpinner)

        val types = arrayOf("Exam", "Assignment", "Presentation", "Meeting")
        typeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, types)

        titleInput.setText(event.title)
        descInput.setText(event.description)
        val selectionIndex = types.indexOfFirst { it.equals(event.type, ignoreCase = true) }
        if (selectionIndex >= 0) {
            typeSpinner.setSelection(selectionIndex)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Event")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                val type = typeSpinner.selectedItem.toString()

                if (title.isNotEmpty()) {
                    updateEvent(event.eventId, title, desc, type)
                } else {
                    Toast.makeText(requireContext(), "Title is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEvent(eventId: String, title: String, desc: String, type: String) {
        val updates = mapOf(
            "title" to title,
            "description" to desc,
            "type" to type
        )
        db.collection("important_events").document(eventId).update(updates)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Event updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(requireContext(), "Failed to update event", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteEvent(event: ImportantEvent) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete '${event.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("important_events").document(event.eventId).delete()
                    .addOnSuccessListener {
                        if (isAdded) Toast.makeText(requireContext(), "Event deleted!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        if (isAdded) Toast.makeText(requireContext(), "Failed to delete event", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun calculateExpectedTopic(userCourse: UserCourse, date: Calendar): TopicDetail? {
        val courseTopics = availableTopics.filter { it.planId == userCourse.planId }.sortedBy { it.topicOrder }
        if (courseTopics.isEmpty()) return null

        val startCal = Calendar.getInstance().apply {
            time = userCourse.startDate.toDate()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (date.timeInMillis < startCal.timeInMillis) return null 

        val timeSlot = timeSlots.find { it.slotId == userCourse.preferredSlot }
        val validDays = timeSlot?.selectedDays ?: emptyList()

        if (validDays.isEmpty()) {
            val diffInMillis = date.timeInMillis - startCal.timeInMillis
            val daysFromStart = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
            
            var currentDayCounter = 0
            for (topic in courseTopics) {
                val topicEndDay = currentDayCounter + topic.requiredDays
                if (daysFromStart < topicEndDay) return topic
                currentDayCounter = topicEndDay
            }
            return null
        }

        var studyDaysPassed = 0
        val tempCal = startCal.clone() as Calendar
        while (tempCal.timeInMillis < date.timeInMillis) {
            if (validDays.contains(tempCal.get(Calendar.DAY_OF_WEEK))) {
                studyDaysPassed++
            }
            tempCal.add(Calendar.DAY_OF_YEAR, 1)
        }

        var currentDayCounter = 0
        for (topic in courseTopics) {
            val topicEndDay = currentDayCounter + topic.requiredDays
            if (studyDaysPassed < topicEndDay) {
                return topic
            }
            currentDayCounter = topicEndDay
        }
        
        return null
    }

    private fun loadTimeSlots() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("time_slots")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null) return@addSnapshotListener
                val slots = mutableListOf<com.example.studybuddy.model.TimeSlot>()
                snapshot?.forEach { doc ->
                    doc.toObject(com.example.studybuddy.model.TimeSlot::class.java).let { slots.add(it) }
                }
                timeSlots = slots
                loadActivitiesForDate(selectedDate)
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
        
        AlertDialog.Builder(requireContext())
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
        TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
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
                    Toast.makeText(context, "Time Slot Saved", Toast.LENGTH_SHORT).show()
                    com.example.studybuddy.notification.NotificationHelper.scheduleTimeSlotAlarm(requireContext(), timeSlot)
                }
            }
    }

    private fun showManageTimeSlotsDialog() {
        if (timeSlots.isEmpty()) {
            Toast.makeText(requireContext(), "No time slots available", Toast.LENGTH_SHORT).show()
            return
        }
        val options = timeSlots.map { "${it.name} (${it.timeString})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Time Slot to Manage")
            .setItems(options) { _, which ->
                val slot = timeSlots[which]
                showTimeSlotActionDialog(slot)
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showTimeSlotActionDialog(slot: com.example.studybuddy.model.TimeSlot) {
        val actions = arrayOf("Edit", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle(slot.name)
            .setItems(actions) { _, which ->
                if (which == 0) {
                    showEditTimeSlotDialog(slot)
                } else {
                    deleteTimeSlot(slot)
                }
            }
            .show()
    }

    private fun showEditTimeSlotDialog(slot: com.example.studybuddy.model.TimeSlot) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_time_slot, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.slotNameInput)
        val cbSun = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSun)
        val cbMon = dialogView.findViewById<android.widget.CheckBox>(R.id.cbMon)
        val cbTue = dialogView.findViewById<android.widget.CheckBox>(R.id.cbTue)
        val cbWed = dialogView.findViewById<android.widget.CheckBox>(R.id.cbWed)
        val cbThu = dialogView.findViewById<android.widget.CheckBox>(R.id.cbThu)
        val cbFri = dialogView.findViewById<android.widget.CheckBox>(R.id.cbFri)
        val cbSat = dialogView.findViewById<android.widget.CheckBox>(R.id.cbSat)

        nameInput.setText(slot.name)
        if (slot.selectedDays.contains(Calendar.SUNDAY)) cbSun.isChecked = true
        if (slot.selectedDays.contains(Calendar.MONDAY)) cbMon.isChecked = true
        if (slot.selectedDays.contains(Calendar.TUESDAY)) cbTue.isChecked = true
        if (slot.selectedDays.contains(Calendar.WEDNESDAY)) cbWed.isChecked = true
        if (slot.selectedDays.contains(Calendar.THURSDAY)) cbThu.isChecked = true
        if (slot.selectedDays.contains(Calendar.FRIDAY)) cbFri.isChecked = true
        if (slot.selectedDays.contains(Calendar.SATURDAY)) cbSat.isChecked = true

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Time Slot")
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
                    val calendar = Calendar.getInstance()
                    TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                        updateTimeSlot(slot.slotId, slotName, hourOfDay, minute, selectedDays)
                    }, slot.hour, slot.minute, true).show()
                } else {
                    Toast.makeText(requireContext(), "Name and at least one day required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTimeSlot(slotId: String, name: String, hour: Int, minute: Int, selectedDays: List<Int>) {
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        val updates = mapOf(
            "name" to name,
            "hour" to hour,
            "minute" to minute,
            "timeString" to timeString,
            "selectedDays" to selectedDays
        )
        db.collection("time_slots").document(slotId).update(updates)
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(requireContext(), "Time Slot Updated", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTimeSlot(slot: com.example.studybuddy.model.TimeSlot) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Time Slot")
            .setMessage("Are you sure you want to delete '${slot.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("time_slots").document(slot.slotId).delete()
                    .addOnSuccessListener {
                        if (isAdded) Toast.makeText(requireContext(), "Time Slot Deleted", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}