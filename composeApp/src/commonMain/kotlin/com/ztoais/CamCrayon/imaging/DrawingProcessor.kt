package com.ztoais.CamCrayon.imaging

import com.ztoais.CamCrayon.model.*
import kotlin.math.*
import kotlin.random.Random

enum class KidDrawingPreset(
    val id: String,
    val title: String,
    val kidArtistTag: String,
    val emoji: String,
    val description: String
) {
    FLYING_DINO(
        id = "dino",
        title = "Barnaby the Winged Dino",
        kidArtistTag = "Drawn by Leo, Age 6 (Crayon on Paper)",
        emoji = "🦖",
        description = "A smiling green dinosaur with golden fairy wings and a tiny party crown!"
    ),
    ASTRO_CAT_ROCKET(
        id = "rocket",
        title = "Captain Whiskers' Rocket",
        kidArtistTag = "Drawn by Maya, Age 7 (Markers on Sketchpad)",
        emoji = "🚀",
        description = "An orange space-cat inside a red rocket ship blasting rainbow stars!"
    ),
    CORAL_OCTOPUS(
        id = "octopus",
        title = "Princess Pippa Octopus",
        kidArtistTag = "Drawn by Aria, Age 5 (Pastel Crayons)",
        emoji = "🐙",
        description = "A happy pink octopus wearing a golden tiara and juggling shimmer bubbles!"
    ),
    DISCO_ROBOT(
        id = "robot",
        title = "Sparky the Boogie Bot",
        kidArtistTag = "Drawn by Sam, Age 8 (Color Pencils)",
        emoji = "🤖",
        description = "A friendly turquoise robot with springy legs, a glowing heart chest, and a lightning antenna!"
    )
}

object DrawingProcessor {

    /**
     * Takes a camera capture or drawing on paper (or a Nano Banana 2.1 character image)
     * and extracts the drawn character as a clean transparent puppet cutout with a white sticker outline!
     * Uses adaptive local-illumination paper estimation + border flood-fill + connected-component filtering
     * so uneven room shadows or desk borders in real camera photos are stripped away while keeping interior whites!
     */
    fun extractCharacterFromPaper(source: RgbaBuffer, addStickerBorder: Boolean = true): RgbaBuffer {
        val targetMaxDim = 340
        val scaled = scaleDownIfNeeded(source, targetMaxDim)
        val w = scaled.width
        val h = scaled.height

        // 1. Compute local paper illumination across an 8x8 spatial grid (using upper quartile brightness in each cell)
        // so shadows or uneven room lighting across a camera photo of paper are normalized automatically.
        val gridCols = 8
        val gridRows = 8
        val gridR = IntArray(gridCols * gridRows)
        val gridG = IntArray(gridCols * gridRows)
        val gridB = IntArray(gridCols * gridRows)
        val gridLum = IntArray(gridCols * gridRows)

        for (gy in 0 until gridRows) {
            val y0 = (gy * h) / gridRows
            val y1 = (((gy + 1) * h) / gridRows).coerceAtMost(h)
            for (gx in 0 until gridCols) {
                val x0 = (gx * w) / gridCols
                val x1 = (((gx + 1) * w) / gridCols).coerceAtMost(w)

                // Find bright low-saturation paper pixels in this cell
                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var sumLum = 0L
                var count = 0
                for (y in y0 until y1 step 2) {
                    for (x in x0 until x1 step 2) {
                        val p = scaled.getPixel(x, y)
                        val a = (p ushr 24) and 0xFF
                        if (a < 100) continue
                        val r = (p ushr 16) and 0xFF
                        val g = (p ushr 8) and 0xFF
                        val b = p and 0xFF
                        val maxC = maxOf(r, g, b)
                        val minC = minOf(r, g, b)
                        val sat = if (maxC == 0) 0 else ((maxC - minC) * 255) / maxC
                        val lum = (r * 299 + g * 587 + b * 114) / 1000
                        if (sat < 55 && lum > 115) {
                            sumR += r
                            sumG += g
                            sumB += b
                            sumLum += lum
                            count++
                        }
                    }
                }
                val cellIdx = gy * gridCols + gx
                if (count > 0) {
                    gridR[cellIdx] = (sumR / count).toInt()
                    gridG[cellIdx] = (sumG / count).toInt()
                    gridB[cellIdx] = (sumB / count).toInt()
                    gridLum[cellIdx] = (sumLum / count).toInt()
                } else {
                    gridR[cellIdx] = 235
                    gridG[cellIdx] = 232
                    gridB[cellIdx] = 225
                    gridLum[cellIdx] = 232
                }
            }
        }

        // Smooth the grid so cells mostly covered by a large drawing still inherit neighbor paper brightness
        val smoothLum = gridLum.copyOf()
        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                var maxNeighborLum = gridLum[gy * gridCols + gx]
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = (gx + dx).coerceIn(0, gridCols - 1)
                        val ny = (gy + dy).coerceIn(0, gridRows - 1)
                        maxNeighborLum = maxOf(maxNeighborLum, gridLum[ny * gridCols + nx])
                    }
                }
                smoothLum[gy * gridCols + gx] = maxNeighborLum
            }
        }

        // 2. Classify each pixel as candidate paper/border background vs colorful/dark crayon foreground
        val isPaperLike = BooleanArray(w * h)
        val borderMarginX = (w * 0.04f).toInt().coerceAtLeast(2)
        val borderMarginY = (h * 0.04f).toInt().coerceAtLeast(2)

        for (y in 0 until h) {
            val gy = ((y * gridRows) / h).coerceIn(0, gridCols - 1)
            for (x in 0 until w) {
                val idx = y * w + x
                val p = scaled.pixels[idx]
                val a = (p ushr 24) and 0xFF
                if (a < 45) {
                    isPaperLike[idx] = true
                    continue
                }

                // Treat outermost 4% camera frame edge as passable so a dark desk edge around the paper doesn't trap the paper
                if (x < borderMarginX || x >= w - borderMarginX || y < borderMarginY || y >= h - borderMarginY) {
                    isPaperLike[idx] = true
                    continue
                }

                val gx = ((x * gridCols) / w).coerceIn(0, gridCols - 1)
                val cellIdx = gy * gridCols + gx
                val locR = gridR[cellIdx]
                val locG = gridG[cellIdx]
                val locB = gridB[cellIdx]
                val locLum = smoothLum[cellIdx]

                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF

                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val saturation = if (maxC == 0) 0 else ((maxC - minC) * 255) / maxC
                val lum = (r * 299 + g * 587 + b * 114) / 1000

                val dr = r - locR
                val dg = g - locG
                val db = b - locB
                val distFromLocalPaper = sqrt((dr * dr + dg * dg + db * db).toDouble())
                val darkerThanPaper = locLum - lum

                // Faint ruled notebook lines or shadowy white/cream paper have low saturation and are close to local paper luminance
                val isNotebookLine = (darkerThanPaper < 38 && saturation < 62 && distFromLocalPaper < 66.0)
                val isShadowyPaper = (darkerThanPaper < 30 && saturation < 48) || (lum > 215 && saturation < 35)

                isPaperLike[idx] = isShadowyPaper || isNotebookLine
            }
        }

        // 3. Flood-fill from outer perimeter AND outer 10% paper ring through paper-like pixels to find true exterior background
        val isExteriorBg = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        fun tryEnqueue(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val i = y * w + x
            if (!isExteriorBg[i] && isPaperLike[i]) {
                isExteriorBg[i] = true
                queue[tail++] = i
            }
        }

        for (x in 0 until w) {
            tryEnqueue(x, 0)
            tryEnqueue(x, h - 1)
            tryEnqueue(x, borderMarginY)
            tryEnqueue(x, h - 1 - borderMarginY)
        }
        for (y in 0 until h) {
            tryEnqueue(0, y)
            tryEnqueue(w - 1, y)
            tryEnqueue(borderMarginX, y)
            tryEnqueue(w - 1 - borderMarginX, y)
        }

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % w
            val cy = curr / w
            tryEnqueue(cx + 1, cy)
            tryEnqueue(cx - 1, cy)
            tryEnqueue(cx, cy + 1)
            tryEnqueue(cx, cy - 1)
        }

        // 4. Connected-component filtering to remove tiny isolated camera sensor speckles (< 18px)
        val fgMask = BooleanArray(w * h)
        for (i in 0 until w * h) {
            fgMask[i] = !isExteriorBg[i]
        }

        val visited = BooleanArray(w * h)
        val compQueue = IntArray(w * h)
        for (startIdx in 0 until w * h) {
            if (!fgMask[startIdx] || visited[startIdx]) continue
            var cHead = 0
            var cTail = 0
            compQueue[cTail++] = startIdx
            visited[startIdx] = true
            while (cHead < cTail) {
                val curr = compQueue[cHead++]
                val cx = curr % w
                val cy = curr / w
                val neighbors = intArrayOf(
                    if (cx + 1 < w) curr + 1 else -1,
                    if (cx - 1 >= 0) curr - 1 else -1,
                    if (cy + 1 < h) curr + w else -1,
                    if (cy - 1 >= 0) curr - w else -1
                )
                for (nb in neighbors) {
                    if (nb >= 0 && fgMask[nb] && !visited[nb]) {
                        visited[nb] = true
                        compQueue[cTail++] = nb
                    }
                }
            }
            // Clear tiny isolated camera speckles so only real character strokes remain
            if (cTail < 18) {
                for (k in 0 until cTail) {
                    fgMask[compQueue[k]] = false
                }
            }
        }

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var fgCount = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (fgMask[i]) {
                    fgCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        // Fallback if image was completely dark or solid: keep center subject
        if (fgCount < (w * h) / 40) {
            for (y in h / 8 until (h * 7) / 8) {
                for (x in w / 8 until (w * 7) / 8) {
                    val i = y * w + x
                    if (!isPaperLike[i]) {
                        fgMask[i] = true
                        minX = minOf(minX, x)
                        maxX = maxOf(maxX, x)
                        minY = minOf(minY, y)
                        maxY = maxOf(maxY, y)
                        fgCount++
                    }
                }
            }
        }

        if (minX >= maxX || minY >= maxY) {
            minX = 0
            minY = 0
            maxX = w - 1
            maxY = h - 1
        }

        // 4. Create cropped & centered square sprite buffer with vibrant color boost + white sticker halo
        val subjW = (maxX - minX + 1).coerceAtLeast(1)
        val subjH = (maxY - minY + 1).coerceAtLeast(1)
        val pad = 18
        val outSize = maxOf(subjW, subjH) + pad * 2
        val out = RgbaBuffer(outSize, outSize)

        val offsetX = (outSize - subjW) / 2 - minX
        val offsetY = (outSize - subjH) / 2 - minY

        // Optional 4px white die-cut sticker border around foreground pixels
        if (addStickerBorder) {
            val borderRadius = 4
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    if (fgMask[y * w + x]) {
                        val ox = x + offsetX
                        val oy = y + offsetY
                        for (dy in -borderRadius..borderRadius) {
                            for (dx in -borderRadius..borderRadius) {
                                if (dx * dx + dy * dy <= borderRadius * borderRadius + 1) {
                                    out.setPixel(ox + dx, oy + dy, 0xFFFFFFFF.toInt())
                                }
                            }
                        }
                    }
                }
            }
        }

        // Copy foreground character pixels with slight crayon vibrancy enhancement
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val i = y * w + x
                if (fgMask[i]) {
                    val p = scaled.pixels[i]
                    val boosted = boostCrayonVibrancy(p)
                    out.setPixel(x + offsetX, y + offsetY, boosted)
                }
            }
        }

        return out
    }

    private fun boostCrayonVibrancy(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val avg = (r + g + b) / 3
        val factor = 1.22f
        val nr = (avg + ((r - avg) * factor).toInt()).coerceIn(0, 255)
        val ng = (avg + ((g - avg) * factor).toInt()).coerceIn(0, 255)
        val nb = (avg + ((b - avg) * factor).toInt()).coerceIn(0, 255)
        return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    fun scaleDownIfNeeded(source: RgbaBuffer, maxDim: Int): RgbaBuffer {
        if (source.width <= maxDim && source.height <= maxDim) return source
        val scale = maxDim.toFloat() / maxOf(source.width, source.height).toFloat()
        val nw = (source.width * scale).toInt().coerceAtLeast(16)
        val nh = (source.height * scale).toInt().coerceAtLeast(16)
        val out = RgbaBuffer(nw, nh)
        for (y in 0 until nh) {
            val sy = ((y.toFloat() / nh) * source.height).toInt().coerceIn(0, source.height - 1)
            for (x in 0 until nw) {
                val sx = ((x.toFloat() / nw) * source.width).toInt().coerceIn(0, source.width - 1)
                out.setPixel(x, y, source.getPixel(sx, sy))
            }
        }
        return out
    }

    /**
     * Summarizes dominant colors in the child's drawing for display & prompt enrichment.
     */
    fun describeDominantColors(buffer: RgbaBuffer): String {
        var redCount = 0
        var greenCount = 0
        var blueCount = 0
        var yellowCount = 0
        var pinkPurpleCount = 0
        var orangeCount = 0

        for (p in buffer.pixels) {
            val a = (p ushr 24) and 0xFF
            if (a < 128) continue
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            if (r > 220 && g > 220 && b > 220) continue // skip white sticker/paper
            when {
                r > 190 && g > 170 && b < 110 -> yellowCount++
                r > 200 && g in 90..175 && b < 100 -> orangeCount++
                r > 180 && b > 140 && g < 140 -> pinkPurpleCount++
                r > g + 45 && r > b + 45 -> redCount++
                g > r + 25 && g > b + 20 -> greenCount++
                b > r + 25 && b > g + 10 -> blueCount++
            }
        }

        val counts = listOf(
            "emerald green" to greenCount,
            "cherry red" to redCount,
            "ocean blue" to blueCount,
            "sunshine yellow" to yellowCount,
            "tangerine orange" to orangeCount,
            "berry pink & purple" to pinkPurpleCount
        ).sortedByDescending { it.second }.filter { it.second > 20 }

        return if (counts.isEmpty()) {
            "colorful rainbow crayon strokes"
        } else {
            counts.take(3).joinToString(", ") { it.first }
        }
    }

    /**
     * Generates a realistic hand-drawn kid's crayon drawing on notebook paper for instant camera simulation & testing!
     */
    fun generateKidPaperDrawing(preset: KidDrawingPreset, width: Int = 360, height: Int = 360): RgbaBuffer {
        val buf = RgbaBuffer(width, height)
        val rng = Random(preset.ordinal * 777 + 42)

        // 1. Warm cream sketchpad paper with subtle grain & faint blue notebook lines
        for (y in 0 until height) {
            val isRuleLine = (y > 40 && y % 36 == 0)
            for (x in 0 until width) {
                val grain = rng.nextInt(-4, 5)
                val r = (250 + grain).coerceIn(0, 255)
                val g = (247 + grain).coerceIn(0, 255)
                val b = (238 + grain).coerceIn(0, 255)
                val color = if (isRuleLine) {
                    argb(255, 218, 230, 242)
                } else {
                    argb(255, r, g, b)
                }
                buf.setPixel(x, y, color)
            }
        }

        // Helper crayon drawing primitives with organic hand-drawn wobble and wax texture
        fun drawCrayonCircle(cx: Float, cy: Float, radius: Float, fillArgb: Int, outlineArgb: Int = 0xFF222222.toInt()) {
            val rInt = (radius + 6).toInt()
            for (dy in -rInt..rInt) {
                for (dx in -rInt..rInt) {
                    val px = (cx + dx).toInt()
                    val py = (cy + dy).toInt()
                    if (px !in 0 until width || py !in 0 until height) continue
                    val angle = atan2(dy.toDouble(), dx.toDouble())
                    val wobble = 2.2 * sin(angle * 5.0 + cx * 0.1) + 1.4 * cos(angle * 8.0 + cy * 0.1)
                    val dist = sqrt((dx * dx + dy * dy).toDouble()) + wobble
                    if (dist <= radius - 3.2) {
                        // Wax crayon texture
                        val tex = ((px * 13 + py * 29) % 11)
                        if (tex != 0) {
                            buf.setPixel(px, py, jitterColor(fillArgb, (tex - 5) * 2))
                        }
                    } else if (dist <= radius + 1.5) {
                        buf.setPixel(px, py, outlineArgb)
                    }
                }
            }
        }

        fun drawCrayonOval(cx: Float, cy: Float, rx: Float, ry: Float, fillArgb: Int, outlineArgb: Int = 0xFF222222.toInt(), tiltRad: Float = 0f) {
            val maxR = (maxOf(rx, ry) + 8).toInt()
            val cosT = cos(tiltRad)
            val sinT = sin(tiltRad)
            for (dy in -maxR..maxR) {
                for (dx in -maxR..maxR) {
                    val px = (cx + dx).toInt()
                    val py = (cy + dy).toInt()
                    if (px !in 0 until width || py !in 0 until height) continue
                    val lx = dx * cosT + dy * sinT
                    val ly = -dx * sinT + dy * cosT
                    val norm = sqrt((lx * lx) / (rx * rx) + (ly * ly) / (ry * ry))
                    val wobble = 0.03f * sin((px * 0.2f) + (py * 0.15f))
                    val d = norm + wobble
                    if (d <= 0.90f) {
                        val tex = ((px * 17 + py * 31) % 12)
                        if (tex != 0) {
                            buf.setPixel(px, py, jitterColor(fillArgb, (tex - 6) * 2))
                        }
                    } else if (d <= 1.04f) {
                        buf.setPixel(px, py, outlineArgb)
                    }
                }
            }
        }

        fun drawCrayonLine(x0: Float, y0: Float, x1: Float, y1: Float, thickness: Float, colorArgb: Int) {
            val steps = (hypot(x1 - x0, y1 - y0) * 1.5f).toInt().coerceAtLeast(1)
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val wx = x0 + (x1 - x0) * t + 1.5f * sin(t * PI.toFloat() * 3f)
                val wy = y0 + (y1 - y0) * t + 1.5f * cos(t * PI.toFloat() * 3f)
                val r = thickness.toInt()
                for (dy in -r..r) {
                    for (dx in -r..r) {
                        if (dx * dx + dy * dy <= thickness * thickness) {
                            buf.setPixel((wx + dx).toInt(), (wy + dy).toInt(), colorArgb)
                        }
                    }
                }
            }
        }

        fun drawSolidCircle(cx: Float, cy: Float, radius: Float, fillArgb: Int, outlineArgb: Int = fillArgb) {
            val rInt = (radius + 3).toInt()
            for (dy in -rInt..rInt) {
                for (dx in -rInt..rInt) {
                    val px = (cx + dx).toInt()
                    val py = (cy + dy).toInt()
                    if (px !in 0 until width || py !in 0 until height) continue
                    val dist = sqrt((dx * dx + dy * dy).toDouble())
                    if (dist <= radius - 2.0) {
                        buf.setPixel(px, py, fillArgb)
                    } else if (dist <= radius) {
                        buf.setPixel(px, py, outlineArgb)
                    }
                }
            }
        }

        fun drawGoofyEye(cx: Float, cy: Float, radius: Float) {
            drawSolidCircle(cx, cy, radius, argb(255, 255, 255, 255), argb(255, 30, 30, 35))
            drawSolidCircle(cx + 2f, cy + 1f, radius * 0.52f, argb(255, 20, 24, 35))
            drawSolidCircle(cx - 2.5f, cy - 2.5f, radius * 0.24f, argb(255, 255, 255, 255))
        }

        when (preset) {
            KidDrawingPreset.FLYING_DINO -> {
                // Golden fairy wings behind Barnaby
                drawCrayonOval(125f, 165f, 48f, 28f, argb(255, 255, 213, 79), argb(255, 180, 110, 20), tiltRad = -0.45f)
                drawCrayonOval(235f, 165f, 48f, 28f, argb(255, 255, 213, 79), argb(255, 180, 110, 20), tiltRad = 0.45f)

                // Red back spikes & tail
                drawCrayonCircle(115f, 235f, 14f, argb(255, 244, 63, 94))
                drawCrayonCircle(122f, 195f, 14f, argb(255, 244, 63, 94))
                drawCrayonCircle(135f, 140f, 14f, argb(255, 244, 63, 94))
                drawCrayonOval(112f, 252f, 38f, 20f, argb(255, 34, 197, 94), tiltRad = 0.35f)

                // Plump green dino body & yellow belly
                drawCrayonOval(180f, 220f, 58f, 68f, argb(255, 34, 197, 94))
                drawCrayonOval(188f, 228f, 36f, 46f, argb(255, 253, 224, 71), argb(255, 22, 163, 74))

                // Dino legs & happy arms
                drawCrayonOval(152f, 292f, 20f, 28f, argb(255, 34, 197, 94))
                drawCrayonOval(208f, 292f, 20f, 28f, argb(255, 34, 197, 94))
                drawCrayonOval(126f, 205f, 22f, 12f, argb(255, 34, 197, 94), tiltRad = -0.3f)
                drawCrayonOval(236f, 200f, 22f, 12f, argb(255, 34, 197, 94), tiltRad = 0.4f)

                // Big smiling dino head
                drawCrayonOval(192f, 126f, 56f, 44f, argb(255, 34, 197, 94))
                drawGoofyEye(174f, 114f, 13f)
                drawGoofyEye(206f, 114f, 13f)
                // Rosy cheeks & big crayon grin
                drawCrayonCircle(156f, 134f, 9f, argb(255, 251, 113, 133), argb(255, 251, 113, 133))
                drawCrayonLine(170f, 142f, 200f, 148f, 3.5f, argb(255, 30, 30, 35))
                drawCrayonLine(200f, 148f, 222f, 136f, 3.5f, argb(255, 30, 30, 35))

                // Little golden party crown on head
                drawCrayonLine(172f, 82f, 182f, 62f, 5f, argb(255, 245, 158, 11))
                drawCrayonLine(182f, 62f, 192f, 78f, 5f, argb(255, 245, 158, 11))
                drawCrayonLine(192f, 78f, 204f, 60f, 5f, argb(255, 245, 158, 11))
                drawCrayonLine(204f, 60f, 212f, 82f, 5f, argb(255, 245, 158, 11))
                drawCrayonLine(170f, 82f, 214f, 82f, 6f, argb(255, 245, 158, 11))
            }

            KidDrawingPreset.ASTRO_CAT_ROCKET -> {
                // Rainbow rocket exhaust flames at bottom
                drawCrayonOval(180f, 302f, 34f, 36f, argb(255, 249, 115, 22))
                drawCrayonOval(180f, 296f, 22f, 26f, argb(255, 250, 204, 21))

                // Blue side fins
                drawCrayonOval(120f, 248f, 28f, 38f, argb(255, 59, 130, 246), tiltRad = 0.4f)
                drawCrayonOval(240f, 248f, 28f, 38f, argb(255, 59, 130, 246), tiltRad = -0.4f)

                // Red & silver rocket fuselage
                drawCrayonOval(180f, 185f, 56f, 92f, argb(255, 239, 68, 68))
                // Nose cone
                drawCrayonCircle(180f, 86f, 28f, argb(255, 250, 204, 21))

                // Round glass porthole window with Astro-Cat inside!
                drawCrayonCircle(180f, 175f, 44f, argb(255, 186, 230, 253), argb(255, 30, 64, 175))
                // Cat ears
                drawCrayonCircle(156f, 148f, 13f, argb(255, 249, 115, 22))
                drawCrayonCircle(204f, 148f, 13f, argb(255, 249, 115, 22))
                // Orange tabby cat head
                drawCrayonCircle(180f, 178f, 30f, argb(255, 251, 146, 60))
                drawGoofyEye(168f, 172f, 9f)
                drawGoofyEye(192f, 172f, 9f)
                // Pink cat nose & whiskers
                drawCrayonCircle(180f, 184f, 5f, argb(255, 244, 114, 182))
                drawCrayonLine(146f, 182f, 166f, 185f, 2f, argb(255, 40, 40, 40))
                drawCrayonLine(194f, 185f, 214f, 182f, 2f, argb(255, 40, 40, 40))
            }

            KidDrawingPreset.CORAL_OCTOPUS -> {
                // 6 curly waving tentacles at bottom
                val tentacleX = floatArrayOf(105f, 132f, 162f, 196f, 226f, 254f)
                val tentacleTilt = floatArrayOf(0.45f, 0.25f, 0.08f, -0.08f, -0.25f, -0.45f)
                for (i in tentacleX.indices) {
                    drawCrayonOval(
                        tentacleX[i],
                        258f,
                        18f,
                        48f,
                        argb(255, 244, 114, 182),
                        argb(255, 157, 23, 77),
                        tiltRad = tentacleTilt[i]
                    )
                    drawCrayonCircle(tentacleX[i], 295f, 11f, argb(255, 192, 132, 252), argb(255, 157, 23, 77))
                }

                // Big round pink octopus head
                drawCrayonOval(180f, 168f, 74f, 64f, argb(255, 244, 114, 182), argb(255, 157, 23, 77))
                drawGoofyEye(154f, 162f, 15f)
                drawGoofyEye(206f, 162f, 15f)
                // Rosy cheeks & sweet smile
                drawCrayonCircle(132f, 180f, 10f, argb(255, 251, 113, 133), argb(255, 251, 113, 133))
                drawCrayonCircle(228f, 180f, 10f, argb(255, 251, 113, 133), argb(255, 251, 113, 133))
                drawCrayonLine(162f, 188f, 180f, 198f, 3.5f, argb(255, 131, 24, 67))
                drawCrayonLine(180f, 198f, 198f, 188f, 3.5f, argb(255, 131, 24, 67))

                // Starry golden sea-princess tiara
                drawCrayonOval(180f, 96f, 38f, 18f, argb(255, 250, 204, 21), argb(255, 180, 83, 9))
                drawCrayonCircle(180f, 88f, 10f, argb(255, 56, 189, 248), argb(255, 180, 83, 9))
            }

            KidDrawingPreset.DISCO_ROBOT -> {
                // Antenna with glowing yellow star bulb
                drawCrayonLine(180f, 65f, 180f, 105f, 5f, argb(255, 71, 85, 105))
                drawCrayonCircle(180f, 60f, 14f, argb(255, 250, 204, 21), argb(255, 234, 88, 12))

                // Waving robot arms
                drawCrayonOval(100f, 195f, 28f, 14f, argb(255, 56, 189, 248), tiltRad = -0.5f)
                drawCrayonOval(260f, 185f, 28f, 14f, argb(255, 56, 189, 248), tiltRad = 0.5f)
                drawCrayonCircle(78f, 180f, 13f, argb(255, 250, 204, 21))
                drawCrayonCircle(282f, 170f, 13f, argb(255, 250, 204, 21))

                // Springy legs
                drawCrayonOval(150f, 288f, 18f, 34f, argb(255, 148, 163, 184))
                drawCrayonOval(210f, 288f, 18f, 34f, argb(255, 148, 163, 184))
                drawCrayonOval(146f, 316f, 24f, 12f, argb(255, 239, 68, 68))
                drawCrayonOval(214f, 316f, 24f, 12f, argb(255, 239, 68, 68))

                // Robot torso & head
                drawCrayonOval(180f, 222f, 58f, 52f, argb(255, 14, 165, 233))
                drawCrayonOval(180f, 134f, 52f, 40f, argb(255, 56, 189, 248))
                drawGoofyEye(160f, 128f, 13f)
                drawGoofyEye(200f, 128f, 13f)
                drawCrayonLine(158f, 152f, 202f, 152f, 4f, argb(255, 250, 204, 21))

                // Glowing pink heart badge on robot chest
                drawCrayonCircle(170f, 215f, 15f, argb(255, 244, 63, 94), argb(255, 255, 255, 255))
                drawCrayonCircle(190f, 215f, 15f, argb(255, 244, 63, 94), argb(255, 255, 255, 255))
                drawCrayonCircle(180f, 228f, 15f, argb(255, 244, 63, 94), argb(255, 255, 255, 255))
            }
        }

        return buf
    }

    /**
     * Built-in rich 4-scene story adventures for each preset or custom drawing fallback,
     * so kids experience instant 60 FPS multi-scene magic even offline or while testing!
     */
    fun createRichStoryForPreset(preset: KidDrawingPreset, dominantColors: String): StoryAdventure {
        return when (preset) {
            KidDrawingPreset.FLYING_DINO -> StoryAdventure(
                storyTitle = "Barnaby and the Cloudberry Crown",
                characterName = "Barnaby the Winged Dino",
                characterSpeciesOrType = "Emerald Fairy-Winged Tyrannosaurus",
                detectedDrawingSummary = "A joyful hand-drawn dinosaur in $dominantColors with golden wings, red back spikes, and a shiny party crown!",
                personalityTrait = "Giggly, brave, and loves sharing cloud-berries",
                superpower = "Flutter-Roar Rainbow Gust",
                moralOrSparkleMessage = "Even the biggest dinosaurs can fly high when they believe in their tiny wings!",
                scenes = listOf(
                    StoryScene(
                        sceneNumber = 1,
                        sceneTitle = "Scene 1: The Big Meadow Bounce",
                        narrationText = "Once upon a sunny morning, Barnaby the dinosaur wiggled his tiny golden wings in Whispering Meadow. Every time he giggled, his feet bounced higher than the daisies!",
                        characterSpeechBubble = "Rawr-hee-hee! Watch me bounce to the clouds! 🦖✨",
                        soundEffectOnomatopoeia = "BOING-FLAP! 🌈",
                        biome = WorldBiome.SUNNY_MEADOW,
                        motionStyle = MotionStyle.BOUNCE_ADVENTURE,
                        particleMagic = ParticleMagicType.SPARKLES,
                        speedMultiplier = 1.05f,
                        jumpHeightRatio = 0.25f,
                        squashStretchIntensity = 0.24f,
                        skyTopColorHex = "#38BDF8",
                        skyBottomColorHex = "#FEF08A",
                        groundColorHex = "#4ADE80",
                        accentColorHex = "#F59E0B",
                        companions = listOf(
                            CompanionProp("Pip the Butterfly", CompanionType.BUTTERFLY, "#F472B6", 1.2f),
                            CompanionProp("Sunny Sprite", CompanionType.STAR_SPRITE, "#FACC15", 0.9f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 2,
                        sceneTitle = "Scene 2: Soaring Over Volcano Island",
                        narrationText = "WHOOSH! A warm marshmallow breeze lifted Barnaby right off the grass! He zoomed in loop-de-loops past Friendly Volcano Island, where baby birds cheered for his shiny crown.",
                        characterSpeechBubble = "I'm really flying! Follow my rainbow tail! 🦅🌋",
                        soundEffectOnomatopoeia = "WHOOOOSH! 🌟",
                        biome = WorldBiome.VOLCANO_ISLAND,
                        motionStyle = MotionStyle.FLY_LOOP,
                        particleMagic = ParticleMagicType.RAINBOW_TRAIL,
                        speedMultiplier = 1.25f,
                        jumpHeightRatio = 0.30f,
                        squashStretchIntensity = 0.18f,
                        skyTopColorHex = "#FB7185",
                        skyBottomColorHex = "#FDE047",
                        groundColorHex = "#34D399",
                        accentColorHex = "#F97316",
                        companions = listOf(
                            CompanionProp("Mango Bird", CompanionType.BABY_BIRD, "#FB923C", 1.3f),
                            CompanionProp("Kiwi Bird", CompanionType.BABY_BIRD, "#A3E635", 1.0f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 3,
                        sceneTitle = "Scene 3: The Sugar Cloud Kingdom",
                        narrationText = "High above the rainbow, Barnaby landed on a bouncy cotton-candy cloud castle! Floating balloons tickled his nose as he did a super-hero zoom between peppermint towers.",
                        characterSpeechBubble = "Cotton candy clouds taste like strawberries! 🍭👑",
                        soundEffectOnomatopoeia = "ZOOM-POP! 🎈",
                        biome = WorldBiome.CANDY_KINGDOM,
                        motionStyle = MotionStyle.SUPER_HERO_ZOOM,
                        particleMagic = ParticleMagicType.HEARTS,
                        speedMultiplier = 1.30f,
                        jumpHeightRatio = 0.22f,
                        squashStretchIntensity = 0.22f,
                        skyTopColorHex = "#C084FC",
                        skyBottomColorHex = "#FBCFE8",
                        groundColorHex = "#F472B6",
                        accentColorHex = "#38BDF8",
                        companions = listOf(
                            CompanionProp("Berry Balloon", CompanionType.BALLOON, "#EC4899", 0.8f),
                            CompanionProp("Mint Star", CompanionType.STAR_SPRITE, "#2DD4BF", 1.4f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 4,
                        sceneTitle = "Scene 4: The Starlight Dino Boogie",
                        narrationText = "As the stars twinkled on, all the forest friends gathered for Barnaby's Victory Dance! Confetti rained from the sky while Barnaby spun and shared glowing cloudberries with everyone.",
                        characterSpeechBubble = "Best adventure ever! Let's dance together! 🎉💃",
                        soundEffectOnomatopoeia = "TA-DAAA! 🎊",
                        biome = WorldBiome.ENCHANTED_FOREST,
                        motionStyle = MotionStyle.DANCE_SPIN,
                        particleMagic = ParticleMagicType.CONFETTI,
                        speedMultiplier = 1.15f,
                        jumpHeightRatio = 0.26f,
                        squashStretchIntensity = 0.28f,
                        skyTopColorHex = "#1E1B4B",
                        skyBottomColorHex = "#4338CA",
                        groundColorHex = "#10B981",
                        accentColorHex = "#FBBF24",
                        companions = listOf(
                            CompanionProp("Glow Firefly", CompanionType.STAR_SPRITE, "#FDE047", 1.5f),
                            CompanionProp("Luna Butterfly", CompanionType.BUTTERFLY, "#60A5FA", 1.1f)
                        )
                    )
                )
            )

            KidDrawingPreset.ASTRO_CAT_ROCKET -> StoryAdventure(
                storyTitle = "Captain Whiskers and the Milk-Way Galaxy",
                characterName = "Captain Whiskers",
                characterSpeciesOrType = "Space-Explorer Tabby Cat in a Rainbow Rocket",
                detectedDrawingSummary = "An adventurous orange tabby cat peeking out of a porthole inside a $dominantColors rocket ship with blazing booster flames!",
                personalityTrait = "Curious, purr-fectly fearless, and loves chasing shooting stars",
                superpower = "Supersonic Purr-Booster Drive",
                moralOrSparkleMessage = "Curiosity is the fuel that takes your imagination past the farthest star!",
                scenes = listOf(
                    StoryScene(
                        sceneNumber = 1,
                        sceneTitle = "Scene 1: Countdown at Rainbow Launchpad",
                        narrationText = "Three... Two... One... MEOW-OFF! Captain Whiskers pressed the big red paw-button, and his crayon rocket rumbled into the sunny sky trailing shimmering golden sparks!",
                        characterSpeechBubble = "Hold onto your whiskers! Next stop: Space! 🚀🐱",
                        soundEffectOnomatopoeia = "BLAST-OFF! 🔥",
                        biome = WorldBiome.SUNNY_MEADOW,
                        motionStyle = MotionStyle.SUPER_HERO_ZOOM,
                        particleMagic = ParticleMagicType.SPARKLES,
                        speedMultiplier = 1.3f,
                        jumpHeightRatio = 0.28f,
                        squashStretchIntensity = 0.20f,
                        skyTopColorHex = "#0284C7",
                        skyBottomColorHex = "#BAE6FD",
                        groundColorHex = "#22C55E",
                        accentColorHex = "#EF4444",
                        companions = listOf(
                            CompanionProp("Booster Bird", CompanionType.BABY_BIRD, "#FACC15", 1.2f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 2,
                        sceneTitle = "Scene 2: Zero-Gravity Yarn Nebula",
                        narrationText = "Up in the Starlight Galaxy, gravity turned off! Captain Whiskers floated Past giant glowing planets shaped like yarn balls while friendly green UFOs blinked hello.",
                        characterSpeechBubble = "Whoa! Everything floats in the Milk-Way! 🪐✨",
                        soundEffectOnomatopoeia = "BEEP-PURRRR! 🛸",
                        biome = WorldBiome.STARRY_SPACE,
                        motionStyle = MotionStyle.FLOAT_SPACE,
                        particleMagic = ParticleMagicType.METEORS,
                        speedMultiplier = 0.95f,
                        jumpHeightRatio = 0.20f,
                        squashStretchIntensity = 0.15f,
                        skyTopColorHex = "#090D16",
                        skyBottomColorHex = "#1E1B4B",
                        groundColorHex = "#4C1D95",
                        accentColorHex = "#38BDF8",
                        companions = listOf(
                            CompanionProp("Blinky UFO", CompanionType.FRIENDLY_UFO, "#4ADE80", 1.1f),
                            CompanionProp("Comet Sprite", CompanionType.STAR_SPRITE, "#F472B6", 1.4f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 3,
                        sceneTitle = "Scene 3: Looping Around the Cheese Moon",
                        narrationText = "Captain Whiskers steered his rocket in a giant figure-eight around the Golden Cheese Moon, racing a shower of rainbow meteors that tickled his rocket fins!",
                        characterSpeechBubble = "Wheee! Let's do a cosmic loop-de-loop! 🌈🌙",
                        soundEffectOnomatopoeia = "ZOOOOOM! ☄️",
                        biome = WorldBiome.STARRY_SPACE,
                        motionStyle = MotionStyle.FLY_LOOP,
                        particleMagic = ParticleMagicType.RAINBOW_TRAIL,
                        speedMultiplier = 1.35f,
                        jumpHeightRatio = 0.32f,
                        squashStretchIntensity = 0.22f,
                        skyTopColorHex = "#0F172A",
                        skyBottomColorHex = "#312E81",
                        groundColorHex = "#F59E0B",
                        accentColorHex = "#F43F5E",
                        companions = listOf(
                            CompanionProp("Zappy UFO", CompanionType.FRIENDLY_UFO, "#38BDF8", 1.3f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 4,
                        sceneTitle = "Scene 4: Cosmic Disco Party",
                        narrationText = "All the aliens and star-sprites threw a galactic celebration for Captain Whiskers! His rocket bounced and spun to the space-beat while confetti floated across the cosmos.",
                        characterSpeechBubble = "Purr-fect landing! Time for the Astro-Dance! 🎉🐾",
                        soundEffectOnomatopoeia = "MEOW-YAY! 🎊",
                        biome = WorldBiome.CANDY_KINGDOM,
                        motionStyle = MotionStyle.DANCE_SPIN,
                        particleMagic = ParticleMagicType.CONFETTI,
                        speedMultiplier = 1.2f,
                        jumpHeightRatio = 0.25f,
                        squashStretchIntensity = 0.26f,
                        skyTopColorHex = "#3B0764",
                        skyBottomColorHex = "#831843",
                        groundColorHex = "#EC4899",
                        accentColorHex = "#FACC15",
                        companions = listOf(
                            CompanionProp("Disco UFO", CompanionType.FRIENDLY_UFO, "#FACC15", 1.4f),
                            CompanionProp("Star Buddy", CompanionType.STAR_SPRITE, "#A855F7", 1.2f)
                        )
                    )
                )
            )

            KidDrawingPreset.CORAL_OCTOPUS -> StoryAdventure(
                storyTitle = "Princess Pippa's Musical Bubble Reef",
                characterName = "Princess Pippa",
                characterSpeciesOrType = "Royal Eight-Armed Coral Octopus",
                detectedDrawingSummary = "A cheerful pink octopus with purple curls and a golden sea-crown drawn in $dominantColors!",
                personalityTrait = "Musical, kind-hearted, and gives the best 8-arm hugs",
                superpower = "Magical Musical Shimmer-Bubbles",
                moralOrSparkleMessage = "When you share your song with others, the whole ocean shines brighter!",
                scenes = listOf(
                    StoryScene(
                        sceneNumber = 1,
                        sceneTitle = "Scene 1: Morning in the Coral Kingdom",
                        narrationText = "Deep beneath the turquoise waves, Princess Pippa adjusted her golden tiara and waved all eight curly arms. Every wiggle sent glowing shimmer-bubbles floating toward the sunlit water!",
                        characterSpeechBubble = "Good morning, ocean friends! Let's make bubbles! 🐙🫧",
                        soundEffectOnomatopoeia = "BLUB-POP! 🫧",
                        biome = WorldBiome.OCEAN_REEF,
                        motionStyle = MotionStyle.SWIM_WAVE,
                        particleMagic = ParticleMagicType.BUBBLES,
                        speedMultiplier = 1.0f,
                        jumpHeightRatio = 0.22f,
                        squashStretchIntensity = 0.24f,
                        skyTopColorHex = "#0284C7",
                        skyBottomColorHex = "#0D9488",
                        groundColorHex = "#F472B6",
                        accentColorHex = "#FACC15",
                        companions = listOf(
                            CompanionProp("Jelly-Belle", CompanionType.JELLYFISH, "#C084FC", 1.0f),
                            CompanionProp("Coral Sprite", CompanionType.STAR_SPRITE, "#38BDF8", 1.2f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 2,
                        sceneTitle = "Scene 2: The Great Dolphin Leap",
                        narrationText = "Pippa swam faster and faster until—SPLASH!—she leaped right above the ocean waves into the golden sunset, doing a joyful flip alongside her jellyfish friends!",
                        characterSpeechBubble = "Wheee! Look how high an octopus can jump! 🌈🌊",
                        soundEffectOnomatopoeia = "SPLASH-WHEEE! ✨",
                        biome = WorldBiome.VOLCANO_ISLAND,
                        motionStyle = MotionStyle.BOUNCE_ADVENTURE,
                        particleMagic = ParticleMagicType.SPARKLES,
                        speedMultiplier = 1.2f,
                        jumpHeightRatio = 0.28f,
                        squashStretchIntensity = 0.26f,
                        skyTopColorHex = "#38BDF8",
                        skyBottomColorHex = "#FDE047",
                        groundColorHex = "#06B6D4",
                        accentColorHex = "#F43F5E",
                        companions = listOf(
                            CompanionProp("Breezy Bird", CompanionType.BABY_BIRD, "#FBBF24", 1.3f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 3,
                        sceneTitle = "Scene 3: The Enchanted Sea-Lantern Cave",
                        narrationText = "Inside the Whispering Pearl Cavern, Pippa conducted an underwater orchestra! Each tentacle tapped a glowing crystal shell, filling the reef with rainbow heart notes.",
                        characterSpeechBubble = "Sing with me, little jellyfish! La-la-splish! 💖🎶",
                        soundEffectOnomatopoeia = "TING-CHIME! 💖",
                        biome = WorldBiome.ENCHANTED_FOREST,
                        motionStyle = MotionStyle.FLOAT_SPACE,
                        particleMagic = ParticleMagicType.HEARTS,
                        speedMultiplier = 0.95f,
                        jumpHeightRatio = 0.20f,
                        squashStretchIntensity = 0.20f,
                        skyTopColorHex = "#1E1B4B",
                        skyBottomColorHex = "#0F766E",
                        groundColorHex = "#14B8A6",
                        accentColorHex = "#EC4899",
                        companions = listOf(
                            CompanionProp("Glow Jelly", CompanionType.JELLYFISH, "#F472B6", 1.1f),
                            CompanionProp("Pearl Sprite", CompanionType.STAR_SPRITE, "#FEF08A", 1.3f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 4,
                        sceneTitle = "Scene 4: The Royal Reef Fiesta",
                        narrationText = "The whole Coral Kingdom joined Princess Pippa for a swirling, twirling Undersea Ball! Confetti and pearl bubbles danced around her tiara as everyone shared an eight-arm group hug.",
                        characterSpeechBubble = "Eight arms means eight times the hugs for you! 🎉👑",
                        soundEffectOnomatopoeia = "SPLISH-HOORAY! 🎊",
                        biome = WorldBiome.OCEAN_REEF,
                        motionStyle = MotionStyle.DANCE_SPIN,
                        particleMagic = ParticleMagicType.CONFETTI,
                        speedMultiplier = 1.25f,
                        jumpHeightRatio = 0.26f,
                        squashStretchIntensity = 0.28f,
                        skyTopColorHex = "#0369A1",
                        skyBottomColorHex = "#1D4ED8",
                        groundColorHex = "#FB7185",
                        accentColorHex = "#FDE047",
                        companions = listOf(
                            CompanionProp("Sunny Jelly", CompanionType.JELLYFISH, "#FACC15", 1.4f),
                            CompanionProp("Aqua Jelly", CompanionType.JELLYFISH, "#2DD4BF", 1.1f)
                        )
                    )
                )
            )

            KidDrawingPreset.DISCO_ROBOT -> StoryAdventure(
                storyTitle = "Sparky the Boogie-Bot's Rainbow Recharge",
                characterName = "Sparky the Boogie-Bot",
                characterSpeciesOrType = "Spring-Legged Musical Robot with a Heart Core",
                detectedDrawingSummary = "A friendly turquoise and sunshine-yellow robot in $dominantColors with springy legs, a glowing red heart badge, and a star antenna!",
                personalityTrait = "Energetic, helpful, and powered by giggles and music",
                superpower = "Turbo-Spring Rainbow Boogie Beam",
                moralOrSparkleMessage = "The most powerful battery in the universe is a kind and happy heart!",
                scenes = listOf(
                    StoryScene(
                        sceneNumber = 1,
                        sceneTitle = "Scene 1: Bouncing Out of the Invention Lab",
                        narrationText = "BEEP-BOOP-BOING! Sparky's heart badge lit up bright pink as his springy legs launched him across the sunny meadow! Every hop made musical chimes ring from his star antenna.",
                        characterSpeechBubble = "BEEP-BOOP! My springs are 100% charged for fun! 🤖⚡",
                        soundEffectOnomatopoeia = "SPROING-BEEP! ⚡",
                        biome = WorldBiome.SUNNY_MEADOW,
                        motionStyle = MotionStyle.BOUNCE_ADVENTURE,
                        particleMagic = ParticleMagicType.SPARKLES,
                        speedMultiplier = 1.15f,
                        jumpHeightRatio = 0.28f,
                        squashStretchIntensity = 0.28f,
                        skyTopColorHex = "#38BDF8",
                        skyBottomColorHex = "#BAE6FD",
                        groundColorHex = "#4ADE80",
                        accentColorHex = "#F43F5E",
                        companions = listOf(
                            CompanionProp("Spark Sprite", CompanionType.STAR_SPRITE, "#FACC15", 1.3f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 2,
                        sceneTitle = "Scene 2: Rocket-Boot Flight Over Candy Towers",
                        narrationText = "Sparky clicked his red heels together and—ZAP!—his spring boots transformed into rainbow jet-thrusters! He soared over the Sugar Cloud Kingdom, high-fiving floating balloons.",
                        characterSpeechBubble = "activating Rainbow Jet Mode! Zoom-zoom! 🌈🚀",
                        soundEffectOnomatopoeia = "WHIRRR-ZAP! 🌈",
                        biome = WorldBiome.CANDY_KINGDOM,
                        motionStyle = MotionStyle.FLY_LOOP,
                        particleMagic = ParticleMagicType.RAINBOW_TRAIL,
                        speedMultiplier = 1.3f,
                        jumpHeightRatio = 0.30f,
                        squashStretchIntensity = 0.20f,
                        skyTopColorHex = "#A855F7",
                        skyBottomColorHex = "#FBCFE8",
                        groundColorHex = "#F472B6",
                        accentColorHex = "#38BDF8",
                        companions = listOf(
                            CompanionProp("Pop Balloon", CompanionType.BALLOON, "#38BDF8", 1.0f),
                            CompanionProp("Fizz Balloon", CompanionType.BALLOON, "#FACC15", 1.2f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 3,
                        sceneTitle = "Scene 3: Lighting Up Firefly Woods",
                        narrationText = "When the Enchanted Forest grew sleepy and dark, Sparky beamed a warm golden heart-light from his chest! Hundreds of fireflies woke up and danced in glowing circles around him.",
                        characterSpeechBubble = "Don't worry, forest friends! My heart-light is on! 💖🌟",
                        soundEffectOnomatopoeia = "GLOW-CHIME! ✨",
                        biome = WorldBiome.ENCHANTED_FOREST,
                        motionStyle = MotionStyle.SUPER_HERO_ZOOM,
                        particleMagic = ParticleMagicType.FIREFLIES,
                        speedMultiplier = 1.1f,
                        jumpHeightRatio = 0.22f,
                        squashStretchIntensity = 0.22f,
                        skyTopColorHex = "#0F172A",
                        skyBottomColorHex = "#1E3A8A",
                        groundColorHex = "#059669",
                        accentColorHex = "#FDE047",
                        companions = listOf(
                            CompanionProp("Flicker Bug", CompanionType.BUTTERFLY, "#FDE047", 1.4f),
                            CompanionProp("Beamy Sprite", CompanionType.STAR_SPRITE, "#F472B6", 1.1f)
                        )
                    ),
                    StoryScene(
                        sceneNumber = 4,
                        sceneTitle = "Scene 4: The Grand Galaxy Boogie Showdown",
                        narrationText = "Up on the Starlight Stage, Sparky spun 360 degrees in his famous Robo-Boogie! Friendly UFOs flashed disco lights while confetti showered down on the happiest robot in the universe.",
                        characterSpeechBubble = "Everybody do the Robo-Spin! Beep-boop-hooray! 🎉🤖",
                        soundEffectOnomatopoeia = "DISCO-BOING! 🎊",
                        biome = WorldBiome.STARRY_SPACE,
                        motionStyle = MotionStyle.DANCE_SPIN,
                        particleMagic = ParticleMagicType.CONFETTI,
                        speedMultiplier = 1.3f,
                        jumpHeightRatio = 0.27f,
                        squashStretchIntensity = 0.30f,
                        skyTopColorHex = "#090D16",
                        skyBottomColorHex = "#311042",
                        groundColorHex = "#6366F1",
                        accentColorHex = "#2DD4BF",
                        companions = listOf(
                            CompanionProp("Beat UFO", CompanionType.FRIENDLY_UFO, "#2DD4BF", 1.5f),
                            CompanionProp("Groove Star", CompanionType.STAR_SPRITE, "#F43F5E", 1.2f)
                        )
                    )
                )
            )
        }
    }

    /**
     * Transforms a child's raw paper cutout into a lush, 3D-shaded Storybook Character Sprite!
     * - Fills rough crayon scribble gaps via morphological closing
     * - Applies 3D spherical Pixar/storybook lighting (top-left warm sunlight highlight + bottom ambient shadow + rim glow)
     * - Enhances color vibrancy and adds a golden magical emblem so the character in the scene looks truly transformed
     *   compared to the raw paper scan!
     */
    fun synthesizeLivingStorybookCharacter(
        extractedCutout: RgbaBuffer
    ): RgbaBuffer {
        val w = extractedCutout.width
        val h = extractedCutout.height
        val out = RgbaBuffer(w, h)

        // 1. Identify non-white character pixels vs white sticker border vs transparent background
        val isCharBody = BooleanArray(w * h)
        val bodyColors = IntArray(w * h)
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = extractedCutout.pixels[i]
                val a = (p ushr 24) and 0xFF
                if (a > 128) {
                    val r = (p ushr 16) and 0xFF
                    val g = (p ushr 8) and 0xFF
                    val b = p and 0xFF
                    val isStickerWhite = (r > 248 && g > 248 && b > 248)
                    if (!isStickerWhite) {
                        isCharBody[i] = true
                        bodyColors[i] = p
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
        }

        if (minX >= maxX || minY >= maxY) {
            return extractedCutout.copy()
        }

        // 2. Fill tiny 1-2px wax crayon gaps inside the character using neighbor color interpolation
        // so the character surface looks like a solid, lush painted storybook illustration!
        val filledBody = isCharBody.copyOf()
        val filledColors = bodyColors.copyOf()
        for (pass in 0 until 2) {
            for (y in (minY + 1) until maxY) {
                for (x in (minX + 1) until maxX) {
                    val i = y * w + x
                    if (!filledBody[i]) {
                        var neighborCount = 0
                        var sumR = 0
                        var sumG = 0
                        var sumB = 0
                        for (dy in -2..2) {
                            for (dx in -2..2) {
                                val nx = x + dx
                                val ny = y + dy
                                if (nx in 0 until w && ny in 0 until h) {
                                    val ni = ny * w + nx
                                    if (filledBody[ni]) {
                                        val np = filledColors[ni]
                                        sumR += (np ushr 16) and 0xFF
                                        sumG += (np ushr 8) and 0xFF
                                        sumB += np and 0xFF
                                        neighborCount++
                                    }
                                }
                            }
                        }
                        if (neighborCount >= 12) {
                            filledBody[i] = true
                            filledColors[i] = argb(255, sumR / neighborCount, sumG / neighborCount, sumB / neighborCount)
                        }
                    }
                }
            }
        }

        // 3. Apply 3x3 bilateral color smoothing + 3D Spherical Storybook Shading & Rim Glow
        val centerX = (minX + maxX) * 0.5f
        val centerY = (minY + maxY) * 0.5f
        val radiusX = ((maxX - minX) * 0.5f).coerceAtLeast(1f)
        val radiusY = ((maxY - minY) * 0.5f).coerceAtLeast(1f)

        // Draw golden-cyan magical sticker glow around the character
        val borderRadius = 5
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                if (filledBody[y * w + x]) {
                    for (dy in -borderRadius..borderRadius) {
                        for (dx in -borderRadius..borderRadius) {
                            val d2 = dx * dx + dy * dy
                            if (d2 <= borderRadius * borderRadius) {
                                val ox = x + dx
                                val oy = y + dy
                                if (ox in 0 until w && oy in 0 until h && !filledBody[oy * w + ox]) {
                                    val edgeColor = if (d2 <= 6) argb(255, 255, 252, 235) else argb(210, 253, 224, 71)
                                    out.setPixel(ox, oy, edgeColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shade interior character pixels with 3D volumetric lighting (top-left light source)
        val lightDirX = -0.45f
        val lightDirY = -0.55f
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val i = y * w + x
                if (!filledBody[i]) continue

                // Smooth color with 3x3 similar neighbors
                val baseP = filledColors[i]
                val br = (baseP ushr 16) and 0xFF
                val bg = (baseP ushr 8) and 0xFF
                val bb = baseP and 0xFF

                var sR = br * 2
                var sG = bg * 2
                var sB = bb * 2
                var wSum = 2
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx in 0 until w && ny in 0 until h && filledBody[ny * w + nx]) {
                            val np = filledColors[ny * w + nx]
                            val nr = (np ushr 16) and 0xFF
                            val ng = (np ushr 8) and 0xFF
                            val nb = np and 0xFF
                            if (abs(nr - br) + abs(ng - bg) + abs(nb - bb) < 95) {
                                sR += nr
                                sG += ng
                                sB += nb
                                wSum++
                            }
                        }
                    }
                }
                var r = sR / wSum
                var g = sG / wSum
                var b = sB / wSum

                // Boost saturation so storybook character pops vividly
                val avg = (r + g + b) / 3
                r = (avg + ((r - avg) * 1.32f).toInt()).coerceIn(0, 255)
                g = (avg + ((g - avg) * 1.32f).toInt()).coerceIn(0, 255)
                b = (avg + ((b - avg) * 1.32f).toInt()).coerceIn(0, 255)

                // Compute normalized 3D normal on ellipsoid for volumetric storybook shading
                val nx = (x - centerX) / radiusX
                val ny = (y - centerY) / radiusY
                val dotLight = (nx * lightDirX + ny * lightDirY).coerceIn(-1f, 1f)

                // Keep dark ink outlines dark and crisp; apply 3D highlight/shadow to colored surfaces
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                if (lum > 55) {
                    val shadeFactor = 1.04f + dotLight * 0.24f
                    val specular = if (dotLight > 0.35f) ((dotLight - 0.35f) * 48f).toInt() else 0
                    r = (r * shadeFactor + specular).toInt().coerceIn(0, 255)
                    g = (g * shadeFactor + specular).toInt().coerceIn(0, 255)
                    b = (b * shadeFactor + specular * 0.8f).toInt().coerceIn(0, 255)
                }

                out.setPixel(x, y, argb(255, r, g, b))
            }
        }

        return out
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    private fun jitterColor(argb: Int, delta: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (((argb ushr 16) and 0xFF) + delta).coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) + delta).coerceIn(0, 255)
        val b = ((argb and 0xFF) + delta).coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
