package bluej.debugger.gentype;

import java.util.Map;

/*
 * "short" primitive type.
 * 
 * @author Davin McCall
 * @version $Id: GenTypeShort.java 2656 2004-06-25 01:44:18Z davmac $
 */
public class GenTypeShort implements GenType
{
    public GenTypeShort()
    {
        super();
    }
    
    public String toString()
    {
        return toString(false);
    }
    
    public String toString(boolean stripPrefix)
    {
        return "short";
    }
    
    public boolean isPrimitive()
    {
        return true;
    }
    
    public GenType mapTparsToTypes(Map tparams)
    {
        return this;
    }
}
