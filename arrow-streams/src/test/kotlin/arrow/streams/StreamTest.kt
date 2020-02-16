package arrow.streams

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamTest {

    @Test
    fun forEach() {
        val latch = CountDownLatch(1)
        var acc = ""
        val mapper = { it: Int -> 20 - it }
        val a = (0 until 10).toList()
        Stream.fromArray(a)
            .map(mapper)
            .forEach(onNext = { acc += it }, onComplete = { latch.countDown() })

        latch.await(1, TimeUnit.SECONDS)
        assertEquals(a.map(mapper).joinToString(""), acc)
    }

    @Test
    fun map() {
        var acc = ""
        val mapper = { it: Int -> 20 - it }
        val a = (0 until 10).toList()
        Stream.fromArray(a)
            .map(mapper)
            .forEachSync(onNext = { acc += it })

        assertEquals(a.map(mapper).joinToString(""), acc)
    }

    @Test
    fun flatten() {
        var acc = ""
        val mapper = { it: Int -> listOf(it * 10 + 1, it * 10 + 2, it * 10 + 3) }
        val a = listOf(1, 2, 3)
        Stream.fromArray(a)
            .map { Stream.fromArray(mapper(it)) }
            .concatAll()
            .forEachSync(onNext = { acc += it })

        assertEquals(a.map(mapper).flatten().joinToString(""), acc)
    }

    @Test
    fun flatMap() {
        var acc = ""
        val mapper = { it: Int -> listOf(it * 10 + 1, it * 10 + 2, it * 10 + 3) }
        val a = listOf(1, 2, 3)
        Stream.fromArray(a)
            .flatMap { Stream.fromArray(mapper(it)) }
            .forEachSync(onNext = { acc += it })

        assertEquals(a.flatMap(mapper).joinToString(""), acc)
    }

    @Test
    fun flatMap2() {
        var acc = ""
        val mapper = { it: Int -> listOf(it * 10 + 1, it * 10 + 2, it * 10 + 3) }
        val a = listOf<Int>()
        Stream.fromArray(a)
            .flatMap { Stream.fromArray(mapper(it)) }
            .forEachSync(onNext = { acc += it })

        assertEquals(a.flatMap(mapper).joinToString(""), acc)
    }

    @Test
    fun concat() {
        var acc = ""
        val a = listOf(1, 2, 3)
        val b = listOf(4, 5, 6)
        Stream.fromArray(a)
            .concat(Stream.fromArray(b))
            .forEachSync(onNext = { acc += it })

        assertEquals((a + b).joinToString(""), acc)
    }

    @Test
    fun concat2() {
        var acc = ""
        val a = listOf<Int>()
        val b = listOf(4, 5, 6)
        Stream.fromArray(a)
            .concat(Stream.fromArray(b))
            .forEachSync(onNext = { acc += it })

        assertEquals((a + b).joinToString(""), acc)
    }

    @Test
    fun merge() {
        var acc = ""
        val a = listOf(1, 2, 3, 7, 8, 9)
        val b = listOf(4, 5, 6)
        Stream.fromArray(a)
            .merge(Stream.fromArray(b))
            .forEachSync(onNext = { acc += it })

        assertEquals("142536789", acc)
    }

    @Test
    fun mergeAll() {
    }

    @Test
    fun concatAll() {
    }

    @Test
    fun skipUntil() {
//
//        Stream.seq((0..100).toList(), 0.milliseconds, 100.milliseconds)
//            .flatMap {  }
//            .skipUntil {  }
    }
}