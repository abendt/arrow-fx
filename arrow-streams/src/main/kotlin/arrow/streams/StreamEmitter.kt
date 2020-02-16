package arrow.streams

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.fx.ForIO
import arrow.fx.IO
import arrow.fx.Queue
import arrow.fx.extensions.fx
import arrow.fx.extensions.io.concurrent.concurrent
import arrow.fx.fix

interface StreamEmitter<A> {
  fun emit(a: () -> A)
  fun complete()
  fun onTerminate(f: () -> Unit)
}

object Complete

/**
 * An effectful(?) Stream construction based on a StreamEmitter callback.
 *
 * Stream.fromEmitter { emitter ->
 *   emiter.onTerminate { println("Done!") }
 *   emitter.emit { 1 }
 *   emitter.emit { 2 }
 *   emitter.emit { 2 }
 *   emitter.complete()
 * }
 */
fun <A> Stream.Companion.fromEmitter(emitter: (StreamEmitter<A>) -> Unit): Stream<A> {
  return Stream.Ongoing(
    IO.fx {
      val queue = !Queue.bounded<ForIO, Either<Complete, A>>(1_000, IO.concurrent())
      val emitterImpl = object : StreamEmitter<A> {
        override fun emit(a: () -> A) {
          // TODO consider error case
          queue.offer(a().right()).fix().unsafeRunAsync { }
        }

        override fun complete() {
          queue.offer(Complete.left()).fix().unsafeRunAsync { }
        }

        override fun onTerminate(f: () -> Unit) {
          TODO()
        }
      }

      emitter(emitterImpl)
      fromFiniteQueue(queue)
    }
  )
}

fun <A> Stream.Companion.fromFiniteQueue(queue: Queue<ForIO, Either<Complete, A>>): Stream<A> {
  fun loop(): Stream<A> =
    Stream.Ongoing(
      IO.fx {
        val valueOrCompletion = queue.take().bind()
        valueOrCompletion.fold({
          Stream.Empty
        }, {
          Stream.Cons(it, loop())
        })
      }
    )

  return loop()
}
