package net.lax1dude.eaglercraft.v1_8.teavmcompat.plugin;

import org.teavm.model.BasicBlock;
import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.Instruction;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.MethodReference;
import org.teavm.model.Variable;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.PutFieldInstruction;

/** Changes only the completed-rewrite cache initializer in DFU's browser build. */
public final class DfuRewriteCacheTransformer implements ClassHolderTransformer {
    @Override
    public void transformClass(ClassHolder target, ClassHolderTransformerContext context) {
        if (!target.getName().equals("com.mojang.datafixers.types.Type")) return;
        MethodHolder initializer = target.getMethod(new MethodDescriptor("<clinit>", void.class));
        int replacements = 0;
        if (initializer != null && initializer.getProgram() != null) {
            for (BasicBlock block : initializer.getProgram().getBasicBlocks()) {
                for (Instruction instruction = block.getFirstInstruction(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (instruction instanceof PutFieldInstruction store
                            && store.getField().getClassName().equals(target.getName())
                            && store.getField().getFieldName().equals("REWRITE_CACHE")) {
                        Variable cache = initializer.getProgram().createVariable();
                        InvokeInstruction factory = new InvokeInstruction();
                        factory.setType(InvocationType.SPECIAL);
                        factory.setMethod(new MethodReference(
                            "net.lax1dude.eaglercraft.v1_8.teavmcompat.dfu.DfuRewriteCache",
                            "create", org.teavm.model.ValueType.object("java.util.Map")));
                        factory.setReceiver(cache);
                        instruction.insertPrevious(factory);
                        store.setValue(cache);
                        replacements++;
                    }
                }
            }
        }
        if (replacements != 1) {
            context.getDiagnostics().error(null, "Expected one DFU REWRITE_CACHE initializer, found " + replacements);
        }
    }
}
