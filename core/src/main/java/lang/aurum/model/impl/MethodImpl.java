package lang.aurum.model.impl;

import lang.aurum.model.*;

import java.lang.reflect.AccessFlag;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public record MethodImpl(
        Type owner,
        String name,
        Type returnType,
        Parameter[] parameters,
        Type[] exceptions,
        AccessFlag[] accessFlags,
        Optional<TypeParameter[]> typeParameters,
        Optional<TypeArgument[]> typeArguments,
        Attribute[] attributes
) implements Method {
    public Method withTypeArguments(TypeArgument[] typeArguments) {
        var typeMap = getTypeMap(typeArguments);
        // Return type, parameters, exceptions
        Type newReturnType = Utils.replaceTemplates(this.returnType, typeMap);

        Parameter[] oldParams = this.parameters;
        Parameter[] newParams = new Parameter[oldParams.length];
        for (int j = 0; j < oldParams.length; j++) {
            var p = oldParams[j];
            Type newParamType = Utils.replaceTemplates(p.type(), typeMap);
            newParams[j] = new ParameterImpl(p.name(), newParamType, p.attributes());
        }

        Type[] oldEx = this.exceptions;
        Type[] newEx = new Type[oldEx.length];
        for (int j = 0; j < oldEx.length; j++) newEx[j] = Utils.replaceTemplates(oldEx[j], typeMap);

        // Method type parameters bounds may reference class-level templates; update their bounds
        Optional<TypeParameter[]> newMethodTypeParams = this.typeParameters.map(mTPs -> {
            TypeParameter[] res = new TypeParameter[mTPs.length];
            for (int j = 0; j < mTPs.length; j++) {
                var tp = mTPs[j];
                Type newBound = Utils.replaceTemplates(tp.bound(), typeMap);
                res[j] = new TypeParameterImpl(tp.name(), newBound);
            }
            return res;
        });

        return new MethodImpl(
                this.owner,
                this.name,
                newReturnType,
                newParams,
                newEx,
                this.accessFlags,
                newMethodTypeParams,
                this.typeArguments,
                this.attributes
        );
    }

    @Override
    public Method withTypeArguments(Type[] typeArguments) {
        if (typeArguments == null) return this;
        if (typeParameters.isEmpty()) return this;
        TypeParameter[] tps = typeParameters.get();
        int n = Math.min(tps.length, typeArguments.length);
        TypeArgument[] args = new TypeArgument[n];
        for (int i = 0; i < n; i++) {
            args[i] = new TypeArgumentImpl(tps[i].name(), typeArguments[i]);
        }
        return withTypeArguments(args);
    }

    private Map<String, Type> getTypeMap(TypeArgument[] typeArguments) {
        if (typeParameters.isEmpty())
            return new HashMap<>();
        TypeParameter[] tps = typeParameters.get();
        int n = Math.min(tps.length, typeArguments.length);
        Map<String, Type> typeMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            typeMap.put(tps[i].name(), typeArguments[i].bound());
        }
        return typeMap;
    }
}
