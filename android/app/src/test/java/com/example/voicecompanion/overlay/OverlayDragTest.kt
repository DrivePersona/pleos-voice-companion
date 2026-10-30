package com.example.voicecompanion.ui.overlay


import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayDragTest {
    // 창(280px) 왼쪽 위 모서리가 놓일 수 있는 범위: x 0..2280, y 90..1020 (2560x1300 화면에서 위 90px 바를 뺀 영역).
    // 손가락은 (500, 500)에서 누르고, 창은 (x, y)에서 시작한다.
    private fun drag(x: Int = 1140, y: Int = 555) =
        OverlayDrag(touchSlop = 10).apply { down(500f, 500f, x, y, 0, 2280, 90, 1020) }

    @Test
    fun `탭 범위 안에서는 끌기로 보지 않는다`() {
        val d = drag()
        assertNull(d.move(505f, 504f))
        assertFalse(d.dragging)
    }

    @Test
    fun `탭 범위 경계까지는 탭이고 한 픽셀 넘으면 끌기다`() {
        val d = drag()
        assertNull(d.move(510f, 500f)) // 정확히 touchSlop(10)
        assertEquals(1151 to 555, d.move(511f, 500f))
    }

    @Test
    fun `세로로만 움직여도 탭 범위를 넘으면 끌기다`() {
        val d = drag()
        assertEquals(1140 to 585, d.move(500f, 530f))
        assertTrue(d.dragging)
    }

    @Test
    fun `탭 범위를 넘으면 손가락이 움직인 만큼 옮긴다`() {
        val d = drag(100, 200)
        assertEquals(130 to 170, d.move(530f, 470f))
        assertTrue(d.dragging)
    }

    @Test
    fun `한 번 끌기가 시작되면 제자리로 돌아와도 끌기다`() {
        val d = drag(100, 200)
        d.move(530f, 500f)
        assertEquals(100 to 200, d.move(500f, 500f))
        assertTrue(d.dragging)
    }

    @Test
    fun `창이 영역 밖으로 나가는 위치는 만들지 않는다`() {
        val d = drag(2000, 900)
        assertEquals(2280 to 1020, d.move(5500f, 5500f))
        assertEquals(0 to 90, d.move(-5000f, -5000f))
    }

    @Test
    fun `영역이 창보다 작으면 왼쪽 위 모서리에 붙인다`() {
        val d = OverlayDrag(touchSlop = 10).apply { down(0f, 0f, 5, 5, 0, -50, 90, 20) }
        assertEquals(0 to 90, d.move(100f, 100f))
    }

    @Test
    fun `새 터치는 이전 끌기 상태를 지운다`() {
        val d = drag()
        d.move(550f, 500f)
        d.down(0f, 0f, 0, 90, 0, 2280, 90, 1020)
        assertFalse(d.dragging)
        assertNull(d.move(3f, 0f))
    }
}
