package com.myra.assistant.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_MYRA = 2
    }

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(msg: ChatMessage) {
        if (!msg.isUser && messages.isNotEmpty() && !messages.last().isUser) {
            // Deduplicate or update last message if identical
            val last = messages.last()
            if (last.text == msg.text) return
        }
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun lastMyraText(): String {
        return messages.lastOrNull { !it.isUser }?.text ?: ""
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_MYRA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val view = inflater.inflate(R.layout.item_chat_user, parent, false)
            UserViewHolder(view as ViewGroup)
        } else {
            val view = inflater.inflate(R.layout.item_chat_myra, parent, false)
            MyraViewHolder(view as ViewGroup)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is UserViewHolder) {
            holder.bind(msg)
        } else if (holder is MyraViewHolder) {
            holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = messages.size

    class UserViewHolder(val root: ViewGroup) : RecyclerView.ViewHolder(root) {
        private val userText: TextView = root.findViewById(R.id.chatUserText)
        fun bind(msg: ChatMessage) {
            userText.text = msg.text
        }
    }

    class MyraViewHolder(val root: ViewGroup) : RecyclerView.ViewHolder(root) {
        private val myraText: TextView = root.findViewById(R.id.chatMyraText)
        fun bind(msg: ChatMessage) {
            myraText.text = msg.text
        }
    }
}
