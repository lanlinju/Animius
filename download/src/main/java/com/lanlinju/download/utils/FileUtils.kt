package com.lanlinju.download.utils

import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

fun File.shadow(): File {
    val shadowPath = "$canonicalPath.download"
    return File(shadowPath)
}

fun File.tmp(): File {
    val tmpPath = "$canonicalPath.tmp"
    return File(tmpPath)
}

fun File.tsFile(index: Long): File {
    val tsPath = "$canonicalPath-${index}.ts"
    return File(tsPath)
}

fun File.recreate(length: Long = 0L) {
    delete()
    val created = createNewFile()
    if (created) {
        setLength(length)
    } else {
        throw IllegalStateException("File create failed!")
    }
}

fun File.setLength(length: Long = 0L) {
    RandomAccessFile(this, "rw").setLength(length)
}

fun File.channel(): FileChannel {
    return RandomAccessFile(this, "rw").channel
}

fun File.mappedByteBuffer(position: Long, size: Long): MappedByteBuffer {
    val channel = channel()
    val map = channel.map(FileChannel.MapMode.READ_WRITE, position, size)
    channel.closeQuietly()
    return map
}

fun File.clear() {
    if (!exists()) return

    val shadow = shadow()
    val tmp = tmp()

    for (f in parentFile.listFiles()) {
        if (f.name.startsWith("${this.name}-") && f.name.endsWith(".ts")) f.delete()
    }

    shadow.delete()
    tmp.delete()
    delete()
}

fun File.handleFormat() {
    val headFile = RandomAccessFile(this, "rw")
    val bytes = ByteArray(8)
    headFile.read(bytes)
    if (!String(bytes).contains("PNG")) {
        headFile.close()
        return
    }
    bytes.fill(0xff.toByte())
    headFile.seek(0L)
    headFile.write(bytes, 0, bytes.size)
    headFile.close()
}
