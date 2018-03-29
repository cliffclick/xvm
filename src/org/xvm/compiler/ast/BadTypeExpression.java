package org.xvm.compiler.ast;


import java.lang.reflect.Field;
import org.xvm.asm.constants.TypeConstant;


/**
 * A type expression that can't figure out how to be a type exception. It pretends to be a type,
 * but it's going to end in misery and compiler errors.
 */
public class BadTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BadTypeExpression(Expression nonType)
        {
        this.nonType = nonType;
        }


    // ----- TypeExpression methods ----------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        throw new UnsupportedOperationException();
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isAborting()
        {
        return true;
        }

    @Override
    public long getStartPosition()
        {
        return nonType.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return nonType.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "/* NOT A TYPE!!! */ " + nonType;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression nonType;

    private static final Field[] CHILD_FIELDS = fieldsForNames(BadTypeExpression.class, "nonType");
    }
