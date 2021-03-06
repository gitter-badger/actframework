package act.util;

import act.app.event.AppEventId;
import act.asm.AnnotationVisitor;
import act.asm.MethodVisitor;
import act.asm.Type;
import org.osgl.$;
import org.osgl.util.S;

import javax.inject.Singleton;

/**
 * If a certain non-abstract/public class extends {@link SingletonBase} ensure the class
 * has {@link javax.inject.Singleton} annotation, and generate {@link SingletonBase#instance()}
 * implementation
 */
public class SingletonEnhancer extends AppByteCodeEnhancer<SingletonEnhancer> {

    private boolean shouldEnhance = false;
    private boolean shouldAddAnnotation = true;
    private String typeName;
    private String className;

    public SingletonEnhancer() {
        super($.F.<String>yes());
    }

    @Override
    protected Class<SingletonEnhancer> subClass() {
        return SingletonEnhancer.class;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if (SingletonBase.class.getName().equals(Type.getObjectType(superName).getClassName())) {
            if (isAbstract(access)) {
                logger.warn("SingletonBase sub class is abstract: %s", name);
                return;
            } else if (!isPublic(access)) {
                logger.warn("SingletonBase sub class is not public: %s", name);
                return;
            }
            shouldEnhance = true;
            this.typeName = name;
            this.className = Type.getObjectType(typeName).getClassName();
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        Type type = Type.getType(desc);
        if (Singleton.class.getName().equals(type.getClassName())) {
            shouldAddAnnotation = false;
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visitEnd() {
        if (shouldEnhance) {
            addAnnotationIfNeeded();
            addInstanceMethod();
        }
        super.visitEnd();
    }

    private void addAnnotationIfNeeded() {
        if (shouldAddAnnotation) {
            AnnotationVisitor av = super.visitAnnotation(Type.getType(Singleton.class).getDescriptor(), true);
            av.visitEnd();
            scheduleSingletonRegistering();
        }
    }

    private void addInstanceMethod() {
        MethodVisitor mv = super.visitMethod(ACC_PUBLIC + ACC_STATIC, "instance", "()Ljava/lang/Object;", "<T:Ljava/lang/Object;>()TT;", null);
        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, "act/app/App", "instance", "()Lact/app/App;", false);
        mv.visitLdcInsn(Type.getType(instanceTypeDesc()));
        mv.visitMethodInsn(INVOKEVIRTUAL, "act/app/App", "singleton", "(Ljava/lang/Class;)Ljava/lang/Object;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
    }

    private String instanceMethodReturnTypeDesc() {
        return S.fmt("()L%s;", typeName);
    }

    private String instanceTypeDesc() {
        return S.fmt("L%s;", typeName);
    }

    private void scheduleSingletonRegistering() {
        app.jobManager().on(AppEventId.DEPENDENCY_INJECTOR_LOADED, new Runnable() {
            @Override
            public void run() {
                Class c = $.classForName(className, app.classLoader());
                app.registerSingletonClass(c);
            }
        });
    }

}
