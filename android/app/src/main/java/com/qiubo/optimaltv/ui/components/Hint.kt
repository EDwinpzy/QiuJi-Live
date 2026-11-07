package com.qiubo.optimaltv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.qiubo.optimaltv.ui.theme.rememberUiScale
import com.qiubo.optimaltv.ui.theme.sx
import com.qiubo.optimaltv.ui.theme.sxs

/**
 * 底部轻提示（需求 足球#5：只保留文字，背景半透明灰色）。
 * 替代系统 Toast（自带深色底样式不可控）；文案 2.5s 自动消失由调用方管理。
 * 提示统一贴屏幕最下方（底边距 40）。
 */
@Composable
fun BoxScope.OtvHint(text: String?, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    val s = rememberUiScale()
    Box(
        modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 40f.sx(s))
            .background(Color(0x8C808080), RoundedCornerShape(24f.sx(s)))
            .padding(horizontal = 36f.sx(s), vertical = 16f.sx(s)),
    ) {
        Text(
            text, color = Color.White, fontSize = 26f.sxs(s), fontWeight = FontWeight.Medium,
        )
    }
}
