package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.stages.ProcessedType
import aurum.lang.compiler.frontend.stages.ProcessedTypes
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.model.Method
import aurum.lang.model.Type
import aurum.lang.model.attribute.Associativity
import aurum.lang.model.attribute.CustomOperator
import aurum.lang.model.attribute.Operator

class OperatorResolvingStage : Stage() {
    val processedTypes = input<ProcessedTypes>()

    override fun execute() {
        processedTypes.get()
            .map(ProcessedType::type)
            .map(Type::methods)
            .flatMap { it.toList() }
            .forEach(::processMethod)
    }

    private fun processMethod(method: Method): Method {
        if (method !is MutableMethod) return method

        if (method.attributes.contains<OperatorTemplate>()) {
            method.attributes -= OperatorTemplate

            method.attributes += CustomOperator(
                method.name,
                Operator.DEFAULT_PRECEDENCE,
                Associativity.LEFT_TO_RIGHT,
                if (method.isStatic)
                    if (method.parameters.size == 2) true
                    else if (method.parameters.size == 1) false
                    else error("Static operator overloads must have one or two parameters")
                else
                    if (method.parameters.size == 1) true
                    else if (method.parameters.isEmpty()) false
                    else error("Non static operator overloads must have one parameter or none")
            )
        }

        return method
    }
}