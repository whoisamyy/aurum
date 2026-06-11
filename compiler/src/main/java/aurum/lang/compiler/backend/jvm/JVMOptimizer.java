package aurum.lang.compiler.backend.jvm;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StoreInstruction;

// this is quite primitive optimization that removes sequential `store load` and replaces it with `dup store`
public class JVMOptimizer implements CodeTransform {
    private CodeElement previousElement = null;
    private boolean changed = false;

    public boolean hasChanged() {
        return changed;
    }

    @Override
    public void accept(CodeBuilder builder, CodeElement element) {
        if (previousElement instanceof StoreInstruction store
                && element instanceof LoadInstruction load) {
            if (store.slot() == load.slot() && store.typeKind() == load.typeKind()) {
                if (store.typeKind().slotSize() == 1)
                    builder.dup();
                else if (store.typeKind().slotSize() == 2)
                    builder.dup2();
                builder.with(store);

                previousElement = null;
                changed = true;
                return;
            }
        }

        if (previousElement != null) {
            builder.with(previousElement);
        }

        if (element instanceof StoreInstruction) {
            previousElement = element;
        } else {
            builder.with(element);
            previousElement = null;
        }
    }

    @Override
    public void atEnd(CodeBuilder builder) {
        if (previousElement != null) {
            builder.with(previousElement);
        }
    }
}
