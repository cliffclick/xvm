package org.xvm.runtime.template._native.collections.arrays;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.numbers.xUInt16;


/**
 * Native RTDelegate<UInt16> implementation.
 */
public class xRTUInt16Delegate
        extends LongBasedDelegate
        implements ByteView
    {
    public static xRTUInt16Delegate INSTANCE;

    public xRTUInt16Delegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 16, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeCUInt16());
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xUInt16.INSTANCE.makeJavaLong(lValue);
        }
    }