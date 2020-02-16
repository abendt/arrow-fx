package arrow.streams

import arrow.core.Either
import arrow.core.None
import arrow.core.left
import arrow.core.right
import arrow.core.some
import arrow.fx.ForIO
import arrow.fx.IO
import arrow.fx.Queue
import arrow.fx.extensions.fx
import arrow.fx.extensions.io.concurrent.concurrent
import arrow.fx.fix
import arrow.fx.typeclasses.seconds
import arrow.test.UnitSpec
import io.kotlintest.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamTest : UnitSpec() {

  init {
//    FunctorLaws.laws()
    "forEach" {
      val latch = CountDownLatch(1)
      var acc = ""
      val mapper = { it: Int -> 20 - it }
      val a = (0 until 10).toList()
      Stream.fromArray(a)
        .map(mapper)
        .forEach(onNext = { acc += it }, onComplete = { latch.countDown() })

      latch.await(1, TimeUnit.SECONDS)
      a.map(mapper).joinToString("") shouldBe acc
    }

    "map" {
      val mapper = { it: Int -> 20 - it }
      val a = (0 until 10).toList()
      Stream.fromArray(a)
        .map(mapper)
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe a.map(mapper).some()
    }

    "flatten" {
      val mapper = { it: Int -> listOf(it * 10 + 1, it * 10 + 2, it * 10 + 3) }
      val a = listOf(1, 2, 3)
      Stream.fromArray(a)
        .map { Stream.fromArray(mapper(it)) }
        .concatAll()
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe a.map(mapper).flatten().some()
    }

    "flatMap" {
      val mapper = { it: Int -> listOf(it * 10 + 1, it * 10 + 2, it * 10 + 3) }
      val a = listOf(1, 2, 3)
      Stream.fromArray(a)
        .flatMap { Stream.fromArray(mapper(it)) }
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe a.flatMap(mapper).some()
    }

    "flatMap2" {
      val mapper = { it: Int -> listOf(it * 10 + 1, it * 10 + 2, it * 10 + 3) }
      val a = listOf<Int>()
      Stream.fromArray(a)
        .flatMap { Stream.fromArray(mapper(it)) }
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe  a.flatMap(mapper).some()
    }

    "concat" {
      val a = listOf(1, 2, 3)
      val b = listOf(4, 5, 6)
      Stream.fromArray(a)
        .concat(Stream.fromArray(b))
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe (a + b).some()
    }

    "concat2" {
      val a = listOf<Int>()
      val b = listOf(4, 5, 6)
      Stream.fromArray(a)
        .concat(Stream.fromArray(b))
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe (a + b).some()
    }

    "merge" {
      val a = listOf(1, 2, 3, 7, 8, 9)
      val b = listOf(4, 5, 6)
      Stream.fromArray(a)
        .merge(Stream.fromArray(b))
        .toList()
        .unsafeRunTimed(1.seconds) shouldBe listOf(1, 4, 2, 5, 3, 6, 7, 8, 9).some()
    }

    "mergeAll" {
    }

    "concatAll" {
    }

    "skipUntil" {
      //
//        Stream.seq((0..100).toList(), 0.milliseconds, 100.milliseconds)
//            .flatMap {  }
//            .skipUntil {  }
    }

    "fromEmitter" {
      val list = (0 until 10).toList()
      Stream.fromEmitter<Int> { emitter ->
        list.forEach { emitter.emit { it } }
        emitter.complete()
      }.toList()
        .unsafeRunTimed(5.seconds) shouldBe list.some()
    }

    "empty fromEmitter" {
      Stream.fromEmitter<Int> { emitter ->
        emitter.apply {
          complete()
        }
      }.toList()
        .unsafeRunTimed(5.seconds) shouldBe listOf<Int>().some()
    }

    "non finished fromEmitter should hang" {
      Stream.fromEmitter<Int> { emitter ->
        emitter.apply {
          emit { 1 }
        }
      }.toList()
        .unsafeRunTimed(2.seconds) shouldBe None
    }

    "slow emitter fromFiniteQueue" {
      IO.fx {
        val queue = !Queue.bounded<ForIO, Either<Complete, Int>>(1_000, IO.concurrent()).fix()

        queue.apply {
          !sleep(1.seconds).followedBy(offer(1.right()))
          !sleep(1.seconds).followedBy(offer(2.right()))
          !sleep(1.seconds).followedBy(offer(Complete.left()))
        }

        val list = !Stream.fromFiniteQueue(queue).toList()
        list
      }.unsafeRunTimed(4.seconds) shouldBe listOf(1, 2).some()
    }
  }
}