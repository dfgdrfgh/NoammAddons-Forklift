package com.github.noamm9.utils.render.world

import com.github.noamm9.NoammAddons.mc
import com.github.noamm9.utils.render.world.batches.*
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import gg.essential.universal.*
import gg.essential.universal.render.URenderPipeline
import gg.essential.universal.vertex.*
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.client.renderer.feature.FeatureFrameContext
import net.minecraft.client.renderer.feature.TextFeatureRenderer
import net.minecraft.network.chat.Component
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import org.joml.Vector3f

object RenderBatcher {
    private val filledBatches = mutableMapOf<URenderPipeline, FilledBatch>()
    private val lineBatches = mutableMapOf<URenderPipeline, LineBatch>()
    private val texts = ArrayList<TextRenderState>()

    private val textRenderer = TextFeatureRenderer()
    private val textVertexBuffer = StagedVertexBuffer({ "NoammAddons Text" }, 1024 * 1024)

    val tmpVec = Vector3f()
    val tmpDir = Vector3f()

    fun filledBatch(phase: Boolean) = filledBatch(if (phase) NoammRenderPipelines.FILLED_THROUGH_WALLS else NoammRenderPipelines.FILLED, UGraphics.DrawMode.TRIANGLES)
    fun circleBatch(phase: Boolean) = filledBatch(if (phase) NoammRenderPipelines.CIRCLE_FILLED_THROUGH_WALLS else NoammRenderPipelines.CIRCLE_FILLED, UGraphics.DrawMode.TRIANGLE_STRIP)
    fun lineBatch(phase: Boolean): LineBatch {
        val pipeline = if (phase) NoammRenderPipelines.LINES_THROUGH_WALLS else NoammRenderPipelines.LINES
        return lineBatches.getOrPut(pipeline) { LineBatch(pipeline) }
    }

    internal fun addText(matrix: Matrix4f, text: String, xOff: Float, yOff: Float, argb: Int, seeThrough: Boolean) {
        texts.add(TextRenderState(Matrix4f(matrix), text, xOff, yOff, argb, seeThrough))
    }

    /**
     * Minecraft 26.2 renders normal submitText nodes before custom geometry.
     * NoammAddons draws its world overlays later, so submitting text through the
     * normal collector causes labels to be covered by our own ESP/highlight geometry.
     *
     * Render the queued text at END_MAIN instead. NORMAL still respects world depth;
     * SEE_THROUGH still ignores it, but both are now drawn after NoammAddons geometry.
     */
    internal fun flushTexts() {
        if (texts.isEmpty()) return

        val gameRenderer = mc.gameRenderer
        val frameContext = FeatureFrameContext(
            gameRenderer.gameRenderState().optionsRenderState,
            mc.font,
            mc.modelManager.blockStateModelSet,
            mc.blockColors,
            mc.textureManager,
            mc.atlasManager,
            gameRenderer.levelLightmap(),
            textVertexBuffer
        )

        val submits = texts.map { text ->
            TextFeatureRenderer.Submit(
                text.matrix,
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

        try {
            textRenderer.prepareGroup(frameContext, submits, false)
            textVertexBuffer.upload()
            textRenderer.executeGroup(frameContext, 0, submits, false)
        } finally {
            textRenderer.finishExecute(frameContext)
            textVertexBuffer.endFrame()
            texts.clear()
        }
    }

    internal fun flush() {
        if (filledBatches.isEmpty() && lineBatches.isEmpty()) return

        for (batchData in filledBatches.values) {
            val builder = UBufferBuilder.create(batchData.mode, UGraphics.CommonVertexFormats.POSITION_COLOR)

            for (state in batchData.data) {
                builder.pos(UMatrixStack.UNIT, state.x, state.y, state.z)
                builder.color(state.r, state.g, state.b, state.a)
                builder.endVertex()
            }

            builder.build()?.drawAndClose(batchData.pipeline) { noScissor() }
        }

        for (batchData in lineBatches.values) {
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

        filledBatches.clear()
        lineBatches.clear()
    }

    private fun filledBatch(pipeline: URenderPipeline, mode: UGraphics.DrawMode) = filledBatches.getOrPut(pipeline) { FilledBatch(pipeline, mode) }
}
