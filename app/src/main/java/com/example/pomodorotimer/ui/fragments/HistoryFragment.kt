package com.example.pomodorotimer.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pomodorotimer.databinding.FragmentHistoryBinding
import com.example.pomodorotimer.ui.adapters.PomodoroSessionAdapter
import com.example.pomodorotimer.ui.viewmodels.HistoryViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HistoryViewModel
    private lateinit var sessionAdapter: PomodoroSessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        // Set up RecyclerView
        sessionAdapter = PomodoroSessionAdapter()
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = sessionAdapter
        }

        // Set up back button
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Set up bar chart
        setupBarChart()
    }

    private fun setupObservers() {
        viewModel.allSessions.observe(viewLifecycleOwner) { sessions ->
            if (sessions.isNotEmpty()) {
                binding.tvNoHistory.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.barChart.visibility = View.VISIBLE

                sessionAdapter.submitList(sessions)
                updateBarChart(sessions)
            } else {
                binding.tvNoHistory.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
                binding.barChart.visibility = View.GONE
            }
        }
    }

    private fun setupBarChart() {
        binding.barChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setPinchZoom(false)
            setDrawBarShadow(false)
            setDrawGridBackground(false)

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)

            axisLeft.setDrawGridLines(false)
            axisRight.isEnabled = false

            animateY(1500)
        }
    }

    private fun updateBarChart(sessions: List<com.example.pomodorotimer.data.entity.PomodoroSession>) {
        // Group sessions by day for the last 7 days
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dayLabels = ArrayList<String>()
        val entries = ArrayList<BarEntry>()

        // Create a map to hold completed sessions count for each day
        val daySessionsMap = mutableMapOf<Int, Int>()

        // Initialize the map with 0 for all 7 days
        for (i in 6 downTo 0) {
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            dayLabels.add(dateFormat.format(calendar.time))
            daySessionsMap[6 - i] = 0
            calendar.add(Calendar.DAY_OF_YEAR, i) // Reset calendar
        }

        // Count completed sessions for each day
        for (session in sessions) {
            if (session.completed) {
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.time = session.date

                val currentCalendar = Calendar.getInstance()

                for (i in 6 downTo 0) {
                    currentCalendar.add(Calendar.DAY_OF_YEAR, -i)

                    if (isSameDay(sessionCalendar.time, currentCalendar.time)) {
                        daySessionsMap[6 - i] = (daySessionsMap[6 - i] ?: 0) + 1
                    }

                    currentCalendar.add(Calendar.DAY_OF_YEAR, i) // Reset calendar
                }
            }
        }

        // Create bar entries
        daySessionsMap.forEach { (day, count) ->
            entries.add(BarEntry(day.toFloat(), count.toFloat()))
        }

        val dataSet = BarDataSet(entries, "Completed Sessions")
        dataSet.color = Color.parseColor("#CDDC39") // Lime color

        val barData = BarData(dataSet)
        barData.barWidth = 0.7f

        binding.barChart.apply {
            data = barData
            xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
            invalidate()
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

