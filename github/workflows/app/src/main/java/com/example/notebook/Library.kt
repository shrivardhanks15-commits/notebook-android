package com.example.notebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** One pen stroke. Points are normalized (0..1) x,y pairs; width is a fraction of page width. */
data class Ink(val color: Int, val width: Float, val pts: List<Float>)

data class Page(
    val id: String = UUID.randomUUID().toString(),
    val strokes: List<Ink> = emptyList(),
    val pdfFile: String? = null,   // imported PDF used as background
    val pdfIndex: Int = 0
)

data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val color: Int,
    val pages: List<Page> = listOf(Page()),
    val updated: Long = System.currentTimeMillis()
)

/** Holds all notebooks and persists them as JSON in the app's private storage. */
class Library(private val ctx: Context) {
    var notebooks by mutableStateOf(listOf<Notebook>())
        private set
    private val file = File(ctx.filesDir, "notebooks.json")
    private val pdfDir = File(ctx.filesDir, "pdfs").apply { mkdirs() }

    init { load() }

    fun get(id: String) = notebooks.firstOrNull { it.id == id }
    fun add(title: String, color: Int) = set(listOf(Notebook(title = title, color = color)) + notebooks)
    fun delete(id: String) = set(notebooks.filter { it.id != id })
    fun update(nb: Notebook) = set(notebooks.map {
        if (it.id == nb.id) nb.copy(updated = System.currentTimeMillis()) else it
    })

    private fun set(l: List<Notebook>) { notebooks = l; save() }

    fun importPdf(nbId: String, at: Int, uri: Uri) {
        val nb = get(nbId) ?: return
