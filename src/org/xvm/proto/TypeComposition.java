package org.xvm.proto;

import org.xvm.asm.Component;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;

import java.util.function.Supplier;

/**
 * TypeComposition represents a fully resolved class (e.g. ArrayList<String>)
 *
 * @author gg 2017.02.23
 */
public class TypeComposition
    {
    public final ClassTemplate f_template;

    // at the moment, ignore the case of ArrayList<Runnable | String>
    public final Type[] f_atGenericActual; // corresponding to the m_template's GenericTypeName

    private Type m_typePublic;
    private Type m_typeProtected;
    private Type m_typePrivate;
    private Type m_typeStruct;

    public TypeComposition(ClassTemplate template, Type[] atnGenericActual)
        {
        // assert(atnGenericActual.length == template.f_asFormalType.length);

        f_template = template;
        f_atGenericActual = atnGenericActual;
        }

    public ObjectHandle ensureAccess(ObjectHandle handle, Constants.Access access)
        {
        assert handle.f_clazz == this;

        Type typeCurrent = handle.m_type;
        Type typeTarget;

        switch (access)
            {
            case PUBLIC:
                typeTarget = ensurePublicType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case PROTECTED:
                typeTarget = ensureProtectedType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case PRIVATE:
                typeTarget = ensurePrivateType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            case STRUCT:
                typeTarget = ensureStructType();
                if (typeCurrent == typeTarget)
                    {
                    return handle;
                    }
                break;

            default:
                throw new IllegalStateException();
            }

        handle = handle.cloneHandle();
        handle.m_type = typeTarget;
        return handle;
        }

    public synchronized Type ensurePublicType()
        {
        Type type = m_typePublic;
        if (type == null)
            {
            m_typePublic = type = f_template.f_types.createType(
                    f_template, f_atGenericActual, Constants.Access.PUBLIC);
            }
        return type;
        }
    public synchronized Type ensureProtectedType()
        {
        Type type = m_typeProtected;
        if (type == null)
            {
            m_typeProtected = type = f_template.f_types.createType(
                    f_template, f_atGenericActual, Constants.Access.PROTECTED);
            }
        return type;
        }

    public synchronized Type ensurePrivateType()
        {
        Type type = m_typePrivate;
        if (type == null)
            {
            m_typePrivate = type = f_template.f_types.createType(
                    f_template, f_atGenericActual, Constants.Access.PRIVATE);
            }
        return type;
        }

    public synchronized Type ensureStructType()
        {
        Type type = m_typeStruct;
        if (type == null)
            {
            m_typeStruct = type = f_template.f_types.createType(
                    f_template, f_atGenericActual, Constants.Access.STRUCT);
            }
        return type;
        }

    public boolean isStruct(Type type)
        {
        return type == m_typeStruct;
        }

    // does this class extend that?
    public boolean extends_(TypeComposition that)
        {
        assert that.f_template.f_struct.getFormat() != Component.Format.INTERFACE;

        if (this.f_template.extends_(that.f_template))
            {
            // TODO: check the generic type relationship
            return true;
            }

        return false;
        }

    // create a type by resolving the specified formal type parameter, which must be defined
    // by this class's template with a corresponding actual type of this class
    public Type resolveFormalType(String sFormalName)
        {
        try
            {
            // TODO
            return f_atGenericActual[f_template.f_struct.getTypeParamsAsList().indexOf(sFormalName)];
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            throw new IllegalArgumentException(
                    "Invalid formal name: " + sFormalName + " for " + f_template);
            }
        }

    // create a sequence of frames to be called in the inverse order (the base super first)
    public Frame callDefaultConstructors(Frame frame, ObjectHandle[] ahVar, Supplier<Frame> continuation)
        {
        ClassTemplate template = f_template;
        MethodStructure ftDefault = ConstantPoolAdapter.getDefaultConstructor(template.f_struct);
        ClassTemplate templateSuper = template.getSuper();

        Frame frameDefault;
        if (ftDefault == null)
            {
            frameDefault = null;
            }
        else
            {
            frameDefault = frame.f_context.createFrame1(frame, ftDefault,
                                ahVar[0], ahVar, Frame.RET_UNUSED);
            frameDefault.m_continuation = continuation;
            continuation = null;
            }

        Frame frameSuper = null;
        if (templateSuper != null)
            {
            TypeComposition clazzSuper = templateSuper.resolve(f_atGenericActual);
            frameSuper = clazzSuper.callDefaultConstructors(frame, ahVar, continuation);
            }

        if (frameSuper == null)
            {
            return frameDefault;
            }

        frameSuper.m_continuation = () -> frameDefault;
        return frameSuper;
        }

    @Override
    public String toString()
        {
        return f_template.f_sName + Utils.formatArray(f_atGenericActual, "<", ">", ", ");
        }
    }
