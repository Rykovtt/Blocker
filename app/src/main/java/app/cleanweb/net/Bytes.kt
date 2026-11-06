package app.cleanweb.net

internal fun u16(b: ByteArray, off: Int): Int =
    ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

internal fun put16(b: ByteArray, off: Int, v: Int) {
    b[off] = (v shr 8).toByte()
    b[off + 1] = v.toByte()
}

internal fun put32(b: ByteArray, off: Int, v: Int) {
    put16(b, off, v ushr 16)
    put16(b, off + 2, v and 0xFFFF)
}

internal fun u32(b: ByteArray, off: Int): Long =
    (u16(b, off).toLong() shl 16) or u16(b, off + 2).toLong()
