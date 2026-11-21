package com.min.assetstudio

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Unpacks assets/cli.zip into filesDir/cli/<tag>. */
object BinaryInstaller {
    const val EXE = "AssetStudioModCLI"
    private const val OK = ".ok"

    private class Counting(inner: InputStream) : FilterInputStream(inner) {
        var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) count += it }
    }

    private fun root(ctx: Context) = File(ctx.filesDir, "cli")
    fun dir(ctx: Context) = File(root(ctx), BuildConfig.BUILD_TAG)
    fun exe(ctx: Context) = File(dir(ctx), EXE)

    private fun bundledTag(ctx: Context): String? = try {
        ctx.assets.open("cli.info").bufferedReader().use { r ->
            r.readLines().firstOrNull { it.startsWith("tag=") }?.removePrefix("tag=")?.trim()
        }
    } catch (e: IOException) {
        null
    }

    fun install(ctx: Context, progress: (Int) -> Unit) {
        if (bundledTag(ctx) != BuildConfig.BUILD_TAG) error("CLI bundle does not match this build")
        if (File(dir(ctx), OK).exists() && exe(ctx).exists()) return

        val root = root(ctx).apply { mkdirs() }
        val tmp = File(root, BuildConfig.BUILD_TAG + ".part")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val base = tmp.canonicalPath + File.separator

        val total = ctx.assets.openFd("cli.zip").use { it.length }.coerceAtLeast(1L)
        val counting = Counting(ctx.assets.open("cli.zip"))
        ZipInputStream(BufferedInputStream(counting, 1 shl 16)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(tmp, e.name)
                if (!out.canonicalPath.startsWith(base)) throw IOException("Bad entry: ${e.name}")
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zin.copyTo(it, 1 shl 16) }
                    out.setReadable(true, false)
                    if (!out.name.endsWith(".dll")) out.setExecutable(true, false)
                }
                progress((counting.count * 100 / total).toInt().coerceIn(0, 100))
                e = zin.nextEntry
            }
        }
        val exe = File(tmp, EXE)
        if (!exe.exists()) throw IOException("Missing $EXE in bundle")
        exe.setExecutable(true, false)
        File(tmp, OK).writeText(BuildConfig.BUILD_TAG)

        val target = dir(ctx)
        target.deleteRecursively()
        if (!tmp.renameTo(target)) throw IOException("Cannot move files into place")
        root.listFiles()?.forEach { if (it.name != BuildConfig.BUILD_TAG) it.deleteRecursively() }
    }
}
