package com.example.pixellauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherScreen() }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var showAppDrawer by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }
    
    val homeScale = remember { Animatable(1f) }
    val homeAlpha = remember { Animatable(1f) }
    
    val time = remember { mutableStateOf(getCurrentTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            time.value = getCurrentTime()
            kotlinx.coroutines.delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        apps = pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            if (ri.activityInfo.packageName == context.packageName) null
            else AppInfo(
                label = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon = ri.loadIcon(pm)
            )
        }.sortedBy { it.label.lowercase() }
    }

    LaunchedEffect(isLaunching) {
        if (!isLaunching) {
            homeScale.snapTo(0.9f)
            homeAlpha.snapTo(0f)
            homeScale.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            homeAlpha.animateTo(1f, tween(200))
        }
    }

    BackHandler(enabled = showAppDrawer) { showAppDrawer = false }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scale(homeScale.value)
                .alpha(homeAlpha.value)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -30f) showAppDrawer = true
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            Text(text = time.value, color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Thin)
            Text(
                text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), 
                color = Color.Gray, 
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Свайп вверх для меню", 
                color = Color.DarkGray, 
                fontSize = 12.sp, 
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        AnimatedVisibility(
            visible = showAppDrawer,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250, easing = FastOutLinearInEasing)) + fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
                    Text(
                        text = "Все приложения", 
                        color = Color.White, 
                        fontSize = 20.sp, 
                        modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(apps) { app ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    isLaunching = true
                                    scope.launch {
                                        homeScale.animateTo(1.3f, tween(200, easing = FastOutLinearInEasing))
                                        homeAlpha.animateTo(0f, tween(150))
                                        
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                        context.startActivity(launchIntent)
                                        
                                        kotlinx.coroutines.delay(500)
                                        showAppDrawer = false 
                                        isLaunching = false
                                    }
                                }
                            ) {
                                val bitmap = remember(app) { app.icon.toBitmap(144, 144).asImageBitmap() }
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(56.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = app.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
