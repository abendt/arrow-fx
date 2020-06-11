package arrow.fx.guava.extensions

import arrow.Kind
import arrow.core.Either
import arrow.core.Eval
import arrow.extension
import arrow.fx.guava.ForListenableFutureK
import arrow.fx.guava.ListenableFutureK
import arrow.fx.guava.ListenableFutureKOf
import arrow.fx.guava.fix
import arrow.typeclasses.Applicative
import arrow.typeclasses.Foldable
import arrow.typeclasses.Functor
import arrow.typeclasses.Monad

@extension
interface ListenableFutureKFunctor : Functor<ForListenableFutureK> {
  override fun <A, B> Kind<ForListenableFutureK, A>.map(f: (A) -> B): ListenableFutureK<B> =
    fix().map(f)
}

@extension
interface ListenableFutureKApplicative : Applicative<ForListenableFutureK> {
  override fun <A> just(a: A): Kind<ForListenableFutureK, A> =
    ListenableFutureK.just(a)

  override fun <A, B> Kind<ForListenableFutureK, A>.map(f: (A) -> B): ListenableFutureK<B> =
    fix().map(f)

  override fun <A, B> Kind<ForListenableFutureK, A>.ap(ff: Kind<ForListenableFutureK, (A) -> B>): ListenableFutureK<B> =
    fix().ap(ff.fix())
}

@extension
interface ListenableFutureKMonad : Monad<ForListenableFutureK>, ListenableFutureKApplicative {
  override fun <A, B> ListenableFutureKOf<A>.ap(ff: ListenableFutureKOf<(A) -> B>): ListenableFutureK<B> =
    fix().ap(ff.fix())

  override fun <A, B> ListenableFutureKOf<A>.flatMap(f: (A) -> ListenableFutureKOf<B>): ListenableFutureK<B> =
    fix().flatMap {
      f(it).fix()
    }

  override fun <A, B> ListenableFutureKOf<A>.map(f: (A) -> B): ListenableFutureK<B> =
    fix().map(f)

  override fun <A, B> tailRecM(a: A, f: (A) -> ListenableFutureKOf<Either<A, B>>): ListenableFutureK<B> =
    ListenableFutureK.tailRecM(a, f)
}

@extension
interface ListenableFutureKFoldable : Foldable<ForListenableFutureK> {

  override fun <A, B> Kind<ForListenableFutureK, A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> Kind<ForListenableFutureK, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
    fix().foldRight(lb, f)
}
