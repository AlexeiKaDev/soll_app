package com.soll.presentation.screens.chat

import org.junit.Assert.*
import org.junit.Test

class ChatTaskIntentTest {
    @Test fun `explicit task requests preserve the entire multiline body`() {
        assertEquals("Позвонить клиенту\nОбсудить заказ", explicitTaskBody("добавь задачу Позвонить клиенту\nОбсудить заказ"))
        assertEquals("Купить кабель", explicitTaskBody("Создай новую задачу: Купить кабель"))
        assertEquals("Купить кабель", explicitTaskBody("Новая задача: Купить кабель"))
        assertEquals("Купить кабель", explicitTaskBody("Задача: Купить кабель"))
    }
    @Test fun `empty task command can ask for text without creating a task`() {
        assertEquals("", explicitTaskBody("добавь задачу"))
    }
    @Test fun `questions and ordinary chat keep the existing read only policy`() {
        assertNull(explicitTaskBody("Как добавить задачу?"))
        assertNull(explicitTaskBody("Покажи мои задачи"))
        assertNull(explicitTaskBody("Привет"))
    }
}
