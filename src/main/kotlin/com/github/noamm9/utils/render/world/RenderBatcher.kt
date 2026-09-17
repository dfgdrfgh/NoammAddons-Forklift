package com.github.noamm9.utils.render.world

import com.github.noamm9.utils.render.world.batches.*
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import gg.essential.universal.*
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.*
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import org.joml.Vector3f

object RenderBatcher {
    private val filledBatches = mutableMapOf<URenderPipeline, FilledBatch>()
    private val lineBatches = mutableMapOf<URenderPipeline, LineBatch>()
    private val earlyFilledBatches = mutableMapOf<URenderPipeline, FilledBatch>()
    private val earlyLineBatches = mutableMapOf<URenderPipeline, LineBatch>()
    private val texts = ArrayList<TextRenderState>()

    val tmpVec = Vector3f()
    val tmpDir = Vector3f()

    fun filledBatch(phase: Boolean, early: Boolean = false): FilledBatch {
        val pipeline = if (phase) NoammRenderPipelines.FILLED_THROUGH_WALLS else NoammRenderPipelines.FILLED
        val batches = if (early) earlyFilledBatches else filledBatches
        return filledBatch(pipeline, UGraphics.DrawMode.TRIANGLES, batches)
    }

    fun circleBatch(phase: Boolean) = filledBatch(
        if (phase) NoammRenderPipelines.CIRCLE_FILLED_THROUGH_WALLS else NoammRenderPipelines.CIRCLE_FILLED,
        UGraphics.DrawMode.TRIANGLE_STRIP,
        filledBatches
    )

    fun lineBatch(phase: Boolean, early: Boolean = false): LineBatch {
        val pipeline = if (phase) NoammRenderPipelines.LINES_THROUGH_WALLS else NoammRenderPipelines.LINES
        val batches = if (early) earlyLineBatches else lineBatches
        return batches.getOrPut(pipeline) { LineBatch(pipeline) }
    }

    internal fun addText(matrix: Matrix4f, text: String, xOff: Float, yOff: Float, argb: Int, seeThrough: Boolean) {
        texts.add(TextRenderState(Matrix4f(matrix), text, xOff, yOff, argb, seeThrough))
    }

    internal fun submitTexts(context: LevelRenderContext) {
        if (texts.isEmpty()) return

        for (text in texts) {
            val poseStack = PoseStack()
            poseStack.last().pose().set(text.matrix)
            context.submitNodeCollector().submitText(
                poseStack,
                text.xOff,
                text.yOff,
                Component.literal(text.text).visualOrderText,
                true,
                if (text.seeThrough) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL,
                LightCoordsUtil.FULL_BRIGHT,
                text.argb,
                0,
                0
            )
        }

        texts.clear()
    }

    internal fun flushEarly() {
        flushGeometry(earlyFilledBatches, earlyLineBatches)
    }

    internal fun flush() {
        flushGeometry(filledBatches, lineBatches)
    }

    private fun flushGeometry(
        fills: MutableMap<URenderPipeline, FilledBatch>,
        lines: MutableMap<URenderPipeline, LineBatch>
    ) {
        if (fills.isEmpty() && lines.isEmpty()) return

        for (batchData in fills.values) {
            val builder = UBufferBuilder.create(batchData.mode, UGraphics.CommonVertexFormats.POSITION_COLOR)

            for (state in batchData.data) {
                builder.pos(UMatrixStack.UNIT, state.x, state.y, state.z)
                builder.color(state.r, state.g, state.b, state.a)
                builder.endVertex()
            }

            builder.build()?.drawAndClose(batchData.pipeline) { noScissor() }
        }

        for (batchData in lines.values) {
            val format = DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
            val backing = ByteBufferBuilder(maxOf(256, batchData.data.size * format.vertexSize))
            try {
                val mcBuffer = BufferBuilder(backing, PrimitiveTopology.LINES, format)

                for (state in batchData.data) {
                    mcBuffer.addVertex(state.x.toFloat(), state.y.toFloat(), state.z.toFloat())
                        .setColor(state.r, state.g, state.b, state.a)
                        .setNormal(state.nx, state.ny, state.nz)
                        .setLineWidth(state.lineWidth)
                }

                mcBuffer.build()?.let(UBuiltBuffer::wrap)?.drawAndClose(batchData.pipeline) { noScissor() }
            } finally {
                backing.close()
            }
        }

        fills.clear()
        lines.clear()
    }

    private fun filledBatch(
        pipeline: URenderPipeline,
        mode: UGraphics.DrawMode,
        batches: MutableMap<URenderPipeline, FilledBatch>
    ) = batches.getOrPut(pipeline) { FilledBatch(pipeline, mode) }
}
