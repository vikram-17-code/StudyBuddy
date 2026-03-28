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

    private val availableTopics = listOf(
        TopicDetail("daa_t1", "daa_plan", "Asymptotic Analysis", 2, "https://www.geeksforgeeks.org/analysis-of-algorithms-set-1-asymptotic-analysis/", 1),
        TopicDetail("daa_t2", "daa_plan", "Divide and Conquer", 3, "https://www.tutorialspoint.com/data_structures_algorithms/divide_and_conquer.htm", 2),
        TopicDetail("java_t1", "java_plan", "OOP Concepts", 1, "https://docs.oracle.com/javase/tutorial/java/concepts/", 1),
        TopicDetail("java_t2", "java_plan", "Collections", 2, "https://www.javatpoint.com/collections-in-java", 2),
        TopicDetail("dsa_t1", "dsa_plan", "Arrays & Linked Lists", 3, "https://www.programiz.com/dsa/linked-list", 1),
        TopicDetail("dsa_t2", "dsa_plan", "Stacks & Queues", 2, "https://www.geeksforgeeks.org/stack-data-structure/", 2),
        TopicDetail("web_t1", "web_plan", "HTML5 & CSS3", 2, "https://developer.mozilla.org/en-US/docs/Learn/Getting_started_with_the_web/HTML_basics", 1),
        TopicDetail("web_t2", "web_plan", "JavaScript Basics", 3, "https://javascript.info/", 2),
        TopicDetail("se_t1", "se_plan", "SDLC Models", 2, "https://www.tutorialspoint.com/software_engineering/software_engineering_sdlc_models.htm", 1),
        TopicDetail("se_t2", "se_plan", "Agile Methodology", 2, "https://www.atlassian.com/agile", 2),
        TopicDetail("eng_t1", "eng_plan", "Grammar & Tenses", 2, "https://www.grammarly.com/blog/verb-tenses/", 1),
        TopicDetail("eng_t2", "eng_plan", "Business Communication", 3, "https://www.coursera.org/articles/business-communication", 2)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclers()
        loadAllEventsForCalendar()
        
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

        eventAdapter = ImportantEventAdapter()
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
                for (doc in snapshot) {
                    val userCourse = doc.toObject(UserCourse::class.java).copy(userCourseId = doc.id)
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

        val diffInMillis = date.timeInMillis - startCal.timeInMillis
        if (diffInMillis < 0) return null 

        val daysFromStart = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        
        var currentDayCounter = 0
        for (topic in courseTopics) {
            val topicEndDay = currentDayCounter + topic.requiredDays
            if (daysFromStart < topicEndDay) {
                return topic
            }
            currentDayCounter = topicEndDay
        }
        
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}