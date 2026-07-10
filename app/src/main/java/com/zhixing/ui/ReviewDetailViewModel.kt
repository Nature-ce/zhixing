package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.SubprojectEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 回顾详情 ViewModel。
 *
 * 加载任务标题 + 子项目 + 回顾文本，变更即时持久化到数据库。
 */
class ReviewDetailViewModel(
    private val taskId: Long,
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
) : ViewModel() {

    private val _taskTitle = MutableStateFlow("")
    val taskTitle: StateFlow<String> = _taskTitle.asStateFlow()

    private val _subprojects = MutableStateFlow<List<SubprojectEntity>>(emptyList())
    val subprojects: StateFlow<List<SubprojectEntity>> = _subprojects.asStateFlow()

    private val _reviewText = MutableStateFlow("")
    val reviewText: StateFlow<String> = _reviewText.asStateFlow()

    init {
        viewModelScope.launch {
            val task = taskDao.getTaskById(taskId)
            _taskTitle.value = task?.title ?: ""
            _reviewText.value = task?.reviewText ?: ""
        }
        viewModelScope.launch {
            subprojectDao.getSubprojectsByTaskId(taskId).collect { list ->
                _subprojects.value = list
            }
        }
    }

    fun onReviewChange(text: String) {
        _reviewText.value = text
        viewModelScope.launch {
            taskDao.updateTaskReview(taskId, text)
        }
    }
}
