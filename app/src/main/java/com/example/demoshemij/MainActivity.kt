package com.example.demoshemij

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.demoshemij.ui.theme.DemoShemijTheme
import com.stevdza_san.sprite.component.SpriteView
import com.stevdza_san.sprite.domain.SpriteSheet
import com.example.demoshemij.domain.SpriteSpec
import com.stevdza_san.sprite.domain.SpriteFlip
import com.stevdza_san.sprite.domain.SpriteState
import com.stevdza_san.sprite.domain.rememberSpriteState
import com.stevdza_san.sprite.util.getScreenWidth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoShemijTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartService = { startFloatingService() },
                        onStopService = { stopFloatingService() },
                        checkOverlayPermission = { checkAndRequestOverlayPermission() }
                    )
//                    MovingSpriteAroundScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    private fun checkAndRequestOverlayPermission(): Boolean {
        return if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${this.packageName}")
            )
            startActivity(intent)
            false
        } else {
            true
        }
    }

    private fun startFloatingService() {
        if (Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(this, FloatingSpriteService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun stopFloatingService() {
        val serviceIntent = Intent(this, FloatingSpriteService::class.java)
        stopService(serviceIntent)
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    checkOverlayPermission: () -> Boolean
) {
    var isServiceRunning by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isServiceRunning) "Sprite đang chạy!" else "Sprite đã dừng",
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = {
                if (!isServiceRunning) {
                    if (checkOverlayPermission()) {
                        onStartService()
                        isServiceRunning = true
                    } else {
                        // Hiển thị thông báo yêu cầu quyền nếu cần
//                        Text("Vui lòng cấp quyền overlay!")
                    }
                }
            },
            enabled = !isServiceRunning
        ) {
            Text("Bật Sprite")
        }
        Button(
            onClick = {
                if (isServiceRunning) {
                    onStopService()
                    isServiceRunning = false
                }
            },
            enabled = isServiceRunning
        ) {
            Text("Tắt Sprite")
        }
    }
}
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DemoShemijTheme {
        Greeting("Android")
    }
}

@Composable
fun Test(modifier: Modifier = Modifier){
    val screenWidth = getScreenWidth()
    val spriteState = rememberSpriteState(
        totalFrames = 9,
        framesPerRow = 3,
        animationSpeed = 50
    )
    val animationRunning by spriteState.isRunning.collectAsState()
    val currentFrame by spriteState.currentFrame.collectAsState()
    DisposableEffect(Unit) {
        spriteState.start()
        onDispose {
            spriteState.stop()
            spriteState.cleanup()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpriteView(
            spriteState = spriteState,
            spriteSpec = SpriteSpec(
                screenWidth = screenWidth.value,
                default = SpriteSheet(
                    frameWidth = 253,
                    frameHeight = 303,
                    imageRes = R.drawable.sprite_normal
                ),
//                small = SpriteSheet(
//                    frameWidth = 149,
//                    frameHeight = 179,
//                    imageRes = R.drawable.sprite_small
//                ),
//                normal = SpriteSheet(
//                    frameWidth = 253,
//                    frameHeight = 303,
//                    imageRes = R.drawable.sprite_normal
//                ),
//                large = SpriteSheet(
//                    frameWidth = 377,
//                    frameHeight = 451,
//                    imageRes = R.drawable.sprite_large
//                ),
            )
        )
    }
}
@Composable
fun MovingSpriteAroundScreen(modifier: Modifier = Modifier) {
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

    BoxWithConstraints(  modifier = Modifier
        .size(150.dp)) {
        val density = LocalDensity.current
        val densityValue = density.density

        // Lấy kích thước màn hình thật (px)
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Kích thước sprite (px)
        val spriteWidthPx = spriteSpec.spriteSheet.frameWidth.toFloat()
        val spriteHeightPx = spriteSpec.spriteSheet.frameHeight.toFloat()

        // Giới hạn di chuyển (đảm bảo chạm mép thật)
        val maxX = (screenWidthPx - spriteWidthPx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - spriteHeightPx).coerceAtLeast(0f)

        val posX = remember { Animatable(0f) }
        val posY = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            spriteState.start()
            while (true) {
                // Đi sang phải
                posX.animateTo(maxX, tween(3000, easing = LinearEasing))
                // Đi xuống
                posY.animateTo(maxY, tween(3000, easing = LinearEasing))
                // Đi sang trái
                posX.animateTo(0f, tween(3000, easing = LinearEasing))
                // Đi lên
                posY.animateTo(0f, tween(3000, easing = LinearEasing))
            }
        }

        // Chuyển px → dp để dùng trong offset
        val offsetXDp = (posX.value / densityValue).dp
        val offsetYDp = (posY.value / densityValue).dp
        val spriteWidthDp = (spriteWidthPx / densityValue).dp
        val spriteHeightDp = (spriteHeightPx / densityValue).dp

        // Bọc Box để nhìn rõ sprite chạm mép
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            SpriteView(
                modifier = modifier
                    .offset(x = offsetXDp, y = offsetYDp)
                    .size(spriteWidthDp, spriteHeightDp),
                spriteState = spriteState,
                spriteSpec = spriteSpec
            )
        }
    }
}

@Composable
fun MovingSprite(
    spriteState: SpriteState,
    spriteSpec: SpriteSpec,
    modifier: Modifier = Modifier
) {
    SpriteView(
        modifier = modifier,
            // kích thước nhân vật
        spriteState = spriteState,
        spriteSpec = spriteSpec
    )
}


@Composable
fun MovingSpriteAroundScreen1(modifier: Modifier=  Modifier) {
    val spriteState = rememberSpriteState(
        totalFrames = 9,
        framesPerRow = 3,
        animationSpeed = 80
    )

    // Tạo spriteSpec như bạn đang dùng
    val spriteSpec = SpriteSpec(
        screenWidth = /* dùng getScreenWidth().value nếu cần */ 360f,
        default = SpriteSheet(
            frameWidth = 253,   // giả sử đây là pixel (theo file sprite)
            frameHeight = 303,
            imageRes = R.drawable.sprite_normal
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthDp = this.maxWidth      // Dp
        val maxHeightDp = this.maxHeight    // Dp
        val density = LocalDensity.current

        // Chuyển frame kích thước (giả sử frameWidth/Height là px) sang Dp
        val spriteWidthDp = with(density) { spriteSpec.spriteSheet.frameWidth.toDp() }
        val spriteHeightDp = with(density) { spriteSpec.spriteSheet.frameHeight.toDp() }

        // Tính giới hạn tối đa (lấy value Float của Dp để dùng với Animatable<Float>)
        val maxX = (maxWidthDp - spriteWidthDp).coerceAtLeast(0.dp).value
        val maxY = (maxHeightDp - spriteHeightDp).coerceAtLeast(0.dp).value

        val posX = remember { Animatable(0f) }
        val posY = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            spriteState.start()
            while (true) {
                // Sang phải
                posX.animateTo(targetValue = maxX, animationSpec = tween(durationMillis = 7000))
                // Xuống dưới (sát mép vì đã trừ spriteHeightDp)
                posY.animateTo(targetValue = maxY, animationSpec = tween(durationMillis = 7000))
                // Sang trái
                posX.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 7000))
                // Lên trên
                posY.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 7000))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {} // Không intercept event
        ) {
            SpriteView(
                modifier = modifier,
                spriteState = spriteState,
                spriteSpec = spriteSpec,
//                spriteFlip = SpriteFlip.Vertical
            )
        }
    }
    }
