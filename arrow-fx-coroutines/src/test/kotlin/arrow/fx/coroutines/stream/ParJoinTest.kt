package arrow.fx.coroutines.stream

import arrow.core.Either
import arrow.fx.coroutines.Atomic
import arrow.fx.coroutines.StreamSpec
import arrow.fx.coroutines.Promise
import arrow.fx.coroutines.assertThrowable
import arrow.fx.coroutines.milliseconds
import arrow.fx.coroutines.sleep
import arrow.fx.coroutines.never
import arrow.fx.coroutines.throwable
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bool
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.positiveInts
import java.lang.RuntimeException

class ParJoinTest : StreamSpec(spec = {
  "no concurrency" - {
    checkAll(Arb.stream(Arb.int())) { s ->
      s.map { Stream.just(it) }
        .parJoin(1)
        .compile()
        .toList() shouldBe s.compile().toList()
    }
  }

  "concurrency" - {
    checkAll(Arb.stream(Arb.int()), Arb.positiveInts()) { s, n0 ->
      val n = (n0 % 20) + 1
      val expected = s.compile().toList().toSet()

      s.map { Stream(it) }
        .parJoin(n)
        .compile()
        .toSet() shouldBe expected
    }
  }

  "concurrent flattening" - {
    checkAll(Arb.stream(Arb.stream(Arb.int())), Arb.positiveInts()) { s, n0 ->
      val n = n0 % 20 + 1
      val expected = s.flatten().compile().toSet()

      s.parJoin(n)
        .compile()
        .toSet() shouldBe expected
    }
  }

  "resources acquired in outer stream are released after inner streams complete" - {
    val bracketed = Stream.bracket({ Atomic(true) }, { it.set(false) })

    // Starts an inner stream which fails if the resource b is finalized
    val s = bracketed.map { atomic ->
      Stream.effect { atomic.get() }
        .flatMap { b -> if (b) Stream.unit else Stream.raiseError(RuntimeException()) }
        .repeat()
        .take(10000)
    }

    s.parJoinUnbounded()
      .compile()
      .drain()
  }

  "run finalizers of inner streams first" - {
    checkAll(Arb.stream(Arb.int()), Arb.bool()) { s1, bias ->
      val err = RuntimeException()
      val biasIdx = if (bias) 1 else 0
      val finalizerRef = Atomic(emptyList<String>())
      val runEvidenceRef = Atomic(emptyList<Int>())
      val halt = Promise<Unit>()

      suspend fun registerRun(idx: Int): Unit =
        runEvidenceRef.update { it + idx }

      // this introduces delay and failure based on bias of the test
      suspend fun finalizer(idx: Int): Unit =
        if (idx == biasIdx) {
          sleep(50.milliseconds)
          finalizerRef.update { it + "Inner $idx" }
          throw err
        } else finalizerRef.update { it + "Inner $idx" }

      val prg0 = Stream.bracket({ Unit }, { finalizerRef.update { it + "Outer" } })
        .flatMap {
          Stream(
            Stream.bracket({ registerRun(0) }, { finalizer(0) }).flatMap { s1 },
            Stream.bracket({ registerRun(1) }, { finalizer(1) })
              .flatMap { Stream.effect_ { halt.complete(Unit) } }
          )
        }

      val r = Either.catch {
        prg0
          .parJoinUnbounded()
          .compile()
          .drain()
      }
      val finalizers = finalizerRef.get()
      val streamRunned = runEvidenceRef.get()

      finalizers shouldContainExactlyInAnyOrder streamRunned.map { idx -> "Inner $idx" } + "Outer"
      finalizers.lastOrNull() shouldBe "Outer"

      if (streamRunned.contains(biasIdx)) r shouldBe Either.Left(err)
      else r shouldBe Either.Right(Unit)
    }
  }

  val full = Stream.constant(42)
  val hang = Stream.effect { never<Nothing>() }.repeat()
  val hang2 = full.drain()

  "Can take from non-hanging stream on left" - {
    Stream(full, hang)
      .parJoin(10)
      .take(2)
      .compile()
      .toList() shouldBe listOf(42, 42)
  }

  "Can take from non-hanging stream on right" - {
    Stream(hang2, full)
      .parJoin(10)
      .take(1)
      .compile()
      .toList() shouldBe listOf(42)
  }

  "Can take from non-hanging stream in middle" - {
    Stream(hang, full, hang2)
      .parJoin(10)
      .take(1)
      .compile()
      .toList() shouldBe listOf(42)
  }

  "outer failed" - {
    checkAll(Arb.throwable(), Arb.stream(Arb.int())) { e, s ->
      assertThrowable {
        Stream(s, Stream.raiseError(e))
          .parJoinUnbounded()
          .compile()
          .drain()
      } shouldBe e
    }
  }

  "propagate error from inner stream before append" - {
    checkAll(Arb.throwable(), Arb.stream(Arb.int())) { e, s ->
      assertThrowable {
        Stream(Stream.raiseError<Int>(e))
          .parJoinUnbounded()
          .append { s }
          .compile()
          .toList()
      } shouldBe e
    }
  }
})