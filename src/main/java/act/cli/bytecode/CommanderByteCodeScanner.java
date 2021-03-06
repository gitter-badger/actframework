package act.cli.bytecode;

import act.Act;
import act.ActComponent;
import act.app.AppByteCodeScannerBase;
import act.asm.*;
import act.cli.CliDispatcher;
import act.cli.meta.*;
import act.cli.view.CliView;
import act.util.AsmTypes;
import act.util.ByteCodeVisitor;
import act.util.PropertySpec;
import org.osgl.$;
import org.osgl.logging.L;
import org.osgl.logging.Logger;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Scan Commander class bytecode
 */
@ActComponent
public class CommanderByteCodeScanner extends AppByteCodeScannerBase {

    private final static Logger logger = L.get(CommanderByteCodeScanner.class);
    private CliDispatcher dispatcher;
    private CommanderClassMetaInfo classInfo;
    private volatile CommanderClassMetaInfoManager classInfoBase;

    public CommanderByteCodeScanner() {
    }

    @Override
    protected void reset(String className) {
        classInfo = new CommanderClassMetaInfo();
    }

    @Override
    protected boolean shouldScan(String className) {
        return true;
    }

    @Override
    protected void onAppSet() {
        dispatcher = app().cliDispatcher();
    }

    @Override
    public ByteCodeVisitor byteCodeVisitor() {
        return new _ByteCodeVisitor();
    }

    @Override
    public void scanFinished(String className) {
        classInfoBase().registerCommanderMetaInfo(classInfo);
    }

    private CommanderClassMetaInfoManager classInfoBase() {
        if (null == classInfoBase) {
            synchronized (this) {
                if (null == classInfoBase) {
                    classInfoBase = app().classLoader().commanderClassMetaInfoManager();
                }
            }
        }
        return classInfoBase;
    }

    private class _ByteCodeVisitor extends ByteCodeVisitor {
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            classInfo.className(name);
            Type superType = Type.getObjectType(superName);
            classInfo.superType(superType);
            if (isAbstract(access)) {
                classInfo.setAbstract();
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            FieldVisitor fv = super.visitField(access, name, desc, signature, value);
            Type type = Type.getType(desc);
            return new CommanderFieldVisitor(fv, name, type);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (!isEligibleMethod(access, name, desc)) {
                return mv;
            }
            return new CommandMethodVisitor(mv, access, name, desc, signature, exceptions);
        }

        @Override
        public void visitEnd() {
            for (CommandMethodMetaInfo commandMethodMetaInfo : classInfo.commandList()) {
                dispatcher.registerCommandHandler(commandMethodMetaInfo.commandName(), commandMethodMetaInfo, classInfo);
            }
            super.visitEnd();
        }

        private boolean isEligibleMethod(int access, String name, String desc) {
            return isPublic(access) && !isAbstract(access) && !isConstructor(name);
        }

        private class CommanderFieldVisitor extends FieldVisitor implements Opcodes {
            private String fieldName;
            private Type type;

            public CommanderFieldVisitor(FieldVisitor fv, String fieldName, Type type) {
                super(ASM5, fv);
                this.fieldName = fieldName;
                this.type = type;
            }

            @Override
            public void visitAttribute(Attribute attr) {
                super.visitAttribute(attr);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                AnnotationVisitor av = super.visitAnnotation(desc, visible);
                Type type = Type.getType(desc);
                boolean isOptional = $.eq(type, AsmTypes.OPTIONAL.asmType());
                boolean isRequired = $.eq(type, AsmTypes.REQUIRED.asmType());
                if (isOptional || isRequired) {
                    return new FieldOptionAnnotationVisitor(av, isOptional, fieldName, this.type);
                }
                return av;
            }

            private class FieldOptionAnnotationVisitor extends OptionAnnotationVisitorBase implements Opcodes {
                public FieldOptionAnnotationVisitor(AnnotationVisitor av, boolean optional, String fieldName, Type type) {
                    super(av, optional);
                    this.OptionAnnoInfo = new FieldOptionAnnoInfo(fieldName, type, optional);
                }

                @Override
                public void visitEnd2() {
                    classInfo.addFieldOptionAnnotationInfo((FieldOptionAnnoInfo) OptionAnnoInfo);
                }
            }

        }

        private class CommandMethodVisitor extends MethodVisitor implements Opcodes {

            private String methodName;
            private int access;
            private String desc;
            private String signature;
            private boolean requireScan;
            private CommandMethodMetaInfo methodInfo;
            private Map<Integer, ParamOptionAnnoInfo> optionAnnoInfo = C.newMap();
            private BitSet contextInfo = new BitSet();
            private boolean isStatic;

            private int paramIdShift = 0;

            CommandMethodVisitor(MethodVisitor mv, int access, String methodName, String desc, String signature, String[] exceptions) {
                super(ASM5, mv);
                this.access = access;
                this.methodName = methodName;
                this.desc = desc;
                this.signature = signature;
                this.isStatic = AsmTypes.isStatic(access);
                methodInfo = new CommandMethodMetaInfo(classInfo);
                if (isStatic) {
                    methodInfo.invokeStaticMethod();
                } else {
                    methodInfo.invokeInstanceMethod();
                }
                methodInfo.methodName(methodName);
                methodInfo.returnType(Type.getReturnType(desc));
                Type[] argTypes = Type.getArgumentTypes(desc);
                for (int i = 0; i < argTypes.length; ++i) {
                    Type type = argTypes[i];
                    CommandParamMetaInfo param = new CommandParamMetaInfo().type(type);
                    methodInfo.addParam(param);
                }
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                if (!"this".equals(name)) {
                    int paramId = index;
                    if (null == methodInfo) {
                        methodInfo = new CommandMethodMetaInfo(classInfo);
                    }
                    if (!isStatic) {
                        paramId--;
                    }
                    paramId -= paramIdShift;
                    if (paramId < methodInfo.paramCount()) {
                        CommandParamMetaInfo param = methodInfo.param(paramId);
                        param.name(name);
                        if (Type.getType(long.class).equals(param.type()) || Type.getType(double.class).equals(param.type())) {
                            paramIdShift++;
                        }
                    }
                }
                super.visitLocalVariable(name, desc, signature, start, end, index);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                AnnotationVisitor av = super.visitAnnotation(desc, visible);
                Type type = Type.getType(desc);
                if ($.eq(AsmTypes.COMMAND.asmType(), type)) {
                    markRequireScan();
                    return new AnnotationVisitor(ASM5, av) {
                        @Override
                        public void visit(String name, Object value) {
                            super.visit(name, value);
                            if (S.eq("value", name) || S.eq("name", name)) {
                                String commandName = S.string(value);
                                if (S.empty(commandName)) {
                                    throw E.unexpected("command name cannot be empty");
                                }
                                methodInfo.commandName(commandName);
                            } else if (S.eq("help", name)) {
                                methodInfo.helpMsg(S.string(value));
                            }
                        }

                        @Override
                        public void visitEnum(String name, String desc, String value) {
                            super.visitEnum(name, desc, value);
                            if ("mode".equals(name)) {
                                methodInfo.mode(Act.Mode.valueOf(value));
                            }
                        }

                        @Override
                        public void visitEnd() {
                            if (S.blank(methodInfo.commandName())) {
                                throw new IllegalArgumentException("command name not defined");
                            }
                            super.visitEnd();
                        }
                    };
                } else if ($.eq(AsmTypes.CSV_VIEW.asmType(), type)) {
                    methodInfo.view(CliView.CSV);
                    return super.visitAnnotation(desc, visible);
                } else if ($.eq(AsmTypes.TREE_VIEW.asmType(), type)) {
                    methodInfo.view(CliView.TREE);
                    return super.visitAnnotation(desc, visible);
                } else if ($.eq(AsmTypes.TABLE_VIEW.asmType(), type)) {
                    methodInfo.view(CliView.TABLE);
                    return super.visitAnnotation(desc, visible);
                } else if ($.eq(AsmTypes.JSON_VIEW.asmType(), type)) {
                    methodInfo.view(CliView.JSON);
                    return super.visitAnnotation(desc, visible);
                } else if ($.eq(AsmTypes.PROPERTY_SPEC.asmType(), type)) {
                    final PropertySpec.MetaInfo propSpec = new PropertySpec.MetaInfo();
                    methodInfo.propertySpec(propSpec);
                    return new AnnotationVisitor(ASM5, av) {
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            AnnotationVisitor av0 = super.visitArray(name);
                            if (S.eq("value", name)) {
                                return new AnnotationVisitor(ASM5, av0) {
                                    @Override
                                    public void visit(String name, Object value) {
                                        super.visit(name, value);
                                        propSpec.onValue(S.string(value));
                                    }
                                };
                            } else if (S.eq("cli", name)) {
                                return new AnnotationVisitor(ASM5, av0) {
                                    @Override
                                    public void visit(String name, Object value) {
                                        super.visit(name, value);
                                        propSpec.onCli(S.string(value));
                                    }
                                };
                            } else if (S.eq("http", name)) {
                                return new AnnotationVisitor(ASM5, av0) {
                                    @Override
                                    public void visit(String name, Object value) {
                                        super.visit(name, value);
                                        propSpec.onHttp(S.string(value));
                                    }
                                };
                            } else {
                                return av0;
                            }
                        }
                    };
                }
                return av;
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int paramIndex, String desc, boolean visible) {
                AnnotationVisitor av = super.visitParameterAnnotation(paramIndex, desc, visible);
                Type type = Type.getType(desc);
                boolean isOptional = $.eq(type, AsmTypes.OPTIONAL.asmType());
                boolean isRequired = $.eq(type, AsmTypes.REQUIRED.asmType());
                if (isOptional || isRequired) {
                    if (optionAnnoInfo.containsKey(paramIndex)) {
                        throw E.unexpected("Option annotation already found on index %s", paramIndex);
                    }
                    return new ParamOptionAnnotationVisitor(av, paramIndex, isOptional);
                } else if ($.eq(type, AsmTypes.CONTEXT.asmType())) {
                    contextInfo.set(paramIndex);
                    return av;
                } else {
                    return av;
                }
            }

            @Override
            public void visitEnd() {
                if (!requireScan()) {
                    super.visitEnd();
                    return;
                }
                classInfo.addCommand(methodInfo);
                Type[] argTypes = Type.getArgumentTypes(desc);
                for (int i = 0; i < argTypes.length; ++i) {
                    CommandParamMetaInfo param = methodInfo.param(i);
                    ParamOptionAnnoInfo option = optionAnnoInfo.get(i);
                    if (contextInfo.get(i)) {
                        param.setContext();
                    }
                    if (null != option) {
                        param.optionInfo(option);
                        methodInfo.addLead(option.lead1());
                        methodInfo.addLead(option.lead2());
                    }
                }
                super.visitEnd();
            }

            private void markRequireScan() {
                this.requireScan = true;
            }

            private boolean requireScan() {
                return requireScan;
            }

            private class ParamOptionAnnotationVisitor extends OptionAnnotationVisitorBase implements Opcodes {
                protected int index;

                public ParamOptionAnnotationVisitor(AnnotationVisitor av, int index, boolean optional) {
                    super(av, optional);
                    this.index = index;
                    this.OptionAnnoInfo = new ParamOptionAnnoInfo(index, optional);
                }

                @Override
                public void visitEnd2() {
                    optionAnnoInfo.put(index, (ParamOptionAnnoInfo) OptionAnnoInfo);
                }
            }

        }
    }

    private static class OptionAnnotationVisitorBase extends AnnotationVisitor implements Opcodes {
        protected List<String> specs = C.newList();
        protected OptionAnnoInfoBase OptionAnnoInfo;

        public OptionAnnotationVisitorBase(AnnotationVisitor av, boolean optional) {
            super(ASM5, av);
            // sub class to init "info" field here
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av = super.visitArray(name);
            if (S.eq("lead", name)) {
                return new AnnotationVisitor(ASM5, av) {
                    @Override
                    public void visit(String name, Object value) {
                        super.visit(name, value);
                        specs.add((String) value);
                    }
                };
            }
            return av;
        }

        @Override
        public void visit(String name, Object value) {
            super.visit(name, value);
            if (S.eq("group", name)) {
                OptionAnnoInfo.group((String) value);
            } else if (S.eq("defVal", name)) {
                OptionAnnoInfo.defVal((String) value);
            } else if (S.eq("value", name) || S.eq("help", name)) {
                OptionAnnoInfo.help((String) value);
            }
        }

        @Override
        public void visitEnd() {
            if (!specs.isEmpty()) {
                OptionAnnoInfo.spec(specs.toArray(new String[specs.size()]));
            }
            visitEnd2();
            super.visitEnd();
        }

        protected void visitEnd2() {
            // ...
        }
    }


}
