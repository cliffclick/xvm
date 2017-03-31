package org.xvm.proto.template;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ConstantPool.IntConstant;
import org.xvm.proto.*;
import org.xvm.proto.ObjectHandle.JavaLong;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xInt64
        extends TypeCompositionTemplate
    {
    public xInt64(TypeSet types)
        {
        super(types, "x:Int64", "x:Object", Shape.Const);

        addImplement("x:IntNumber");

        INSTANCE = this;
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new JavaLong(clazz);
        }

    @Override
    public ObjectHandle createConstHandle(Constant constant)
        {
        return constant instanceof IntConstant ? new JavaLong(f_clazzCanonical,
            (((ConstantPool.IntConstant) constant).getValue().getLong())) : null;
        }

    @Override
    public ObjectHandle invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, ObjectHandle[] ahReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;
        JavaLong hThat = (JavaLong) hArg;

        // TODO: check overflow
        ahReturn[0] = makeHandle(hThis.getValue() + hThat.getValue());
        return null;
        }

    @Override
    public ObjectHandle invokeInc(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        // TODO: check overflow
        ahReturn[0] = makeHandle(hThis.getValue() + 1);
        return null;
        }

    @Override
    public ObjectHandle invokeNeg(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahReturn)
        {
        JavaLong hThis = (JavaLong) hTarget;

        ahReturn[0] = makeHandle(-hThis.getValue());
        return null;
        }

    public static xInt64 INSTANCE;
    public static JavaLong makeHandle(long lValue)
        {
        return new JavaLong(INSTANCE.f_clazzCanonical, lValue);
        }
    }
