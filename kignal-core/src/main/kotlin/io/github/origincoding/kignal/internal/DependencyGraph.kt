package io.github.origincoding.kignal.internal

internal interface Producer {
    val consumers: MutableSet<Consumer>
}

internal sealed interface Consumer {
    val dependencies: MutableSet<Producer>
}

internal object DependencyGraph {
    fun link(producer: Producer, consumer: Consumer) {
        if (consumer.dependencies.add(producer)) {
            producer.consumers.add(consumer)
        }
    }

    fun clearDependencies(consumer: Consumer) {
        consumer.dependencies.forEach { producer ->
            producer.consumers.remove(consumer)
        }
        consumer.dependencies.clear()
    }
}
