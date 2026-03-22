package com.visualproject.client.notifications

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

data class NotificationToast(
    val id: Long,
    val text: String,
    val textColor: Int,
    val createdAtMs: Long,
    val lifetimeMs: Long,
)

object NotificationManager {

    private const val toastLifetimeMs = 2600L
    private const val maxToasts = 5

    private val nextId = AtomicLong(1L)
    private val activeToasts = CopyOnWriteArrayList<NotificationToast>()

    fun push(text: String, textColor: Int) {
        val toast = NotificationToast(
            id = nextId.getAndIncrement(),
            text = text,
            textColor = textColor,
            createdAtMs = System.currentTimeMillis(),
            lifetimeMs = toastLifetimeMs,
        )
        activeToasts.add(0, toast)
        while (activeToasts.size > maxToasts) {
            activeToasts.removeLastOrNull()
        }
    }

    fun snapshot(nowMs: Long = System.currentTimeMillis()): List<NotificationToast> {
        activeToasts.removeIf { nowMs - it.createdAtMs >= it.lifetimeMs }
        return activeToasts.toList()
    }

    private fun <E> MutableList<E>.removeLastOrNull(): E? {
        return if (isEmpty()) null else removeAt(lastIndex)
    }
}
