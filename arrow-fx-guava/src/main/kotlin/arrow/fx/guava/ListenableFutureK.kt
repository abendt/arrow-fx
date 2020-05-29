package arrow.fx.guava

import arrow.Kind
import arrow.core.Either
import arrow.core.Eval
import arrow.core.left
import arrow.core.right
import arrow.fx.typeclasses.ExitCase
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking

class ForListenableFutureK private constructor()

typealias ListenableFutureKOf<A> = arrow.Kind<ForListenableFutureK, A>

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <A> ListenableFutureKOf<A>.fix(): ListenableFutureK<A> =
  this as ListenableFutureK<A>

fun <A> ListenableFuture<A>.k(): ListenableFutureK<A> = ListenableFutureK(this)

@Suppress("UNCHECKED_CAST")
fun <A> ListenableFutureKOf<A>.value(): ListenableFuture<A> =
  this.fix().value as ListenableFuture<A>

data class ListenableFutureK<out A>(val value: ListenableFuture<out A>) : ListenableFutureKOf<A> {

  fun <B> map(f: (A) -> B): ListenableFutureK<B> =
    runBlocking {
      future {
        f(this@ListenableFutureK.value.await())
      }.k()
    }

  fun <B> ap(ff: ListenableFutureK<(A) -> B>): ListenableFutureK<B> =
    runBlocking {
      future {
        val func = ff.value.await()
        func(this@ListenableFutureK.value.await())
      }.k()
    }

  fun <B> flatMap(f: (A) -> ListenableFutureK<B>): ListenableFutureK<B> =
    runBlocking {
      f(this@ListenableFutureK.value.await())
    }

  fun <B> foldLeft(b: B, f: (B, A) -> B): B =
    runBlocking {
      f(b, value.await())
    }

  fun <B> foldRight(lb: Eval<B>, f: (A, Eval<B>) -> Eval<B>): Eval<B> =
    Eval.later { f(value.get(), lb).value() }

  fun attempt(): ListenableFutureK<Either<Throwable, A>> =
    runBlocking {
      future {
        try {
          value.await().right()
        } catch (e: Exception) {
          e.left()
        }
      }.k()
    }

  fun <B> bracketCase(
    use: (A) -> ListenableFutureK<B>,
    release: (A, ExitCase<Throwable>) -> ListenableFutureK<Unit>
  ): ListenableFutureK<B> = runBlocking {
    future {

      val a = this@ListenableFutureK.value.await()

      try {
        val result = use(a).value.await()
        release(a, ExitCase.Completed)

        result
      } catch (e: Exception) {
        release(a, ExitCase.Error(e))

        throw e
      }

    }.k()
  }


  companion object {

    fun <A> just(a: A): ListenableFutureK<A> =
      Futures.immediateFuture(a).k()

    fun <A> raiseError(e: Throwable): ListenableFutureK<A> =
      Futures.immediateFailedFuture<A>(e).k()

    tailrec fun <A, B> tailRecM(a: A, f: (A) -> Kind<ForListenableFutureK, Either<A, B>>): ListenableFutureK<B> {

      val either = f(a).fix().value.get()

      return when (either) {
        is Either.Left -> tailRecM(either.a, f)
        is Either.Right -> just(either.b)
      }
    }
  }
}

fun <A> ListenableFutureK<A>.listenableFutureHandleErrorWith(f: (Throwable) -> ListenableFutureK<A>): ListenableFutureK<A> =
  runBlocking {
    future {
      try {
        this@listenableFutureHandleErrorWith.value.await()
      } catch (e: Throwable) {
        f(e).value.await()
      }
    }.k()
  }
