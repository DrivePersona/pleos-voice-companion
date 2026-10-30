package com.example.voicecompanion.ui.overlay


import kotlin.math.roundToInt

/**
 * 오버레이를 손가락으로 끌어 옮기는 계산이다. 안드로이드를 모르고 터치 좌표만 받는다.
 * 위치는 창 왼쪽 위 모서리의 화면 좌표(px)이고, 창이 주어진 영역 밖으로 나가는 값은 돌려주지 않는다.
 */
internal class OverlayDrag(private val touchSlop: Int) {
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var minX = 0
    private var maxX = 0
    private var minY = 0
    private var maxY = 0

    /** 손가락이 탭 범위를 한 번이라도 벗어났는지. 손가락을 뗄 때까지 유지된다. */
    var dragging = false
        private set

    /**
     * 새 터치를 시작한다. 창 왼쪽 위 모서리는 지금 ([x], [y])에 있고 [minX]..[maxX] × [minY]..[maxY] 안에서만 움직인다.
     * 영역이 창보다 작아 max가 min보다 작으면 min에 붙인다.
     */
    fun down(rawX: Float, rawY: Float, x: Int, y: Int, minX: Int, maxX: Int, minY: Int, maxY: Int) {
        downX = rawX
        downY = rawY
        startX = x
        startY = y
        this.minX = minX
        this.maxX = maxOf(minX, maxX)
        this.minY = minY
        this.maxY = maxOf(minY, maxY)
        dragging = false
    }

    /** 끄는 중이면 새 (x, y)를, 아직 탭 범위 안이면 null을 돌려준다. */
    fun move(rawX: Float, rawY: Float): Pair<Int, Int>? {
        val dx = rawX - downX
        val dy = rawY - downY
        val slop = touchSlop.toFloat()
        if (!dragging && dx * dx + dy * dy <= slop * slop) return null
        dragging = true
        return (startX + dx.roundToInt()).coerceIn(minX, maxX) to (startY + dy.roundToInt()).coerceIn(minY, maxY)
    }
}
