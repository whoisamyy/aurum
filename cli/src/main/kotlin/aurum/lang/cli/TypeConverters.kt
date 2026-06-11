package aurum.lang.cli

import java.nio.file.Path

interface TypeConverter<T : Any> {
    fun convert(string: String): T
}

object StringConverter : TypeConverter<String> {
    override fun convert(string: String) = string
}

object IntConverter : TypeConverter<Int> {
    override fun convert(string: String) = string.toInt()
}

object CharConverter : TypeConverter<Char> {
    override fun convert(string: String) = string[0]
}

object LongConverter : TypeConverter<Long> {
    override fun convert(string: String) = string.toLong()
}

object FloatConverter : TypeConverter<Float> {
    override fun convert(string: String) = string.toFloat()
}

object DoubleConverter : TypeConverter<Double> {
    override fun convert(string: String) = string.toDouble()
}

object ShortConverter : TypeConverter<Short> {
    override fun convert(string: String) = string.toShort()
}

object ByteConverter : TypeConverter<Byte> {
    override fun convert(string: String) = string.toByte()
}

object BooleanConverter : TypeConverter<Boolean> {
    override fun convert(string: String) = string.toBoolean()
}

object PathConverter : TypeConverter<Path> {
    override fun convert(string: String): Path = Path.of(string)
}