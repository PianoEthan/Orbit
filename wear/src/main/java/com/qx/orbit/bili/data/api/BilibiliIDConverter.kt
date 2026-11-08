package com.qx.orbit.bili.data.api

object BilibiliIDConverter {
    private const val XOR_CODE = 23442827791579L
    private const val MASK_CODE = 2251799813685247L
    private const val MAX_AID = 1L shl 51
    private const val BASE = 58L
    private const val DATA = "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"

    fun bvToAid(bv: String): Long {
        if (!bv.startsWith("BV1") || bv.length < 12) return 0L
        val bvidArr = bv.toCharArray()
        
        val temp1 = bvidArr[3]
        bvidArr[3] = bvidArr[9]
        bvidArr[9] = temp1

        val temp2 = bvidArr[4]
        bvidArr[4] = bvidArr[7]
        bvidArr[7] = temp2

        var tmp = 0L
        for (i in 3 until bvidArr.size) {
            val idx = DATA.indexOf(bvidArr[i])
            if (idx != -1) {
                tmp = tmp * BASE + idx
            }
        }
        return (tmp and MASK_CODE) xor XOR_CODE
    }

    fun aidToBv(aid: Long): String {
        val bytes = charArrayOf('B', 'V', '1', '0', '0', '0', '0', '0', '0', '0', '0', '0')
        var bvIndex = bytes.size - 1
        var tmp = (MAX_AID or aid) xor XOR_CODE
        while (tmp > 0) {
            bytes[bvIndex] = DATA[(tmp % BASE).toInt()]
            tmp /= BASE
            bvIndex--
        }
        
        val temp1 = bytes[3]
        bytes[3] = bytes[9]
        bytes[9] = temp1

        val temp2 = bytes[4]
        bytes[4] = bytes[7]
        bytes[7] = temp2

        return String(bytes)
    }
}
