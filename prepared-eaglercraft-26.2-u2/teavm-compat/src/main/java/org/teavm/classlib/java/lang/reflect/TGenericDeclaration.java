package org.teavm.classlib.java.lang.reflect;

/**
 * java.lang.reflect.GenericDeclaration — referenced by TypeVariable; no
 * functional generic reflection exists under TeaVM (see ClassInject notes).
 */
public interface TGenericDeclaration {

	TTypeVariable<?>[] getTypeParameters();

}
