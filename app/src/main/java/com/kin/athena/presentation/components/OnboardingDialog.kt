/*
 * Copyright (C) 2025 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kin.athena.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kin.athena.R

@Composable
fun OnboardingOverlay(
    onDismiss: () -> Unit,
    onFirewallClick: () -> Unit = {}
) {

    val color = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    var showTutorialDialog by remember { mutableStateOf(true) }
    
    val cutoutRadius = 32.dp
    val cutoutCenterX = 52.dp
    val cutoutCenterY = 88.dp
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Background overlay that blocks all clicks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // Absorb clicks - do nothing
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            ) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    size = size
                )
                
                drawCircle(
                    color = Color.Transparent,
                    radius = with(density) { cutoutRadius.toPx() },
                    center = Offset(
                        x = with(density) { cutoutCenterX.toPx() },
                        y = with(density) { cutoutCenterY.toPx() }
                    ),
                    blendMode = BlendMode.Clear
                )
                
                // Add a subtle highlight ring around the cutout to draw attention
                drawCircle(
                    color = color,
                    radius = with(density) { (cutoutRadius + 4.dp).toPx() },
                    center = Offset(
                        x = with(density) { cutoutCenterX.toPx() },
                        y = with(density) { cutoutCenterY.toPx() }
                    ),
                    style = Stroke(width = with(density) { 2.dp.toPx() })
                )
            }
        }
        
        Box(
            modifier = Modifier
                .offset(
                    x = cutoutCenterX - cutoutRadius,
                    y = cutoutCenterY - cutoutRadius
                )
                .size(cutoutRadius * 2)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onFirewallClick()
                    onDismiss() // Auto-dismiss when clicked
                }
        )
        
        // Tutorial dialog positioned lower and more to the left
        if (showTutorialDialog) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = (-85).dp, y = 45.dp)
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 250.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.tap_security_icon_instruction),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}