package arrow.fx.guava

import arrow.Kind
import arrow.core.Either
import arrow.core.extensions.either.eq.eq
import arrow.core.left
import arrow.core.right
import arrow.core.test.UnitSpec
import arrow.core.test.generators.GenK
import arrow.core.test.laws.ApplicativeErrorLaws
import arrow.core.test.laws.ApplicativeLaws
import arrow.core.test.laws.FoldableLaws
import arrow.core.test.laws.MonadLaws
import arrow.fx.guava.extensions.listenablefuturek.applicative.applicative
import arrow.fx.guava.extensions.listenablefuturek.applicativeError.applicativeError
import arrow.fx.guava.extensions.listenablefuturek.foldable.foldable
import arrow.fx.guava.extensions.listenablefuturek.functor.functor
import arrow.fx.guava.extensions.listenablefuturek.monad.monad
import arrow.typeclasses.Eq
import arrow.typeclasses.EqK
import io.kotlintest.properties.Gen

class ListenableFutureKTest : UnitSpec() {
  init {
    testLaws(
      ApplicativeLaws.laws(ListenableFutureK.applicative(), ListenableFutureK.functor(), GENK, EQK),
      MonadLaws.laws(ListenableFutureK.monad(), GENK, EQK),
      FoldableLaws.laws(ListenableFutureK.foldable(), ListenableFutureK.applicative(), GENK, EQK),
      ApplicativeErrorLaws.laws(ListenableFutureK.applicativeError(), GENK, EQK)
    )
  }
}

private val GENK = object : GenK<ForListenableFutureK> {
  override fun <A> genK(gen: Gen<A>): Gen<Kind<ForListenableFutureK, A>> =
    gen.map {
      ListenableFutureK.just(it)
    }
}

private val EQK = object : EqK<ForListenableFutureK> {
  override fun <A> Kind<ForListenableFutureK, A>.eqK(other: Kind<ForListenableFutureK, A>, EQ: Eq<A>): Boolean =
    Either.eq(Eq.any(), EQ).run {
      val a = this@eqK.attempt()

      val b = other.attempt()

      a.eqv(b)
    }
}

private fun <A> Kind<ForListenableFutureK, A>.attempt(): Either<Exception, A> =
  try {
    value().get().right()
  } catch (e: Exception) {
    e.left()
  }
