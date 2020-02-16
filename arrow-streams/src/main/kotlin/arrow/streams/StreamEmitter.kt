package arrow.streams

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.fx.ForIO
import arrow.fx.IO
import arrow.fx.Queue
import arrow.fx.extensions.io.concurrent.concurrent
import arrow.fx.fix

interface StreamEmitter<A> {
  fun emit(a: A)
  fun complete()
  fun onTerminate(f: () -> Unit)
}

object EmitterEnd

/**
 * A pure Stream construction based on a StreamEmitter callback.
 *
 * TODO consider having an effectful constructor
 */
fun <A> Stream.Companion.fromEmitter(emitter: (StreamEmitter<A>) -> Unit): Stream<A> {
  val queue = Queue.bounded<ForIO, Either<EmitterEnd, A>>(1_000, IO.concurrent()).fix()
  val emitterImpl = object : StreamEmitter<A> {
    override fun emit(a: A) {
      queue.flatMap { it.offer(a.right()) }.unsafeRunAsync { }
    }

    override fun complete() {
      queue.flatMap { it.offer(EmitterEnd.left()) }.unsafeRunAsync { }
    }

    override fun onTerminate(f: () -> Unit) {
    }
  }
//  emitter(emitterImpl)
//
//  return Stream.Ongoing(
//
//  )
 TODO()
}
