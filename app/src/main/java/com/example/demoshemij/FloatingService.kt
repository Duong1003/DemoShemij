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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

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
                currentFlipUpdater = { newFlip ->
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
                    Log.d("duonghx","ACTION_DOWN")
                    isDragging = true
                    moveJob?.cancel() // HỦY ngay khi người dùng chạm
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
                    moveJob?.cancel()
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
        animateSpriteWindow11(params)
    }
    private suspend fun animateParamTo(
        params: WindowManager.LayoutParams,
        axis: String,
        target: Int,
        durationMillis: Int,
        isMovingRight: Boolean,
        onComplete: () -> Unit = {}
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
            Log.d("duonghx", "animateParamTo ${params.x} to ${params.y}")
            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                return
            }

            // Cập nhật hướng flip
            val flip = when {
                params.x <= 0 -> SpriteFlip.Horizontal // Cạnh trái
                params.x >= screenWidth - spriteWidth -> null // Cạnh phải
                params.y <= 0 -> SpriteFlip.Both // Cạnh trên
                params.y >= screenHeight - spriteHeight -> SpriteFlip.Horizontal // Cạnh dưới
                else -> if (isMovingRight) null else SpriteFlip.Horizontal
            }

            currentFlipUpdater?.invoke(flip)

            delay(delayPerStep.toLong())
        }

        // Gọi hàm onComplete khi hoàn thành
        onComplete()
    }

    private fun animateSpriteWindow11(params: WindowManager.LayoutParams) {
        floatingView.post {
            moveJob?.cancel()
            moveJob = lifecycleScope.launch {
                val (screenWidth, screenHeight) = getScreenSize(this@FloatingSpriteService)
                val spriteWidth = floatingView.width
                val spriteHeight = floatingView.height
                val margin = 0

                var isMovingRight = true // Theo dõi hướng di chuyển ngang

                while (!isDragging) {
                    // Chọn tỷ lệ dựa trên vị trí (cạnh trái, cạnh phải, hoặc ở giữa)
                    val action = Random.nextInt(100)

                    when {
                        // Cạnh trái: lên 10%, xuống 85%, nhảy 5%
                        params.x <= 0 -> {
                            when {
                                action < 10 -> { // 0-9: 10% - Di chuyển lên
                                    val maxUpDistance = params.y - margin // Khoảng cách tối đa có thể đi lên
                                    if (maxUpDistance > 180) { // Chỉ di chuyển nếu đủ khoảng cách
                                        val targetY = params.y - Random.nextInt(180, min(200, maxUpDistance)) // Di chuyển lên 180-200px
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    } else if (maxUpDistance > 0) {
                                        // Nếu khoảng cách nhỏ, di chuyển lên hết khoảng cách còn lại
                                        val targetY = margin
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    }
                                }
                                action < 95 -> { // 10-94: 85% - Di chuyển xuống
                                    val maxDownDistance = screenHeight - spriteHeight - margin - params.y // Khoảng cách tối đa đi xuống
                                    if (maxDownDistance > 180) { // Chỉ di chuyển nếu đủ khoảng cách
                                        val targetY = params.y + Random.nextInt(180, min(200, maxDownDistance)) // Di chuyển xuống 180-200px
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    } else if (maxDownDistance > 0) {
                                        // Nếu khoảng cách nhỏ, di chuyển xuống hết khoảng cách còn lại
                                        val targetY = screenHeight - spriteHeight - margin
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    }
                                }
                                else -> { // 95-99: 5% - Nhảy sang cạnh đối diện
                                    val targetX = screenWidth - spriteWidth - margin
                                    animateParamTo(params, "x", targetX, 2000, isMovingRight) {
                                        isMovingRight = !isMovingRight // Đổi hướng sau khi nhảy
                                    }
                                }
                            }
                        }
                        // Cạnh phải: lên 85%, xuống 10%, nhảy 5%
                        params.x >= screenWidth - spriteWidth - margin -> {
                            when {
                                action < 85 -> { // 0-84: 85% - Di chuyển lên
                                    val maxUpDistance = params.y - margin // Khoảng cách tối đa có thể đi lên
                                    if (maxUpDistance > 180) { // Chỉ di chuyển nếu đủ khoảng cách
                                        val targetY = params.y - Random.nextInt(180, min(200, maxUpDistance)) // Di chuyển lên 180-200px
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    } else if (maxUpDistance > 0) {
                                        // Nếu khoảng cách nhỏ, di chuyển lên hết khoảng cách còn lại
                                        val targetY = margin
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    }
                                }
                                action < 95 -> { // 85-94: 10% - Di chuyển xuống
                                    val maxDownDistance = screenHeight - spriteHeight - margin - params.y // Khoảng cách tối đa đi xuống
                                    if (maxDownDistance > 180) { // Chỉ di chuyển nếu đủ khoảng cách
                                        val targetY = params.y + Random.nextInt(180, min(200, maxDownDistance)) // Di chuyển xuống 180-200px
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    } else if (maxDownDistance > 0) {
                                        // Nếu khoảng cách nhỏ, di chuyển xuống hết khoảng cách còn lại
                                        val targetY = screenHeight - spriteHeight - margin
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    }
                                }
                                else -> { // 95-99: 5% - Nhảy sang cạnh đối diện
                                    val targetX = 0
                                    animateParamTo(params, "x", targetX, 2000, isMovingRight) {
                                        isMovingRight = !isMovingRight // Đổi hướng sau khi nhảy
                                    }
                                }
                            }
                        }
                        // Ở giữa: giữ tỷ lệ mặc định (lên 10%, xuống 85%, nhảy 5%)
                        else -> {
                            when {
                                action < 10 -> { // 0-9: 10% - Di chuyển lên
                                    val maxUpDistance = params.y - margin // Khoảng cách tối đa có thể đi lên
                                    if (maxUpDistance > 180) { // Chỉ di chuyển nếu đủ khoảng cách
                                        val targetY = params.y - Random.nextInt(180, min(200, maxUpDistance)) // Di chuyển lên 180-200px
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    } else if (maxUpDistance > 0) {
                                        // Nếu khoảng cách nhỏ, di chuyển lên hết khoảng cách còn lại
                                        val targetY = margin
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    }
                                }
                                action < 95 -> { // 10-94: 85% - Di chuyển xuống
                                    val maxDownDistance = screenHeight - spriteHeight - margin - params.y // Khoảng cách tối đa đi xuống
                                    if (maxDownDistance > 180) { // Chỉ di chuyển nếu đủ khoảng cách
                                        val targetY = params.y + Random.nextInt(180, min(200, maxDownDistance)) // Di chuyển xuống 180-200px
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    } else if (maxDownDistance > 0) {
                                        // Nếu khoảng cách nhỏ, di chuyển xuống hết khoảng cách còn lại
                                        val targetY = screenHeight - spriteHeight - margin
                                        animateParamTo(params, "y", targetY, 2000, isMovingRight)
                                    }
                                }
                                else -> { // 95-99: 5% - Nhảy sang cạnh đối diện
                                    val targetX = if (isMovingRight) screenWidth - spriteWidth - margin else 0
                                    animateParamTo(params, "x", targetX, 2000, isMovingRight) {
                                        isMovingRight = !isMovingRight // Đổi hướng sau khi nhảy
                                    }
                                }
                            }
                        }
                    }
                    // Thêm độ trễ nhỏ giữa các hành động để tránh di chuyển quá nhanh
                    delay(100)
                }
            }
        }
    }
    // 👉 Hàm rơi xuống (gravity effect)
    private suspend fun fallDown(params: WindowManager.LayoutParams) {
        val (_, screenHeight) = getScreenSize(this)
        val spriteHeight = floatingView.height
        val groundY = screenHeight - spriteHeight

        while (params.y < groundY) {
            // 👉 Nếu người dùng bắt đầu kéo lại => dừng ngay
            if (isDragging) return

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

    private suspend fun simulateFlingWithGravity(
        params: WindowManager.LayoutParams,
        initialVx: Float,
        initialVy: Float
    ) {
        val (screenWidth, screenHeight) = getScreenSize(this)
        val spriteWidth = floatingView.width
        val spriteHeight = floatingView.height

        var vx = initialVx
        var vy = initialVy
        val gravity = 0.8f       // lực hút xuống
        val friction = 0.98f     // ma sát không khí

        while (true) {
            if (isDragging) return

            params.x += vx.toInt()
            params.y += vy.toInt()

            // ✅ Áp dụng trọng lực mỗi frame
            vy += gravity

            // ✅ Giảm tốc dần
            vx *= friction
            vy *= friction

            // ✅ Giới hạn trong màn hình
            if (params.x < 0) {
                params.x = 0
                vx = -vx * 0.5f  // bật lại yếu dần
            }
            if (params.x > screenWidth - spriteWidth) {
                params.x = screenWidth - spriteWidth
                vx = -vx * 0.5f
            }
            if (params.y > screenHeight - spriteHeight) {
                params.y = screenHeight - spriteHeight
                vy = -vy * 0.3f // bật lại nhẹ khi chạm đất
                if (abs(vy) < 1f && abs(vx) < 1f) break // gần như dừng hẳn
            }

            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                return
            }

            delay(16) // ~60fps
        }
    }


    private suspend fun animateParamTo(
        params: WindowManager.LayoutParams,
        axis: String,
        target: Int,
        durationMillis: Int
    ) {
//        Log.d("duonghx","animateParamTo1 ${params.x} to ${params.y}")
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
            Log.d("duonghx","animateParamTo1 ${params.x} to ${params.y}")
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
