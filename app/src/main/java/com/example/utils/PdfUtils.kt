package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class PdfUtils(private val context: Context) {

    enum class CompressionQuality { HIGH, MEDIUM, LOW }

    // ===== Compress PDF =====
    suspend fun compressPdf(
        inputUri: Uri,
        quality: CompressionQuality = CompressionQuality.MEDIUM,
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: throw Exception("Cannot open file descriptor")
        val renderer = PdfRenderer(pfd)
        val doc = PdfDocument()

        val compressionScale = when(quality) {
            CompressionQuality.HIGH -> 0.9f
            CompressionQuality.MEDIUM -> 0.61f
            CompressionQuality.LOW -> 0.4f
        }

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val width = (page.width * compressionScale).toInt().coerceAtLeast(100)
            val height = (page.height * compressionScale).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
            val docPage = doc.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            doc.finishPage(docPage)
            bitmap.recycle()

            onProgress(((i + 1) * 100) / renderer.pageCount)
        }

        renderer.close()
        pfd.close()

        val outputDir = getOutputDir()
        val outputFile = File(outputDir, "compressed_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()

        Uri.fromFile(outputFile)
    }

    suspend fun compressPdf(
        inputUri: Uri,
        outputFile: File,
        quality: Int = 80 // 0-100 scale
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext false
            val renderer = PdfRenderer(pfd)
            val doc = PdfDocument()
            val scale = (quality / 100f).coerceIn(0.2f, 1f)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = (page.width * scale).toInt().coerceAtLeast(100)
                val height = (page.height * scale).toInt().coerceAtLeast(100)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
                val docPage = doc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                doc.finishPage(docPage)
                bitmap.recycle()
            }

            renderer.close()
            pfd.close()
            FileOutputStream(outputFile).use { doc.writeTo(it) }
            doc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ===== Convert PDF pages to images =====
    suspend fun pdfToImages(
        inputUri: Uri,
        outputDir: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 85,
        dpi: Int = 150
    ): List<File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext files
            val renderer = PdfRenderer(pfd)
            outputDir.mkdirs()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = dpi / 72f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val ext = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
                val outFile = File(outputDir, "page_${i + 1}.$ext")
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(format, quality, out)
                }
                bitmap.recycle()
                files.add(outFile)
            }

            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        files
    }

    suspend fun convertToImages(
        inputUri: Uri,
        quality: Int = 85,
        onProgress: (Int) -> Unit = {}
    ): List<Uri> = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: throw Exception("Cannot open file descriptor")
        val renderer = PdfRenderer(pfd)
        val imageUris = mutableListOf<Uri>()

        val outDir = File(getOutputDir(), "images_${System.currentTimeMillis()}")
        outDir.mkdirs()

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val file = File(outDir, "page_${i + 1}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            bitmap.recycle()
            imageUris.add(Uri.fromFile(file))

            onProgress(((i + 1) * 100) / renderer.pageCount)
        }

        renderer.close()
        pfd.close()
        imageUris
    }

    // ===== Convert images to PDF =====
    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = PdfDocument()
            imageUris.forEachIndexed { i, uri ->
                val stream = context.contentResolver.openInputStream(uri) ?: return@forEachIndexed
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                stream.close()

                if (bitmap == null) return@forEachIndexed

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, i + 1).create()
                val page = doc.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                doc.finishPage(page)
                bitmap.recycle()
            }
            FileOutputStream(outputFile).use { doc.writeTo(it) }
            doc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ===== Copy URI to local file =====
    suspend fun copyUriToFile(uri: Uri, destFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val input: InputStream = context.contentResolver.openInputStream(uri) ?: return@withContext false
            val output: OutputStream = FileOutputStream(destFile)
            input.copyTo(output)
            input.close()
            output.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ===== Split PDF =====
    suspend fun splitPdf(
        inputUri: Uri,
        outputDir: File,
        ranges: List<IntRange>
    ): List<File> = withContext(Dispatchers.IO) {
        val result = mutableListOf<File>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext result
            outputDir.mkdirs()

            ranges.forEachIndexed { idx, range ->
                val renderer = PdfRenderer(pfd)
                val doc = PdfDocument()
                var localPage = 1

                for (pageIdx in range) {
                    if (pageIdx >= renderer.pageCount) break
                    val page = renderer.openPage(pageIdx)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, localPage++).create()
                    val docPage = doc.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(docPage)
                    bitmap.recycle()
                }

                val outFile = File(outputDir, "split_${idx + 1}.pdf")
                FileOutputStream(outFile).use { doc.writeTo(it) }
                doc.close()
                renderer.close()
                result.add(outFile)
            }
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun splitPdf(
        inputUri: Uri,
        fromPage: Int,
        toPage: Int
    ): Uri = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: throw Exception("Cannot open file descriptor")
        val renderer = PdfRenderer(pfd)
        val doc = PdfDocument()

        val adjustedFrom = (fromPage).coerceIn(0, renderer.pageCount - 1)
        val adjustedTo = (toPage).coerceIn(adjustedFrom, renderer.pageCount - 1)

        var localPageNo = 1
        for (i in adjustedFrom..adjustedTo) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, localPageNo++).create()
            val docPage = doc.startPage(pageInfo)
            docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
            doc.finishPage(docPage)
            bitmap.recycle()
        }

        renderer.close()
        pfd.close()

        val outputDir = getOutputDir()
        val outputFile = File(outputDir, "split_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()

        Uri.fromFile(outputFile)
    }

    // ===== Merge PDFs =====
    suspend fun mergePdfs(
        inputUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = PdfDocument()
            var globalPage = 1

            inputUris.forEach { uri ->
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@forEach
                val renderer = PdfRenderer(pfd)

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, globalPage++).create()
                    val docPage = doc.startPage(pageInfo)
                    docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(docPage)
                    bitmap.recycle()
                }

                renderer.close()
                pfd.close()
            }

            FileOutputStream(outputFile).use { doc.writeTo(it) }
            doc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun mergePdfs(
        uris: List<Uri>,
        onProgress: (Int) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        var totalPagesToMerge = 0

        // Get total page count first
        uris.forEach { uri ->
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@forEach
                val r = PdfRenderer(pfd)
                totalPagesToMerge += r.pageCount
                r.close()
                pfd.close()
            } catch (e: Exception) {}
        }

        var globalPage = 1
        uris.forEachIndexed { fIdx, uri ->
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@forEachIndexed
            val renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, globalPage++).create()
                val docPage = doc.startPage(pageInfo)
                docPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                doc.finishPage(docPage)
                bitmap.recycle()

                onProgress((globalPage * 100) / totalPagesToMerge.coerceAtLeast(1))
            }
            renderer.close()
            pfd.close()
        }

        val outputFile = File(getOutputDir(), "merged_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { doc.writeTo(it) }
        doc.close()

        Uri.fromFile(outputFile)
    }

    // ===== Rotate pages =====
    suspend fun rotatePages(
        inputUri: Uri,
        outputFile: File,
        degrees: Float,
        pageIndices: List<Int>? = null // null = all pages
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(inputUri, "r") ?: return@withContext false
            val renderer = PdfRenderer(pfd)
            val doc = PdfDocument()

            for (i in 0 until renderer.pageCount) {
                val shouldRotate = pageIndices == null || i in pageIndices
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val finalBitmap = if (shouldRotate) rotateBitmap(bitmap, degrees) else bitmap

                val pageInfo = PdfDocument.PageInfo.Builder(finalBitmap.width, finalBitmap.height, i + 1).create()
                val docPage = doc.startPage(pageInfo)
                docPage.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
                doc.finishPage(docPage)

                if (shouldRotate) finalBitmap.recycle()
                bitmap.recycle()
            }

            renderer.close()
            pfd.close()
            FileOutputStream(outputFile).use { doc.writeTo(it) }
            doc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun rotatePdf(
        inputUri: Uri,
        degrees: Int,
        pageIndex: Int = -1 // -1 = all pages
    ): Uri = withContext(Dispatchers.IO) {
        val outputFile = File(getOutputDir(), "rotated_${System.currentTimeMillis()}.pdf")
        val pageIndices = if (pageIndex >= 0) listOf(pageIndex) else null
        val success = rotatePages(inputUri, outputFile, degrees.toFloat(), pageIndices)
        if (!success) throw Exception("Failed to rotate PDF")
        Uri.fromFile(outputFile)
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    // ===== Get output dir =====
    fun getOutputDir(): File {
        val dir = File(context.getExternalFilesDir(null), "PDFReader/Output")
        dir.mkdirs()
        return dir
    }

    fun getThumbnailDir(): File {
        val dir = File(context.cacheDir, "thumbnails")
        dir.mkdirs()
        return dir
    }
}
