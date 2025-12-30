package lang.aurum.parsing.stages;

import lang.aurum.model.Field;
import lang.aurum.model.Method;
import lang.aurum.model.Type;

///**
// * @param <T> Context type
// */
//    <T extends AbstractParsingContext>
public abstract class ParsingStage {
    protected final ParsingContext parsingContext;

    protected FileContext currentFileContext = null;

    protected ParsingStage(ParsingContext parsingContext) {
        this.parsingContext = parsingContext;
    }

    public final void execute() {
        parsingContext.getFiles().forEach(fileContext -> {
            currentFileContext = fileContext;
            execute(fileContext);
            fileContext.getClasses().keySet().forEach(type -> {
                execute(type);
                afterType();
                for (Field field : type.fields()) {
                    execute(field);
                }
                afterFields();
                for (Method method : type.methods()) {
                    execute(method);
                }
                afterMethods();
            });
            afterFileContext(fileContext);
        });
        afterAll();
    }
    public void execute(ParsingContext parsingContext) {}
    public void execute(FileContext fileContext) {}
    public void execute(Type type) {}
    public void execute(Method method) {}
    public void execute(Field field) {}

    public void afterFields() {}
    public void afterMethods() {}
    public void afterType() {}
    public void afterFileContext(FileContext fileContext) {}
    public void afterAll() {}
}
