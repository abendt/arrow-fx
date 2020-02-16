package arrow.streams.extensions

import arrow.Kind
import arrow.extension
import arrow.fx.fix
import arrow.streams.ForStream
import arrow.streams.Stream
import arrow.streams.StreamOf
import arrow.streams.fix
import arrow.streams.flatMap
import arrow.typeclasses.Applicative
import arrow.typeclasses.Apply
import arrow.streams.map as smap
import arrow.typeclasses.Functor

@extension
interface StreamFunctor : Functor<ForStream> {
  override fun <A, B> StreamOf<A>.map(f: (A) -> B): Stream<B> =
    fix().smap(f)
}

@extension
interface StreamApply : Apply<ForStream> {
  override fun <A, B> StreamOf<A>.map(f: (A) -> B): Stream<B> =
    fix().smap(f)

  override fun <A, B> StreamOf<A>.ap(ff: StreamOf<(A) -> B>): Stream<B> =
    fix().flatMap { a -> ff.fix().map { it(a) } }

  override fun <A, B> Kind<ForStream, A>.lazyAp(ff: () -> Kind<ForStream, (A) -> B>): Kind<ForStream, B> =
    fix().flatMap { a -> ff().map { f -> f(a) } }
}


@extension
interface IOApplicative : Applicative<ForStream>, StreamApply {
  override fun <A, B> StreamOf<A>.map(f: (A) -> B): Stream<B> =
    fix().smap(f)

  override fun <A> just(a: A): Stream<A> =
    Stream.just(a)
}