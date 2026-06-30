package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.attribute.ConstantPoolAttribute
import aurum.lang.compiler.backend.ir.IRCompiler
import aurum.lang.compiler.backend.ir.Value
import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.attribute.get
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.AurumFile
import aurum.lang.compiler.frontend.stages.ProcessedTypes
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.ir.*
import aurum.lang.model.Method
import aurum.lang.model.Parameter
import aurum.lang.model.Types
import aurum.lang.model.attribute.CustomOperator

class DefaultParametersProcessingStage : Stage() {
    val processedTypes = input<ProcessedTypes>()

    init {
        dependsOn<PrimaryConstructorResolvingStage>()
        dependsOn<OperatorResolvingStage>()
    }

    override fun execute() {
        processedTypes.get()
            .map { it.file to it.type.methods() }
            .forEach { (file, methods) ->
                methods.forEach { method ->
                    processMethod(file, method)
                }
            }
    }

    private fun processMethod(file: AurumFile, method: Method): Method {
        val owner = method.owner()
        if (method !is MutableMethod || owner !is MutableType) return method

        if (method.attributes.contains<CustomOperator>()) {
            return method
        }

        val defaultParams = method.parameters.dropWhile { !it.attributes().contains<DefaultValueAttribute>() }
        if (defaultParams.any { !it.attributes().contains<DefaultValueAttribute>() })
            error("Default parameter values should come only after non-default parameters")
        // 1  2  3
        // 1  2
        // 1
        //
        for (i in 1..defaultParams.size) {
            val newAttributes = method.attributes.toMutableList()
            val cbAttribute = newAttributes.find { it is CodeBlockAttribute } as CodeBlockAttribute
            newAttributes.removeIf { it is CodeBlockAttribute }
            val newMethod = MutableMethod(
                owner,
                method.name,
                method.returnType,
                method.parameters.dropLast(i).toMutableList(),
                method.exceptions,
                method.accessFlags,
                method.typeParameters,
                method.typeArguments,
                newAttributes
            )
            owner.methods += newMethod

            newMethod.attributes += CodeAttribute(
                buildList {
                    val args: MutableList<RValue> = newMethod.parameters
                        .map(Parameter::name)
                        .map { Reference.Named(it) }
                        .toMutableList()

                    val cp = owner.attributes.get<ConstantPoolAttribute>()!!.constantPool
                    val compiler = IRCompiler(
                        newMethod,
                        cbAttribute.typeResolver,
                        cp,
                        file.imports
                    )

                    args += defaultParams.takeLast(i)
                        .map { it.attributes().get<DefaultValueAttribute>()!! }
                        .map { it.value }
                        .map(compiler::compileExpression)
                        .map(Value::ref)

                    addAll(compiler.instructionList)
                    val target = Reference.Named("${method.name}#returnValue$${newMethod.hashCode().toHexString()}")
                    if (method.isStatic) {
                        add(Call(
                            target,
                            cp.getReference(method),
                            args
                        ))
                    } else {
                        add(CallMethod(
                            target,
                            Reference.This,
                            cp.getReference(method),
                            args
                        ))
                    }

                    if (method.returnType != Types.VOID)
                        add(Return(target))
                    else add(Return())
                }.toMutableList()
            )
        }

        return method
    }
}