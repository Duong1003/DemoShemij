package com.example.demoshemij

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.demoshemij.domain.SpriteSpec
import com.example.demoshemij.ui.theme.DemoShemijTheme
import com.stevdza_san.sprite.domain.SpriteFlip
import com.stevdza_san.sprite.domain.SpriteSheet
import com.stevdza_san.sprite.domain.rememberSpriteState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class FloatingSpriteService : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ComposeView

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var moveJob: Job? = null
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        startForegroundService()
        setupFloatingView()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundService() {
        val channelId = "floating_sprite_channel"
        val channel = NotificationChannel(
            channelId,
            "Floating Sprite Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Floating Sprite Running")
            .setContentText("Sprite is floating on screen")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }
    companion object {
        var currentFlipUpdater: ((SpriteFlip?) -> Unit)? = null
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        floatingView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@FloatingSpriteService)
            setViewTreeSavedStateRegistryOwner(this@FloatingSpriteService)

            setContent {
                var spriteFlip by remember { mutableStateOf<SpriteFlip?>(null) }

                val spriteState = rememberSpriteState(
                    totalFrames = 9,
                    framesPerRow = 3,
                    animationSpeed = 80
                )
                val spriteSpec = SpriteSpec(
                    screenWidth = 360f,
                    default = SpriteSheet(
                        frameWidth = 253,
                        frameHeight = 303,
                        imageRes = R.drawable.sprite_normal
                    )
                )

                LaunchedEffect(Unit) {
                    spriteState.start()
                }

                MovingSprite(spriteState, spriteSpec, spriteFlip = spriteFlip)

                // Gán state này ra ngoài scope để service có thể điều khiển
                FloatingSpriteService.currentFlipUpdater = { newFlip ->
                    spriteFlip = newFlip
                }
            }

        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )



        params.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(floatingView, params)

        // 👇 Xử lý kéo / thả / dừng
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    moveJob?.cancel() // Dừng di chuyển tự động
                    Log.d("duonghx","ACTION_DOWN")
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    isDragging = true
                    Log.d("duonghx","ACTION_MOVE")
                    if (isDragging) {
                        params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    Log.d("duonghx","ACTION_UP")
                    isDragging = false
                    // 👇 Khi thả tay: rơi xuống đáy rồi tiếp tục di chuyển
                    lifecycleScope.launch {
                        fallDown(params)
                        animateSpriteWindow(params)
                    }
                    true
                }

                else -> false
            }
        }

//        // 👉 Bắt đầu chạy tự động
        animateSpriteWindow(params)
    }

    // 👉 Hàm rơi xuống (gravity effect)
    private suspend fun fallDown(params: WindowManager.LayoutParams) {
        val (_, screenHeight) = getScreenSize(this)
        val spriteHeight = floatingView.height
        val groundY = screenHeight - spriteHeight

        while (params.y < groundY) {
            params.y += 20
            windowManager.updateViewLayout(floatingView, params)
            delay(10)
        }
        params.y = groundY
        windowManager.updateViewLayout(floatingView, params)
    }

    // 👉 Di chuyển tự động vòng quanh màn hình
    private fun animateSpriteWindow(params: WindowManager.LayoutParams) {
       floatingView.post{
           moveJob?.cancel()
           moveJob = lifecycleScope.launch {
               val (screenWidth, screenHeight) = getScreenSize(this@FloatingSpriteService)
               val spriteWidth = floatingView.width
               val spriteHeight = floatingView.height
               val margin = 0

               while (!isDragging) {
                   animateParamTo(params, "x", screenWidth - spriteWidth - margin, 4000)
                   animateParamTo(params, "y", screenHeight - spriteHeight - margin, 4000)
                   animateParamTo(params, "x", margin, 4000)
                   animateParamTo(params, "y", margin, 4000)
               }
           }
       }
    }

    private suspend fun animateParamTo(
        params: WindowManager.LayoutParams,
        axis: String,
        target: Int,
        durationMillis: Int
    ) {
        val start = if (axis == "x") params.x else params.y
        val distance = target - start
        val steps = 60
        val delayPerStep = durationMillis / steps

        val (screenWidth, screenHeight) = getScreenSize(this)
        val spriteWidth = floatingView.width
        val spriteHeight = floatingView.height

        repeat(steps) { step ->
            if (isDragging) return
            val fraction = (step + 1).toFloat() / steps
            val value = start + (distance * fraction).toInt()
            if (axis == "x") params.x = value else params.y = value

            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                return
            }

            // 👉 Xác định cạnh
            val flip = when {
                params.x <= 0 -> SpriteFlip.Horizontal // cạnh trái
                params.x >= screenWidth - spriteWidth -> null // cạnh phải (không lật)
                params.y <= 0 -> SpriteFlip.Both // cạnh trên
                params.y >= screenHeight - spriteHeight -> SpriteFlip.Horizontal // cạnh dưới
                else -> null
            }

            // Cập nhật hướng flip trong Compose
            currentFlipUpdater?.invoke(flip)

            delay(delayPerStep.toLong())
        }
    }


//    private suspend fun animateParamTo(
//        params: WindowManager.LayoutParams,
//        axis: String,
//        target: Int,
//        durationMillis: Int
//    ) {
//        val start = if (axis == "x") params.x else params.y
//        val distance = target - start
//        val steps = 60
//        val delayPerStep = durationMillis / steps
//
//        repeat(steps) { step ->
//            if (isDragging) return // Dừng ngay khi người dùng chạm
//            val fraction = (step + 1).toFloat() / steps
//            val value = start + (distance * fraction).toInt()
//            if (axis == "x") params.x = value else params.y = value
//            try {
//                windowManager.updateViewLayout(floatingView, params)
//            } catch (e: Exception) {
//                return
//            }
//            delay(delayPerStep.toLong())
//        }
//    }

    @Suppress("DEPRECATION")
    private fun getScreenSize(context: Context): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()
            )
            val width = windowMetrics.bounds.width() - insets.left - insets.right
            val height = windowMetrics.bounds.height() - insets.top - insets.bottom
            width to height
        } else {
            val displayMetrics = context.resources.displayMetrics
            displayMetrics.widthPixels to displayMetrics.heightPixels
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        moveJob?.cancel()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
