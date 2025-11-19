package lang.aurum.util

open class BinaryExprTree<T>(
    var left: BinaryExprTree<T>? = null,
    var right: BinaryExprTree<T>? = null,
    val value: T?
) {
    open val leftValue: T? get() = left?.value
    open val rightValue: T? get() = right?.value
}