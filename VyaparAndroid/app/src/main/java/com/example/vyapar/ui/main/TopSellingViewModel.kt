package com.example.vyapar.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vyapar.data.DataRepository
import com.example.vyapar.data.TopSellingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class TopSellingViewModel(private val repository: DataRepository) : ViewModel() {
    private val _topSellingItems = MutableStateFlow<List<TopSellingItem>>(emptyList())
    val topSellingItems: StateFlow<List<TopSellingItem>> = _topSellingItems.asStateFlow()

    private val _selectedFilter = MutableStateFlow("Today")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customDateRange: StateFlow<Pair<Long, Long>?> = _customDateRange.asStateFlow()

    init {
        applyFilter("Today")
    }

    fun applyFilter(filter: String) {
        _selectedFilter.value = filter
        if (filter != "Custom") {
            val (start, end) = calculateRange(filter)
            fetchData(start, end)
        } else {
            _customDateRange.value?.let { (start, end) ->
                fetchData(start, end)
            } ?: run {
                _topSellingItems.value = emptyList()
            }
        }
    }

    fun applyCustomRange(startDate: Long, endDate: Long) {
        _customDateRange.value = Pair(startDate, endDate)
        _selectedFilter.value = "Custom"
        
        val startCal = Calendar.getInstance().apply {
            timeInMillis = startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = endDate
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        fetchData(startCal.timeInMillis, endCal.timeInMillis)
    }

    private fun fetchData(startDate: Long, endDate: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val results = repository.getTopSellingItems(startDate, endDate)
            _topSellingItems.value = results
        }
    }

    private fun calculateRange(filter: String): Pair<Long, Long> {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()

        // End of today is 23:59:59.999
        end.set(Calendar.HOUR_OF_DAY, 23)
        end.set(Calendar.MINUTE, 59)
        end.set(Calendar.SECOND, 59)
        end.set(Calendar.MILLISECOND, 999)

        when (filter) {
            "Today" -> {
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            "Yesterday" -> {
                start.add(Calendar.DAY_OF_YEAR, -1)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)

                end.add(Calendar.DAY_OF_YEAR, -1)
            }
            "This Week" -> {
                start.add(Calendar.DAY_OF_YEAR, -6)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
            "This Month" -> {
                start.add(Calendar.DAY_OF_YEAR, -29)
                start.set(Calendar.HOUR_OF_DAY, 0)
                start.set(Calendar.MINUTE, 0)
                start.set(Calendar.SECOND, 0)
                start.set(Calendar.MILLISECOND, 0)
            }
        }
        return Pair(start.timeInMillis, end.timeInMillis)
    }
}
