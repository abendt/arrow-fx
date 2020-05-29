package arrow.fx.guava

import arrow.Kind
import arrow.core.Either
import arrow.core.Left
import arrow.core.extensions.either.eq.eq
import arrow.core.extensions.eq
import arrow.core.left
import arrow.core.right
import arrow.core.test.UnitSpec
import arrow.core.test.generators.GenK
import arrow.core.test.generators.applicativeError
import arrow.core.test.generators.functionAToB
import arrow.core.test.generators.throwable
import arrow.core.test.laws.ApplicativeErrorLaws
import arrow.core.test.laws.ApplicativeErrorLaws.applicativeErrorHandleWith
import arrow.core.test.laws.ApplicativeLaws
import arrow.core.test.laws.FoldableLaws
import arrow.core.test.laws.MonadLaws
import arrow.core.test.laws.equalUnderTheLaw
import arrow.fx.guava.extensions.listenablefuturek.applicative.applicative
import arrow.fx.guava.extensions.listenablefuturek.applicativeError.applicativeError
import arrow.fx.guava.extensions.listenablefuturek.applicativeError.attempt
import arrow.fx.guava.extensions.listenablefuturek.applicativeError.handleErrorWith
import arrow.fx.guava.extensions.listenablefuturek.bracket.bracket
import arrow.fx.guava.extensions.listenablefuturek.foldable.foldable
import arrow.fx.guava.extensions.listenablefuturek.functor.functor
import arrow.fx.guava.extensions.listenablefuturek.monad.monad
import arrow.fx.test.laws.BracketLaws
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll

class ListenableFutureKTest : UnitSpec() {
  init {

    testLaws(
       ApplicativeLaws.laws(ListenableFutureK.applicative(), ListenableFutureK.functor(), GENK, EQK)
      //ApplicativeErrorLaws.laws(ListenableFutureK.applicativeError(), GENK, EQK)
      //MonadLaws.laws(ListenableFutureK.monad(), GENK, EQK),
      //FoldableLaws.laws(ListenableFutureK.foldable(), ListenableFutureK.applicative(), GENK, EQK),
      //BracketLaws.laws(ListenableFutureK.bracket(), GENK, EQK)
    )


    "foo" {

      val EQ = EQK.liftEq(Int.eq())

      ListenableFutureK.applicativeError().run {
        forAll(Gen.functionAToB<Throwable, Kind<ForListenableFutureK, Int>>(Gen.int().applicativeError(this)), Gen.throwable()) { f: (Throwable) -> Kind<ForListenableFutureK, Int>, e: Throwable ->
          val l1 = raiseError<Int>(e)
          println("step1")
          val left = l1.handleErrorWith(f)
          println("left " + left)
          val right = f(e)
          println("right " + right)

          left.equalUnderTheLaw(right, EQ).also {
            println(it)
          }
        }
      }
    }
  }


}

val GENK = object : GenK<ForListenableFutureK> {
  override fun <A> genK(gen: Gen<A>): Gen<Kind<ForListenableFutureK, A>> =
    gen.map {
      ListenableFutureK.just(it)
    }
}

val EQK = object : EqK<ForListenableFutureK> {
  override fun <A> Kind<ForListenableFutureK, A>.eqK(other: Kind<ForListenableFutureK, A>, EQ: Eq<A>): Boolean =
    EQ.run {
      val a = try {
        this@eqK.fix().value.get().right()
      } catch (e: Exception) {
        e.left()
      }

      val b = try {
        other.fix().value.get().right()
      } catch (e: Exception) {
        e.left()
      }

      when (a) {
        is Either.Left ->
          when (b) {
            is Either.Left -> a.a.javaClass == b.a.javaClass
            is Either.Right -> false
          }
        is Either.Right ->
          when (b) {
            is Either.Left -> false
            is Either.Right -> a.b.eqv(b.b)
          }
      }
    }
}
