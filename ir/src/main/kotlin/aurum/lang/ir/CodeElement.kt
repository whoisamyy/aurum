package aurum.lang.ir

import java.io.DataOutputStream

interface Writable {
    fun write(out: DataOutputStream)
}

interface Sized : Writable {
    fun size(): Int
}

interface CodeElement : Sized