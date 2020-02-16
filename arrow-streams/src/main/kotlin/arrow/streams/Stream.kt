package arrow.streams

import arrow.core.identity
import arrow.fx.IO
import arrow.fx.extensions.io.concurrent.raceN
import arrow.fx.internal.TimeoutException
import arrow.fx.typeclasses.Duration
import arrow.fx.typeclasses.seconds
import kotlin.coroutines.EmptyCoroutineContext

class ForStream private constructor() {
  companion object
}
typealias StreamOf<A> = arrow.Kind<ForStream, A>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A> StreamOf<A>.fix(): Stream<A> =
  this as Stream<A>

sealed class Stream<out A> : StreamOf<A> {
  internal object Empty : Stream<Nothing>()
  internal data class Error(val error: Throwable) : Stream<Nothing>()
  internal data class Ongoing<A>(val promise: IO<Stream<A>>) : Stream<A>()
  internal data class Cons<out A>(val head: A, val tail: Stream<A>) : Stream<A>()

  companion object
}

// effectful main execution function
fun <A> Stream<A>.forEach(
  onNext: (A) -> Unit,
  onError: (Throwable) -> Unit = {},
  onComplete: () -> Unit = {}
): Unit = when (this) { // TODO return cancelable
  Stream.Empty -> onComplete()
  is Stream.Error -> onError(error)
  is Stream.Ongoing -> {
    promise.unsafeRunAsync { p ->
      p.fold(ifLeft = onError, ifRight = {
        it.forEach(onNext, onError, onComplete)
      })
    }
  }
  is Stream.Cons -> {
    onNext(head)
    tail.forEach(onNext, onError, onComplete)
  }
}

fun <A> Stream<A>.forEachSync(
  onNext: (A) -> Unit,
  onError: (Throwable) -> Unit = {},
  onComplete: () -> Unit = {},
  timeout: Duration = 5.seconds
): Unit = when (this) { // TODO return cancelable
  Stream.Empty -> onComplete()
  is Stream.Error -> onError(error)
  is Stream.Ongoing -> {
    promise.unsafeRunTimed(timeout).fold({ throw TimeoutException("Sync op timeout!") }, { it.forEachSync(onNext, onError, onComplete) })
  }
  is Stream.Cons -> {
    onNext(head)
    tail.forEachSync(onNext, onError, onComplete)
  }
}

@Suppress("UNCHECKED_CAST")
fun <A, B> Stream<A>.map(f: (A) -> B): Stream<B> = when (this) {
  Stream.Empty,
  is Stream.Error -> this as Stream<B>
  is Stream.Cons -> {
    val tailM = this.tail.map(f)
    val futP = IO { Stream.Cons(f(head), tailM) }
    Stream.Ongoing(futP)
  }
  is Stream.Ongoing -> Stream.Ongoing(promise = promise.map { it.map(f) })
}

@Suppress("UNCHECKED_CAST")
fun <A> Stream<Stream<A>>.flatten(f: (Stream<A>, Stream<A>) -> Stream<A>): Stream<A> = when (this) {
  Stream.Empty,
  is Stream.Error -> this as Stream<A>
  is Stream.Ongoing -> Stream.Ongoing(promise.map { it.flatten(f) })
  is Stream.Cons -> f(head, tail.flatten(f))
}

@Suppress("UNCHECKED_CAST")
fun <A, B> Stream<A>.flatMap(f: (A) -> Stream<B>): Stream<B> = when (this) {
  Stream.Empty,
  is Stream.Error -> this as Stream<B>
  is Stream.Ongoing -> Stream.Ongoing(promise.map { it.flatMap(f) })
  is Stream.Cons -> Stream.Cons(f(head), tail.map(f)).concatAll()
}

infix fun <A> Stream<A>.concat(other: Stream<A>): Stream<A> = when (this) {
  Stream.Empty -> other
  is Stream.Error -> this
  is Stream.Ongoing -> Stream.Ongoing(promise.map { it.concat(other) })
  is Stream.Cons -> Stream.Cons(head, tail.concat(other))
}

fun <A> Stream<A>.merge(other: Stream<A>): Stream<A> = when (this) {
  Stream.Empty -> other
  is Stream.Error -> this
  is Stream.Ongoing -> when (other) {
    is Stream.Ongoing -> Stream.Ongoing(
      EmptyCoroutineContext.raceN(
        promise.flatMap { a -> other.promise.map { b -> a.merge(b) } },
        other.promise.flatMap { a -> promise.map { b -> a.merge(b) } }
      ).map { it.fold(::identity, ::identity) }
    )
    else -> other.merge(this)
  }
  is Stream.Cons -> Stream.Cons(head, other.merge(tail))
}

fun <A> Stream<Stream<A>>.mergeAll(): Stream<A> =
  flatten { s1, s2 -> s1.merge(s2) }

fun <A> Stream<Stream<A>>.concatAll(): Stream<A> =
  flatten { s1, s2 -> s1.concat(s2) }

fun <A> Stream<A>.scan(accF: (A, A) -> A): Stream<A> {
  fun scn(stream: Stream<A>, seed: A?): Stream<A> = when (stream) {
    Stream.Empty,
    is Stream.Error -> stream
    is Stream.Ongoing -> Stream.Ongoing(stream.promise.map { scn(it, seed) })
    is Stream.Cons -> if (seed == null) Stream.Cons(stream.head, scn(stream.tail, stream.head))
    else Stream.Ongoing(
      IO {
        val acc1 = accF(seed, stream.head)
        Stream.Cons(acc1, scn(stream.tail, acc1)) as Stream<A>
      }
    )
  }

  return scn(this, null)
}

fun <A> Stream<A>.debounce(event: (A) -> IO<Unit>): Stream<A> {
  fun dbnc(stream: Stream<A>, last: A?): Stream<A> = when (stream) {
    is Stream.Empty,
    is Stream.Error ->
      if (last != null) Stream.Cons(last, stream)
      else stream
    is Stream.Ongoing ->
      if (last == null) Stream.Ongoing(promise = stream.promise.map { dbnc(it, null) })
      else Stream.Ongoing(
        EmptyCoroutineContext.raceN(
          event(last).flatMap { IO { Stream.Cons(last, dbnc(stream, null)) } },
          stream.promise.map { dbnc(it, last) }
        ).map { it.fold(::identity, ::identity) }
      )
    is Stream.Cons -> dbnc(stream.tail, stream.head)
  }

  return dbnc(this, null)
}

fun <B, A : B> Stream<A>.reduce(f: (B, A) -> B): IO<B> {
  fun op(stream: Stream<A>, seed: B?): IO<B> = when (stream) {
    Stream.Empty ->
      if (seed == null) IO.raiseError(EmptyStream())
      else IO.just(seed)
    is Stream.Error -> IO.raiseError(stream.error)
    is Stream.Ongoing -> stream.promise.flatMap { op(it, seed) }
    is Stream.Cons ->
      if (seed == null) op(stream.tail, stream.head)
      else op(stream.tail, f(seed, stream.head))
  }

  return op(this, null)
}

fun <B, A> Stream<A>.fold(initial: B, f: (B, A) -> B): IO<B> = when (this) {
  Stream.Empty -> IO.just(initial)
  is Stream.Error -> IO.raiseError(error)
  is Stream.Ongoing -> promise.flatMap { it.fold(initial, f) }
  is Stream.Cons -> tail.fold(f(initial, head), f)
}

fun <A> Stream<A>.toList(): IO<List<A>> =
  fold(listOf()) { acc, a -> acc + listOf(a) }

class EmptyStream : Throwable()

val thread = Thread.currentThread().name

fun <A> Stream<A>.runlog(prefix: String, verbose: Boolean) {
  fun verbose() = if (verbose) "// $thread ${System.currentTimeMillis()}" else ""
  forEachSync(
    onNext = { println("$prefix $it ${verbose()}") },
    onError = { println("$prefix error:$it ${verbose()}") },
    onComplete = { println("$prefix end ${verbose()}") }
  )
}

fun <A> Stream.Companion.sequence(arr: List<A>, delay: Duration, interval: Duration): Stream<A> {
  fun from(index: Int, millis: Duration): Stream<A> {
    val getter: () -> Stream<A> = {
      if (index < arr.size) Stream.Cons(arr[index], from(index + 1, interval))
      else Stream.Empty
    }

    return Stream.Ongoing(IO.sleep(millis).map { getter() })
  }

  return from(0, delay)
}

fun <A> Stream.Companion.fromArray(arr: List<A>): Stream<A> {
  fun from(index: Int): Stream<A> =
    if (index < arr.size) Stream.Cons(arr[index], from(index + 1))
    else Stream.Empty

  return from(0)
}

fun <A> Stream.Companion.lazy(f: () -> A): Stream<A> =
  Stream.Ongoing(IO { Stream.just(f()) })

fun <A> Stream.Companion.just(a: A): Stream<A> =
  Stream.Cons(a, Stream.Empty)
