package com.github.kirer.appk.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.kirer.appk.R
import com.github.kirer.appk.test.BaseTest

/**
 * 测试结果适配器
 * 显示测试状态和结果
 */
class TestResultAdapter : RecyclerView.Adapter<TestResultAdapter.TestResultViewHolder>() {
    
    private val testResults = mutableListOf<TestResultItem>()
    
    data class TestResultItem(
        val testName: String,
        val state: BaseTest.TestState,
        val isExpanded: Boolean = false
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_test_result, parent, false)
        return TestResultViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: TestResultViewHolder, position: Int) {
        holder.bind(testResults[position])
    }
    
    override fun getItemCount(): Int = testResults.size
    
    fun updateTestResult(testName: String, state: BaseTest.TestState) {
        val index = testResults.indexOfFirst { it.testName == testName }
        if (index != -1) {
            testResults[index] = testResults[index].copy(state = state)
            notifyItemChanged(index)
        } else {
            testResults.add(TestResultItem(testName, state))
            notifyItemInserted(testResults.size - 1)
        }
    }
    
    fun clearResults() {
        testResults.clear()
        notifyDataSetChanged()
    }
    
    inner class TestResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTestName: TextView = itemView.findViewById(R.id.tvTestName)
        private val tvTestStatus: TextView = itemView.findViewById(R.id.tvTestStatus)
        private val tvTestMessage: TextView = itemView.findViewById(R.id.tvTestMessage)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val tvTestDetails: TextView = itemView.findViewById(R.id.tvTestDetails)
        
        fun bind(item: TestResultItem) {
            tvTestName.text = item.testName
            tvTestMessage.text = item.state.message
            
            // 设置状态
            when (item.state.result) {
                BaseTest.TestResult.NOT_STARTED -> {
                    tvTestStatus.text = "未开始"
                    tvTestStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_orange))
                    progressBar.visibility = View.GONE
                }
                BaseTest.TestResult.RUNNING -> {
                    tvTestStatus.text = "运行中"
                    tvTestStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_orange))
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = item.state.progress
                }
                BaseTest.TestResult.PASSED -> {
                    tvTestStatus.text = "通过"
                    tvTestStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.success_green))
                    progressBar.visibility = View.GONE
                }
                BaseTest.TestResult.FAILED -> {
                    tvTestStatus.text = "失败"
                    tvTestStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.error_red))
                    progressBar.visibility = View.GONE
                }
                BaseTest.TestResult.SKIPPED -> {
                    tvTestStatus.text = "跳过"
                    tvTestStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.warning_orange))
                    progressBar.visibility = View.GONE
                }
            }
            
            // 显示详细信息
            if (item.state.details.isNotEmpty()) {
                tvTestDetails.visibility = View.VISIBLE
                tvTestDetails.text = item.state.details.joinToString("\n")
            } else {
                tvTestDetails.visibility = View.GONE
            }
            
            // 点击展开/收起详细信息
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val newItem = item.copy(isExpanded = !item.isExpanded)
                    testResults[adapterPosition] = newItem

                    if (newItem.isExpanded) {
                        tvTestDetails.visibility = View.VISIBLE
                    } else {
                        tvTestDetails.visibility = View.GONE
                    }
                }
            }
        }
    }
}
