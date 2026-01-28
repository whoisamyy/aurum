package lang.aurum.ir

import java.io.DataOutputStream

interface Writable {
    fun write(out: DataOutputStream)
}

interface Sized : Writable {
    fun size(): Int
}

interface CodeElement : Sized

//abstract class CompositeCodeElement(protected val codeElements: List<CodeElement>) : CodeElement {
//    final override fun size(): Int = codeElements.sumOf(CodeElement::size)
//
//    final override fun write(out: DataOutputStream) {
//        codeElements.forEach { it.write(out) }
//    }
//}
