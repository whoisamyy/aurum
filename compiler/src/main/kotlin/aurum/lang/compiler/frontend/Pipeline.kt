package aurum.lang.compiler.frontend

import aurum.lang.compiler.frontend.stages.Artifact
import aurum.lang.compiler.frontend.stages.CompilationContext
import aurum.lang.compiler.frontend.stages.Property
import aurum.lang.compiler.frontend.stages.Stage
import kotlin.reflect.KClass

object Pipeline {
    internal val stages = LinkedHashSet<Stage>()
    val stagesByClass = mutableMapOf<KClass<out Stage>, Stage>()
    private val context = CompilationContext()

    @JvmStatic
    fun registerStage(stage: Stage) {
        stage.also {
            stages.add(it)
            stagesByClass[it::class] = it
        }
    }

    @JvmStatic
    fun registerStage(stage: Class<out Stage>) {
        stage.getConstructor().newInstance().also {
            stages.add(it)
            stagesByClass[stage.kotlin] = it
        }
    }

    private fun sortStages(unsorted: List<Stage>): List<Stage> {
        val sorted = mutableListOf<Stage>()

        val producers = mutableMapOf<KClass<out Artifact>, Stage>()
        for (stage in unsorted) {
            for (dep in stage.outputs)
                producers[dep.type] = stage
//                producers.computeIfAbsent(dep.type) { _ -> mutableListOf() }
//                    .add(stage)
        }

        val adjacent = unsorted.associateWith { mutableListOf<Stage>() }
        val inDegree = unsorted.associateWith { 0 }.toMutableMap()

        for (stage in unsorted) {
            val allDependencies = mutableSetOf<Stage>()

            val unprocessedInputs = mutableListOf<Property<*>>()
            unprocessedInputs.addAll(stage.inputs)

            for (dependency in stage.dependsOn) {
                unsorted
                    .groupBy { s -> s::class }
                    .map { kv -> kv.key to kv.value.first() }
                    .toMap()[dependency]?.let { s ->
                        val filtered = s.outputs
                            .filter { o -> o.type in stage.inputs.map(Property<*>::type) }
                        val size = filtered.size

                        unprocessedInputs.removeIf { p -> p.type in filtered.map(Property<*>::type) }

                        if (size != 0) {
                            allDependencies += s
                        }
                    }
            }

            for (input in unprocessedInputs) {
                if (producers[input.type] != null)
                    allDependencies += producers[input.type]!!
//                for (producer in producers[input.type] ?: emptyList()) {
//                    if (stage != producer) {
//                        allDependencies += producer
//                        producers[input.type]!!.remove(producer)
//                        break
//                    }
//                }
            }

            allDependencies += unsorted.filter { it::class in stage.dependsOn }

            for (dep in allDependencies) {
                adjacent[dep]!! += stage
                inDegree[stage] = inDegree[stage]!! + 1
            }
        }

        val queue = java.util.ArrayDeque<Stage>()
        queue.addAll(unsorted.filter { inDegree[it] == 0 })

        while (queue.isNotEmpty()) {
            val current = queue.pop()
            sorted += current

            for (neighbor in adjacent[current]!!) {
                inDegree[neighbor] = inDegree[neighbor]!! - 1
                if (inDegree[neighbor] == 0) {
                    queue += neighbor
                }
            }
        }

        if (sorted.size != unsorted.size)
            error("todo. cyclic dependency")

        wireStages(sorted)

        return sorted
    }

    private fun wireStages(sorted: MutableList<Stage>) {
        for ((i, element) in sorted.withIndex()) {
            val current = element

            current.outputs.forEach { curOut ->
                @Suppress("UNCHECKED_CAST")
                sorted.drop(i + 1).flatMap(Stage::inputs).filter { it.type == curOut.type }
                    .forEach {
                        it.getter = { curOut.get() } as (() -> Nothing)?
                    }
            }
        }
    }

    fun buildPlan(): List<Stage> {
        return sortStages(stages.toList())
    }

    @JvmStatic
    fun run() {
        val plan = buildPlan()
        plan.forEach { stage ->
            stage.execute()
        }
    }

    inline fun <reified T : Stage> getStage() = stagesByClass[T::class] as? T
}