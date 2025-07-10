package kotlin.native.internal

internal class PriorityQueue<T>(
        initialCapacity: Int,
        private val comparator: Comparator<T>
) : AbstractMutableCollection<T>() {

    constructor(comparator: Comparator<T>) : this(DEFAULT_INITIAL_CAPACITY, comparator)

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 11

        fun <T : Comparable<T>> minimal(initialCapacity: Int = DEFAULT_INITIAL_CAPACITY): PriorityQueue<T> =
                PriorityQueue(initialCapacity) { a, b -> a.compareTo(b) }

        fun <T : Comparable<T>> maximal(initialCapacity: Int = DEFAULT_INITIAL_CAPACITY): PriorityQueue<T> =
                PriorityQueue(initialCapacity) { a, b -> b.compareTo(a) }
    }

    private val elements = ArrayList<T>(initialCapacity)

    private var modCount: Int = 0

    override val size: Int
        get() = elements.size

    override fun iterator(): MutableIterator<T> {
        var expectedModCount = modCount
        val ordered = ArrayList(elements).also { it.sortWith(comparator) }
        return object : MutableIterator<T> {
            private var nextIndex = 0
            override fun hasNext(): Boolean = nextIndex < ordered.size
            override fun next(): T {
                if (modCount != expectedModCount) throw ConcurrentModificationException()
                return ordered[nextIndex++]
            }

            override fun remove() {
                if (modCount != expectedModCount) throw ConcurrentModificationException()
                this@PriorityQueue.removeAt(nextIndex - 1)
                expectedModCount = modCount
            }
        }
    }

    override fun add(element: T): Boolean {
        modCount++
        elements.add(element)
        siftUp(elements.size - 1)
        return true
    }

    fun firstOrNull(): T? {
        return if (isEmpty()) null else elements[0]
    }

    fun first(): T {
        return firstOrNull() ?: throw NoSuchElementException("PriorityQueue is empty")
    }

    fun removeFirstOrNull(): T? {
        if (isEmpty()) return null
        return removeAt(0)
    }

    fun removeFirst(): T = removeFirstOrNull() ?: throw NoSuchElementException("PriorityQueue is empty")

    override fun clear() {
        modCount++
        elements.clear()
    }

    override fun remove(element: T): Boolean {
        val index = elements.indexOf(element)
        if (index == -1) return false
        removeAt(index)
        return true
    }

    // FIXME we can do better for removeAll/retainAll

    private fun removeAt(index: Int): T {
        modCount++
        val removedElement = elements[index]
        if (index == elements.size - 1) {
            elements.removeAt(index)
        } else {
            elements[index] = elements.removeAt(elements.size - 1)
            siftDown(index)
        }
        return removedElement
    }

    private fun leftChild(index: Int): Int = 2 * index + 1
    private fun rightChild(index: Int): Int = leftChild(index) + 1
    private fun parent(index: Int): Int = (index - 1) / 2

    private operator fun T.compareTo(other: T): Int = comparator.compare(this, other)

    private fun siftUp(start: Int) {
        val startElement = elements[start]
        var child = start
        while (child > 0) {
            val parent = parent(child)
            if (startElement >= elements[parent]) break
            elements[child] = elements[parent]
            child = parent
        }
        elements[child] = startElement
    }

    private fun siftDown(start: Int) {
        val startElement = elements[start]
        var parent = start
        val firstLeaf = elements.size / 2
        while (parent < firstLeaf) {
            val left = leftChild(parent)
            val right = rightChild(parent)

            val leastChild = if (right < elements.size && elements[right] < elements[left]) {
                right
            } else {
                left
            }

            if (startElement <= elements[leastChild]) break
            elements[parent] = elements[leastChild]
            parent = leastChild
        }
        elements[parent] = startElement
    }
}