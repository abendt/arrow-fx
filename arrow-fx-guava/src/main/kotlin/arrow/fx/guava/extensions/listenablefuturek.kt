package arrow.fx.guava.extensions

import arrow.Kind
import arrow.core.Either
import arrow.core.Eval
import arrow.extension
import arrow.fx.guava.ForListenableFutureK
import arrow.fx.guava.ListenableFutureK
import arrow.fx.guava.ListenableFutureKOf
import arrow.fx.guava.fix
import arrow.fx.guava.listenableFutureHandleErrorWith
import arrow.fx.guava.k
import arrow.fx.typeclasses.Bracket
import arrow.fx.typeclasses.ExitCase
import arrow.typeclasses.Applicative
import arrow.typeclasses.ApplicativeError
import arrow.typeclasses.Foldable
import arrow.typeclasses.Functor
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadError
import arrow.typeclasses.MonadThrow
import com.google.common.util.concurrent.Futures

@extension
interface ListenableFutureKFunctor : Functor<ForListenableFutureK> {
  override fun <A, B> Kind<ForListenableFutureK, A>.map(f: (A) -> B): ListenableFutureK<B> =
    fix().map(f)
}

@extension
interface ListenableFutureKApplicative : Applicative<ForListenableFutureK> {
  override fun <A> just(a: A): Kind<ForListenableFutureK, A> =
    Futures.immediateFuture(a).k()

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

  override fun <A, B> tailRecM(a: A, f: Function1<A, ListenableFutureKOf<arrow.core.Either<A, B>>>): ListenableFutureK<B> =
    ListenableFutureK.tailRecM(a, f)
}

@extension
interface ListenableFutureKFoldable : Foldable<ForListenableFutureK> {

  override fun <A, B> Kind<ForListenableFutureK, A>.foldLeft(b: B, f: (B, A) -> B): B =
    fix().foldLeft(b, f)

  override fun <A, B> Kind<ForListenableFutureK, A>.foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
    fix().foldRight(lb, f)
}

@extension
interface ListenableFutureKApplicativeError : ApplicativeError<ForListenableFutureK, Throwable>, ListenableFutureKApplicative {
  override fun <A> raiseError(e: Throwable): ListenableFutureK<A> =
    ListenableFutureK.raiseError(e)

  override fun <A> Kind<ForListenableFutureK, A>.attempt(): ListenableFutureK<Either<Throwable, A>> = fix().attempt()

  override fun <A> Kind<ForListenableFutureK, A>.handleErrorWith(f: (Throwable) -> Kind<ForListenableFutureK, A>): Kind<ForListenableFutureK, A> =
    fix().listenableFutureHandleErrorWith {
      f(it).fix()
    }
}

@extension
interface ListenableFutureKMonadError : MonadError<ForListenableFutureK, Throwable>, ListenableFutureKMonad, ListenableFutureKApplicativeError {
  override fun <A, B> ListenableFutureKOf<A>.map(f: (A) -> B): ListenableFutureK<B> =
    fix().map(f)

  override fun <A> raiseError(e: Throwable): ListenableFutureK<A> =
    ListenableFutureK.raiseError(e)

  override fun <A> ListenableFutureKOf<A>.handleErrorWith(f: (Throwable) -> ListenableFutureKOf<A>): ListenableFutureK<A> =
    fix().listenableFutureHandleErrorWith { f(it).fix() }
}

@extension
interface ListenableFutureKMonadThrow : MonadThrow<ForListenableFutureK>, ListenableFutureKMonadError

@extension
interface ListenableFutureKBracket : Bracket<ForListenableFutureK, Throwable>, ListenableFutureKMonadThrow {

  override fun <A, B> ListenableFutureKOf<A>.bracketCase(
    release: (A, ExitCase<Throwable>) -> ListenableFutureKOf<Unit>,
    use: (A) -> ListenableFutureKOf<B>
  ): ListenableFutureK<B> =
    fix().bracketCase({ use(it).fix() }, { a, e -> release(a, e).fix() })

}
