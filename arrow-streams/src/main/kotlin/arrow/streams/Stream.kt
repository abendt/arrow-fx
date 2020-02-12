package arrow.streams

class ForStream private constructor() {
    companion object
}
typealias StreamOf<F, E, A> = arrow.Kind3<F, ForStream, E, A>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <F, E, A> StreamOf<F, E, A>.fix(): Stream<F, E, A> =
    this as Stream<F, E, A>

sealed class Stream<F, out E, out A> : StreamOf<F, E, A> {
    companion object {
        fun <F, E, A> just(a: A): Stream<F, E, A> = Pure(a)
    }

    internal data class Pure<F, out A>(val a: A) : Stream<F, Nothing, A>()
}