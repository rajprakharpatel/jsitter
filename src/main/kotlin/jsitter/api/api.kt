package jsitter.api

import java.nio.ByteBuffer

data class Key<T>(val key: Any)

interface DataHolder<out T> {
    fun <U> assoc(k: Key<U>, v: U): T
    operator fun <U> get(k: Key<U>): U?
}

interface Tree<out T : NodeType> : DataHolder<Tree<T>> {
    val language: Language
    val nodeType: T
    val byteSize: Int
    fun zipper(): Zipper<T>
}

interface Zipper<out T : NodeType> : DataHolder<Zipper<T>> {
    fun up(): Zipper<*>?
    fun down(): Zipper<*>?
    fun right(): Zipper<*>?
    fun left(): Zipper<*>?

    fun retainSubtree(): Tree<T>

    val byteOffset: Int
    val byteSize: Int
    val nodeType: T
    val language: Language
}

fun<T: NodeType> Zipper<T>.skip(): Zipper<*>? {
    var u: Zipper<*>? = this
    while (u != null) {
        val r = u.right()
        if (r != null) {
            return r
        } else {
            u = u.up()
        }
    }
    return null
}

fun <T: NodeType> Zipper<T>.next() : Zipper<*>? = down() ?: skip()

open class NodeType(val name: String) {
    var id: Int = -1
    var initialized = false
    override fun toString(): String = name
}

object Error : NodeType("ERROR") {
    init {
        id = -1
        initialized = true
    }
}

open class Terminal(name: String) : NodeType(name)

interface Language {
    val name: String
    fun <T : NodeType> parser(nodeType: T): Parser<T>
    fun nodeType(name: String): NodeType
    fun register(nodeType: NodeType)
}

enum class Encoding(val i: Int) { UTF8(0), UTF16(1) }

interface Text {
    /*
    * put data into ByteBuffer up to it's limit
    * */
    fun read(byteOffset: Int, output: ByteBuffer)

    val encoding: Encoding
}

data class Edit(val startByte: Int,
                val oldEndByte: Int,
                val newEndByte: Int)

typealias BytesRange = Pair<Int, Int>

data class ParseResult<T: NodeType>(val tree: Tree<T>,
                                    val changedRanges: List<BytesRange>)

data class Increment<T: NodeType>(val oldTree: Tree<T>, val edits: List<Edit>)

class CancellationToken {
    @Volatile
    internal var cancelled = false
    @Volatile
    internal var handler: (() -> Unit)? = null

    fun cancel() {
        synchronized(this) {
            if (!cancelled) {
                cancelled = true
                val h = handler
                if (h != null) {
                    h()
                }
            }
        }
    }

    internal fun onCancel(f: () -> Unit) {
        synchronized(this) {
            handler = f
        }
    }
}

interface Parser<T : NodeType> {
    fun parse(text: Text, cancellationToken: CancellationToken?, increment: Increment<T>? = null): ParseResult<T>?
    fun parse(text: Text, increment: Increment<T>? = null): ParseResult<T> = parse(text, null, increment)!!
    val language: Language
}
