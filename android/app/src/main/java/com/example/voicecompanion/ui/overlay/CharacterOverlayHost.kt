package com.example.voicecompanion.ui.overlay

import com.example.voicecompanion.ui.MainActivity
import com.example.voicecompanion.characterpack.model.CharacterPack
import com.example.voicecompanion.characterpack.ui.CharacterSurfaceView
import com.example.voicecompanion.characterpack.ui.SpriteCharacterView


import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout

/** WindowManager 위에 정차 중 캐릭터만 올리는 Android 경계다. 캐릭터는 끌어서 옮길 수 있고, 놓은 위치는 prefs에 남는다. */
internal class CharacterOverlayHost(private val context: Context, private val prefs: SharedPreferences) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val sizePx = (280 * context.resources.displayMetrics.density).toInt()
    private val params = layoutParams()
    private var root: FrameLayout? = null
    private var characterView: View? = null
    private var attached = false

    val permissionGranted: Boolean
        get() = Settings.canDrawOverlays(context)

    fun show(pack: CharacterPack, animation: String): OverlayShowResult {
        if (!permissionGranted) return OverlayShowResult.PERMISSION_REQUIRED
        // GLB SurfaceView와 2D View는 창 수명 모델이 다르다. 다른 표현으로 바뀌면 창을 새로 만들어
        // 이전 Filament Surface와 스프라이트 재생 콜백을 함께 정리한다.
        if (root != null && !supports(pack)) hide()

        val overlay = root ?: FrameLayout(context).also { frame ->
            val view = createCharacterView(pack) ?: return OverlayShowResult.REJECTED
            view.contentDescription = "VR 캐릭터 설정 열기"
            view.setOnTouchListener(dragListener())
            frame.addView(view, FrameLayout.LayoutParams(sizePx, sizePx))
            characterView = view
            root = frame
        }
        if (!attached) {
            // 저장한 위치가 없으면 영역 가운데. 화면 크기가 달라졌어도 창이 영역 밖에서 시작하지 않게 안쪽으로 맞춘다.
            val area = usableArea()
            params.x = prefs.getInt(PREF_X, (area.left + area.right - sizePx) / 2)
                .coerceIn(area.left, maxOf(area.left, area.right - sizePx))
            params.y = prefs.getInt(PREF_Y, (area.top + area.bottom - sizePx) / 2)
                .coerceIn(area.top, maxOf(area.top, area.bottom - sizePx))
            try {
                windowManager.addView(overlay, params)
                attached = true
            } catch (_: SecurityException) {
                return OverlayShowResult.PERMISSION_REQUIRED
            } catch (_: WindowManager.BadTokenException) {
                return OverlayShowResult.REJECTED
            }
        }
        present(pack, animation)
        return OverlayShowResult.SHOWING
    }

    private fun createCharacterView(pack: CharacterPack): View? = when {
        pack.model != null -> CharacterSurfaceView(context, transparentBackground = true).also { it.setOnCharacterClick(::openSettings) }
        pack.sprite != null -> SpriteCharacterView(context).also { it.setOnCharacterClick(::openSettings) }
        else -> null
    }

    private fun supports(pack: CharacterPack): Boolean = when (characterView) {
        is CharacterSurfaceView -> pack.model != null
        is SpriteCharacterView -> pack.sprite != null
        else -> false
    }

    private fun present(pack: CharacterPack, animation: String) {
        when (val view = characterView) {
            is CharacterSurfaceView -> view.present(pack, animation)
            is SpriteCharacterView -> view.present(pack, animation)
        }
    }

    fun hide() {
        if (!attached) return
        try {
            root?.let(windowManager::removeViewImmediate)
        } catch (_: IllegalArgumentException) {
            // 시스템이 이미 창을 정리한 경우다. 다음 show에서 새로 만든다.
        } finally {
            attached = false
            // ModelViewer는 창에서 떨어질 때 엔진까지 해제한다. 같은 뷰를 다시 붙이면 캐릭터 없는 검은 사각형이 되니(기기에서 확인) 버리고 show마다 새로 만든다.
            root = null
            characterView = null
        }
    }

    /** 시스템 설정 화면을 열었으면 true. Pleos 에뮬레이터 이미지처럼 그 화면이 없는 기기에선 false다(그대로 열면 앱이 죽는다). */
    fun openPermissionSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun openSettings() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    /**
     * 오버레이가 머무를 수 있는 화면 영역(시스템 바 제외, 화면 좌표).
     * 앱 컨텍스트의 displayMetrics·currentWindowMetrics는 Pleos에서 마지막 액티비티 창 크기(앱 영역 1682x1210)를 돌려줘서(기기에서 확인) 쓰지 않고,
     * 화면 전체는 maximumWindowMetrics에서 읽는다.
     * ponytail: 시스템 바 안쪽이면 어디든 허용한다. Pleos가 가려도 되는 영역(harness/pleos-overlay-questions.md 11번)이 정해지면 그 영역으로 좁힌다.
     * 또 TOP|START 좌표가 화면 좌표와 같다고 본다(부모 프레임 원점이 0,0 — Pleos에서 확인). 컷아웃 등으로 어긋나면 getLocationOnScreen으로 보정한다.
     */
    @Suppress("DEPRECATION") // API 30 미만 폴백
    private fun usableArea(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val b = metrics.bounds
            return Rect(b.left + bars.left, b.top + bars.top, b.right - bars.right, b.bottom - bars.bottom)
        }
        val size = Point().also { windowManager.defaultDisplay.getRealSize(it) }
        return Rect(0, 0, size.x, size.y)
    }

    /** 끌면 창을 옮기고, 거의 안 움직이고 떼면 기존처럼 누른 것(설정 열기)으로 넘긴다. */
    @SuppressLint("ClickableViewAccessibility") // 누름은 performClick으로 그대로 넘긴다
    private fun dragListener(): View.OnTouchListener {
        // 차 안 터치는 손떨림이 커서, 탭이 끌기로 오인되지 않게 기본 slop의 2배로 둔다.
        val drag = OverlayDrag(ViewConfiguration.get(context).scaledTouchSlop * 2)
        return View.OnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> usableArea().let { a ->
                    drag.down(e.rawX, e.rawY, params.x, params.y, a.left, a.right - sizePx, a.top, a.bottom - sizePx)
                }
                MotionEvent.ACTION_MOVE -> drag.move(e.rawX, e.rawY)?.let { (x, y) ->
                    params.x = x
                    params.y = y
                    if (attached) root?.let { windowManager.updateViewLayout(it, params) }
                }
                MotionEvent.ACTION_UP -> if (drag.dragging) savePosition() else view.performClick()
                MotionEvent.ACTION_CANCEL -> if (drag.dragging) savePosition()
            }
            true
        }
    }

    private fun savePosition() {
        prefs.edit().putInt(PREF_X, params.x).putInt(PREF_Y, params.y).apply()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "VoiceCompanionCharacterOverlay"
    }

    private companion object {
        const val PREF_X = "characterpack.vr.x"
        const val PREF_Y = "characterpack.vr.y"
    }
}

internal enum class OverlayShowResult {
    SHOWING,
    PERMISSION_REQUIRED,
    REJECTED,
}
