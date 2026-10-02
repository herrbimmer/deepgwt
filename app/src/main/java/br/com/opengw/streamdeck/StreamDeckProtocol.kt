package br.com.opengw.streamdeck

/**
 * Wire protocol for the RiseMode RM MC 01 W / MiraBox StreamDock 293S USB deck (VID 0x0A00,
 * PID 0x1001). There is no public spec for this hardware -- this was reconstructed from two
 * independent, non-authoritative sources (no official datasheet exists for either):
 *
 * 1. A from-scratch Node.js reimplementation of the "293" command family (raw libusb interrupt
 *    transfers, ASCII command tags) targeting a *different* VID/PID in the same product line.
 *    That gives us the framing below (CRT envelope, BAT/CLE/STP/LIG tags, chunking).
 * 2. Packet sniffing notes against this exact clone (VID 0x0A00), which independently confirmed
 *    the input report layout (ACK header, key code at byte 9, unreliable state byte) and the
 *    "must call refresh() or nothing updates" behavior -- both reused here for the read path.
 *
 * Neither source was captured against this precise VID/PID doing an image write, so the write
 * path (BAT/STP framing) is a best-effort synthesis, not a confirmed capture. If keys don't
 * light up or show garbage on real hardware, this file is the first place to check -- e.g. by
 * wiring [br.com.opengw.nucleo.Shell] logs or a USB sniffer against the
 * real transfers and comparing byte-for-byte.
 */
object StreamDeckProtocol {
    const val VENDOR_ID = 0x0A00
    const val PRODUCT_ID = 0x1001

    const val PACKET_SIZE = 512
    private const val ENVELOPE_SIZE = 5

    /** Physical key numbering (1..18) -> hardware key id used when addressing an image write. */
    private val IMAGE_KEY_MAP = mapOf(
        1 to 13, 2 to 10, 3 to 7, 4 to 4, 5 to 1,
        6 to 14, 7 to 11, 8 to 8, 9 to 5, 10 to 2,
        11 to 15, 12 to 12, 13 to 9, 14 to 6, 15 to 3,
        16 to 16, 17 to 17, 18 to 18
    )

    /** Reverse of [IMAGE_KEY_MAP]: hardware key id reported on button press -> logical key (1..18). */
    private val HARDWARE_TO_LOGICAL_KEY = IMAGE_KEY_MAP.entries.associate { (logical, hw) -> hw to logical }

    const val KEY_COUNT = 18

    fun hardwareKeyForImage(logicalKey: Int): Int =
        IMAGE_KEY_MAP[logicalKey] ?: error("Tecla lógica inválida: $logicalKey (esperado 1..$KEY_COUNT)")

    fun logicalKeyForHardware(hardwareKey: Int): Int? = HARDWARE_TO_LOGICAL_KEY[hardwareKey]

    /** Wakes the screen up from sleep. */
    fun wakeScreen(): ByteArray = envelope(tag("DIS"))

    /** Applies whatever key/background images were sent since the last refresh. Nothing shows up without this. */
    fun refresh(): ByteArray = envelope(tag("STP"))

    fun setBrightness(percent: Int): ByteArray =
        envelope(tag("LIG") + byteArrayOf(percent.coerceIn(0, 100).toByte()))

    fun clearKey(logicalKey: Int): ByteArray =
        envelope(byteArrayOf('C'.code.toByte(), 'L'.code.toByte(), 'E'.code.toByte(), 0, 0, 0, hardwareKeyByte(logicalKey)))

    /**
     * Announces an incoming JPEG of [jpegSize] bytes for [logicalKey]. Must be followed by the
     * chunks from [imageChunks] (in order) and then [refresh].
     */
    fun announceKeyImage(logicalKey: Int, jpegSize: Int): ByteArray =
        envelope(
            byteArrayOf('B'.code.toByte(), 'A'.code.toByte(), 'T'.code.toByte()) +
                beUInt32(jpegSize) +
                byteArrayOf(hardwareKeyByte(logicalKey))
        )

    /** Raw image bytes chunked into 512-byte packets (last one zero-padded), sent with NO envelope. */
    fun imageChunks(jpeg: ByteArray): List<ByteArray> {
        if (jpeg.isEmpty()) return emptyList()
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < jpeg.size) {
            val end = minOf(offset + PACKET_SIZE, jpeg.size)
            chunks.add(padTo(jpeg.copyOfRange(offset, end), PACKET_SIZE))
            offset = end
        }
        return chunks
    }

    /** Total bytes written to the OUT endpoint for a single command packet (envelope + payload). */
    const val COMMAND_PACKET_SIZE = ENVELOPE_SIZE + PACKET_SIZE

    // ---- Input decoding ----

    /** Input reports start with this 8-byte ASCII header on every button-press packet. */
    val INPUT_ACK_HEADER = byteArrayOf(
        'A'.code.toByte(), 'C'.code.toByte(), 'K'.code.toByte(), 0, 0,
        'O'.code.toByte(), 'K'.code.toByte(), 0
    )
    const val INPUT_KEY_CODE_OFFSET = 9
    const val INPUT_STATE_OFFSET = 10

    fun isButtonPacket(packet: ByteArray): Boolean =
        packet.size > INPUT_STATE_OFFSET && INPUT_ACK_HEADER.indices.all { packet[it] == INPUT_ACK_HEADER[it] }

    // ---- framing helpers ----

    private fun tag(name: String): ByteArray {
        require(name.length == 3)
        return name.toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0)
    }

    private fun hardwareKeyByte(logicalKey: Int): Byte = hardwareKeyForImage(logicalKey).toByte()

    private fun envelope(inner: ByteArray): ByteArray {
        val crt = byteArrayOf('C'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte(), 0, 0)
        return crt + padTo(inner, PACKET_SIZE)
    }

    private fun padTo(data: ByteArray, size: Int): ByteArray {
        if (data.size >= size) return data.copyOf(size)
        return data.copyOf(size) // copyOf zero-fills the extra bytes
    }

    private fun beUInt32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
