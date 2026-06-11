package aurum.lang.compiler.frontend.stages.typeresolving

import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.TypeResolverFactory

class TypeResolverInjectionStage : Stage() {
    val resolver = output<TypeResolverFactory<*>>()

    override fun execute() {
        resolver.set(TypeResolverFactory(::SimpleTypeResolver))
    }
}