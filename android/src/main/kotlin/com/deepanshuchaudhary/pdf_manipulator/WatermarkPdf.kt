package com.deepanshuchaudhary.pdf_manipulator

import android.app.Activity
import android.content.ContentResolver
import android.graphics.Color
import android.util.Log
import androidx.core.net.toUri
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.VerticalAlignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


enum class WatermarkLayer {
    UnderContent, OverContent
}

enum class PositionType {
    TopLeft, TopCenter, TopRight, CenterLeft, Center, CenterRight, BottomLeft, BottomCenter, BottomRight, Custom
}
// For compressing pdf.
suspend fun getWatermarkedPDFPath(
    sourceFilePath: String,
    text: String,
    fontSize: Double,
    watermarkLayer: WatermarkLayer,
    opacity: Double,
    rotationAngle: Double,
    watermarkColor: String,
    positionType: PositionType,
    customPositionXCoordinatesList: List<Double>,
    customPositionYCoordinatesList: List<Double>,
    context: Activity,
): String? {

    val resultPDFPath: String? // 声明 resultPDFPath 为可变变量

    withContext(Dispatchers.IO) {
        val utils = Utils()

        val begin = System.nanoTime()

        val contentResolver: ContentResolver = context.contentResolver

        val uri = Utils().getURI(sourceFilePath)

        val pdfReaderFile: File = File.createTempFile("readerTempFile", ".pdf")
        utils.copyDataFromSourceToDestDocument(
            sourceFileUri = uri,
            destinationFileUri = pdfReaderFile.toUri(),
            contentResolver = contentResolver
        )

        val pdfReader = PdfReader(pdfReaderFile).setUnethicalReading(true)
        pdfReader.setMemorySavingMode(true)

        val pdfWriterFile: File = File.createTempFile("writerTempFile", ".pdf")

        val pdfWriter = PdfWriter(pdfWriterFile)

        pdfWriter.setSmartMode(true)
        pdfWriter.compressionLevel = 9

        val pdfDocument = PdfDocument(pdfReader, pdfWriter)

        // watermark 函数被移动到这里，作为 getWatermarkedPDFPath 的局部函数
        // 这样它可以访问 getWatermarkedPDFPath 的参数和局部变量
        // 并且不需要显式地将其参数传递给它，因为它会捕获外部作用域的变量
        // (text, fontSize, watermarkLayer, opacity, rotationAngle, watermarkColor, positionType,
        // customPositionXCoordinatesList, customPositionYCoordinatesList, pdfDocument, pdfReader, pdfWriter,
        // pdfReaderFile, utils, begin, pdfWriterFile)
        fun applyWatermark() { // 将函数名从 watermark() 改为 applyWatermark() 避免混淆

            val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
            val paragraph = Paragraph(text).setFont(font).setFontSize(fontSize.toFloat())

            val color = try {
                Color.parseColor(watermarkColor)
            } catch (e: Exception) {
                Log.e("Parse", "Error parsing watermarkColor $watermarkColor. $e")
                Color.BLACK
            }

            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)

            // 将这两个列表转换为Float一次，避免在循环中重复转换
            val xList = customPositionXCoordinatesList.map { it.toFloat() }
            val yList = customPositionYCoordinatesList.map { it.toFloat() }

           // Log.d("WatermarkDebug", "Kotlin (Float) X List: $xList")
           // Log.d("WatermarkDebug", "Kotlin (Float) Y List: $yList")

            // Implement transformation matrix usage in order to scale image
            for (i in 1..pdfDocument.numberOfPages) { // i 是当前页码 (从1开始)

                val pdfPage: PdfPage = pdfDocument.getPage(i)
                val pageSize: Rectangle = pdfPage.pageSizeWithRotation

                // When "true": in case the page has a rotation, then new content will be automatically rotated in the
                // opposite direction. On the rotated page this would look as if new content ignores page rotation.
                pdfPage.isIgnorePageRotationForContent = true

                val layer = if (watermarkLayer == WatermarkLayer.UnderContent) {
                    PdfCanvas(
                        pdfPage.newContentStreamBefore(), PdfResources(), pdfDocument
                    )
                } else {
                    PdfCanvas(pdfPage)
                }

                layer.setFillColor(DeviceRgb(red, green, blue))
                layer.saveState()
                // Creating a dictionary that maps resource names to graphics state parameter dictionaries
                val gs1 = PdfExtGState()
                gs1.fillOpacity = opacity.toFloat()
                layer.setExtGState(gs1)

                // **重要的改动：将水印添加逻辑放在此处**
                if (positionType == PositionType.Custom) {
                    // 对于每一页，都根据customPositionXCoordinatesList和customPositionYCoordinatesList添加多个水印
                    for (x in xList) { // 遍历所有X坐标
                        for (y in yList) { // 遍历所有Y坐标
                            val canvasWatermark = Canvas(layer, pdfDocument.defaultPageSize).showTextAligned(
                                paragraph,
                                x, // 当前X
                                y, // 当前Y
                                i, // 当前页码
                                TextAlignment.CENTER,
                                VerticalAlignment.TOP,
                                rotationAngle.toFloat()
                            )
                            canvasWatermark.close()
                        }
                    }
                } else {
                    // 如果不是Custom类型，只添加一个水印
                    val x: Float
                    val y: Float

                    when (positionType) {
                        PositionType.TopLeft -> {
                            x = (0).toFloat()
                            y = pageSize.height
                        }
                        PositionType.TopCenter -> {
                            x = pageSize.width / 2
                            y = pageSize.height
                        }
                        PositionType.TopRight -> {
                            x = pageSize.width
                            y = pageSize.height
                        }
                        PositionType.CenterLeft -> {
                            x = (0).toFloat()
                            y = pageSize.height / 2
                        }
                        PositionType.Center -> {
                            x = pageSize.width / 2
                            y = pageSize.height / 2
                        }
                        PositionType.CenterRight -> {
                            x = pageSize.width
                            y = pageSize.height / 2
                        }
                        PositionType.BottomLeft -> {
                            x = (0).toFloat()
                            y = (0).toFloat()
                        }
                        PositionType.BottomCenter -> {
                            x = pageSize.width / 2
                            y = (0).toFloat()
                        }
                        PositionType.BottomRight -> {
                            x = pageSize.width
                            y = (0).toFloat()
                        }
                        else -> { // 如果positionType不是以上任何一种，例如默认或者其他情况
                            // 应该设定一个默认值，或者抛出异常。
                            // 假设你想默认居中
                            x = pageSize.width / 2
                            y = pageSize.height / 2
                        }
                    }

                    val canvasWatermark = Canvas(layer, pdfDocument.defaultPageSize).showTextAligned(
                        paragraph,
                        x,
                        y,
                        i, // 当前页码
                        TextAlignment.CENTER,
                        VerticalAlignment.TOP,
                        rotationAngle.toFloat()
                    )
                    canvasWatermark.close()
                }

                layer.restoreState() // 恢复图层状态，防止影响后续操作
                layer.release() // 释放layer资源，重要！
            }

            pdfDocument.close()
            pdfReader.close()
            pdfWriter.close()

            // 确保这些文件变量在函数作用域内是可访问的
            utils.deleteTempFiles(listOfTempFiles = listOf(pdfReaderFile))

            val end = System.nanoTime()
            println("Elapsed time in nanoseconds: ${end - begin}")
        }

        // 调用局部函数来执行水印逻辑
        applyWatermark()

        // 此时 resultPDFPath 已经赋值，可以返回
        resultPDFPath = pdfWriterFile.path // 确保这里获取的是最终的路径
    }
    return resultPDFPath
}