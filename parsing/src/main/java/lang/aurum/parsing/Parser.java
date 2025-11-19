package lang.aurum.parsing;

import kotlin.Pair;
import lang.aurum.ir.IrFile;
import lang.aurum.model.Type;
import lang.aurum.parsing.model.MutableType;
import lang.aurum.parsing.stages.FileContext;
import lang.aurum.parsing.stages.ParsingContext;
import lang.aurum.parsing.stages.Pipeline;

import java.nio.file.Path;
import java.util.*;

public final class Parser {
    public static void main(String[] args) {
        LinkedHashSet<FileContext> files = new LinkedHashSet<>();
        Path workDir = Path.of(args[0]);
        ParsingContext parsingContext = new ParsingContext(workDir, Set.of(), Set.of(), files);
        Path resolved = workDir.resolve(args[1]);
        files.add(FileContext.Companion.ofPath(parsingContext, resolved));
        new Pipeline().execute(parsingContext);

        parsingContext.getFiles().forEach(file -> {
            List<Type> types = new ArrayList<>();
            types.add(file.fileClass);
            types.addAll(file.getClasses().keySet().stream().toList());
            new VerboseIrFileWriter(
                    file.getConstantPool(),
                    new IrFile(
                            types,
                            file.getMembers().stream()
                                .map(Pair::getFirst).toList()
                    )
            ).write(Path.of(resolved + ".aur"));
        });
    }
}
