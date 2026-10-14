package com.archieapps.oklo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardContentTest {
    @Test
    fun `validates every card type like the server table`() {
        assertNull(CardContent(CardType.Basic, "Frente", "Verso").validationError())
        assertEquals("Preencha o verso do card.", CardContent(CardType.Basic, "Frente", " ").validationError())
        assertEquals("Preencha a resposta.", CardContent(CardType.Qa, "Pergunta?").validationError())
        assertEquals("Escolha se a afirmação é verdadeira ou falsa.", CardContent(CardType.TrueFalse, "Afirmação").validationError())
        assertNull(CardContent(CardType.TrueFalse, "Afirmação", answer = false).validationError())
        val two = listOf(Choice("A", true), Choice("B", false))
        assertNull(CardContent(CardType.MultipleChoice, "Q", choices = two).validationError())
        assertEquals("Marque exatamente uma alternativa correta.", CardContent(CardType.MultipleChoice, "Q", choices = two.map { it.copy(correct = true) }).validationError())
        assertEquals("Informe de duas a quatro alternativas.", CardContent(CardType.MultipleChoice, "Q", choices = listOf(Choice("A", true), Choice(" ", false))).validationError())
        assertEquals("Use no máximo quatro alternativas.", CardContent(CardType.MultipleChoice, "Q", choices = two + two + two).validationError())
        assertEquals("Preencha o enunciado.", CardContent(CardType.MultipleChoice, "  ", choices = two).validationError())
        assertEquals("O texto principal passa de 5000 caracteres.", CardContent(CardType.Basic, "x".repeat(5001), "v").validationError())
    }

    @Test
    fun `normalizing drops fields that do not belong to the type`() {
        val content = CardContent(CardType.TrueFalse, " Afirmação ", back = "lixo", answer = true, choices = listOf(Choice("a", true)), explanation = "  ").normalized()
        assertEquals("Afirmação", content.front)
        assertNull(content.back)
        assertNull(content.choices)
        assertNull(content.explanation)
        val basic = CardContent(CardType.Basic, "F", "V", answer = true).normalized()
        assertNull(basic.answer)
    }
}
