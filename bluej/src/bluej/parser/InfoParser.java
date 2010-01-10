/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.TypeEntity;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;
import bluej.parser.symtab.ClassInfo;
import bluej.parser.symtab.Selection;

public class InfoParser extends EditorParser
{
    private ClassInfo info;
    private int classLevel = 0; // number of nested classes
    private boolean isPublic;
    private int lastTdType; // last typedef type (TYPEDEF_CLASS, _INTERFACE etc)
    private boolean storeCurrentClassInfo;

    private List<LocatableToken> lastTypespecToks;
    private boolean modPublic;
    private List<MethodDesc> methodDescs = new LinkedList<MethodDesc>();
    MethodDesc currentMethod;
    
    /** Represents a method description */
    class MethodDesc
    {
        String name;
        JavaEntity returnType; // null for constructors
        List<JavaEntity> paramTypes;
        String paramNames; // space separated list
        String javadocText;
    }
    
    private boolean gotExtends; // next type spec is the superclass/superinterfaces
    private boolean gotImplements; // next type spec(s) are interfaces
    private List<Selection> interfaceSelections;
    private Selection lastCommaSelection;

    private boolean hadError;

    private LocatableToken pkgLiteralToken;
    private List<LocatableToken> packageTokens;
    private LocatableToken pkgSemiToken;

    public InfoParser(Reader r)
    {
        super(r);
    }

    public static ClassInfo parse(File f) throws FileNotFoundException
    {
        FileInputStream fis = new FileInputStream(f);
        return parse(new InputStreamReader(fis));
    }

    public static ClassInfo parse(Reader r)
    {
        InfoParser infoParser = null;
        infoParser = new InfoParser(r);
        infoParser.parseCU();

        if (infoParser.info != null && !infoParser.hadError) {
            infoParser.resolveComments();
            return infoParser.info;
        }
        else {
            throw new RuntimeException("Couldn't get class info");
        }
    }

    public void resolveComments()
    {
        methodLoop:
        for (MethodDesc md : methodDescs) {
            // Build the method signature
            String methodSig;
            
            if (md.returnType != null) {
                md.returnType = md.returnType.resolveAsType();
                if (md.returnType == null) {
                    continue;
                }
                methodSig = md.returnType.getType().getErasedType() + " " + md.name + "(";
            }
            else {
                // constructor
                methodSig = md.name + "(";
            }
            
            Iterator<JavaEntity> i = md.paramTypes.iterator();
            while (i.hasNext()) {
                JavaEntity paramEnt = i.next();
                if (paramEnt == null) {
                    continue methodLoop;
                }
                TypeEntity paramType = paramEnt.resolveAsType();
                if (paramType == null) {
                    continue methodLoop;
                }
                methodSig += paramType.getType().getErasedType().toString();
                if (i.hasNext()) {
                    methodSig += ", ";
                }
            }
            
            methodSig += ")";
            md.paramNames = md.paramNames.trim();
            info.addComment(methodSig, md.javadocText, md.paramNames);
        }
    }
    
    protected void error(String msg)
    {
        hadError = true;
        // Just try and recover.
    }

    @Override
    protected void beginTypeBody(LocatableToken token)
    {
        super.beginTypeBody(token);
        classLevel++;
    }
    
    @Override
    protected void endTypeBody(LocatableToken token, boolean included)
    {
        super.endTypeBody(token, included);
        classLevel--;
    }
    
    protected void gotTypeSpec(List<LocatableToken> tokens)
    {
        lastTypespecToks = tokens;
        super.gotTypeSpec(tokens);
        LocatableToken first = tokens.get(0);
        if (!isPrimitiveType(first)) {
            if (storeCurrentClassInfo && ! gotExtends && ! gotImplements) {
                info.addUsed(first.getText());
            }
        }

        if (gotExtends) {
            // The list of tokens gives us the name of the class that we extend
            info.setSuperclass(getClassName(tokens));
            Selection superClassSelection = getSelection(tokens);
            info.setSuperReplaceSelection(superClassSelection);
            info.setImplementsInsertSelection(new Selection(superClassSelection.getEndLine(),
                    superClassSelection.getEndColumn()));
            gotExtends = false;
        }
        else if (gotImplements && interfaceSelections != null) {
            Selection interfaceSel = getSelection(tokens);
            if (lastCommaSelection != null) {
                lastCommaSelection.extendEnd(interfaceSel.getLine(), interfaceSel.getColumn());
                interfaceSelections.add(lastCommaSelection);
                lastCommaSelection = null;
            }
            interfaceSelections.add(interfaceSel);
            info.addImplements(getClassName(tokens));
            if (tokenStream.LA(1).getType() == JavaTokenTypes.COMMA) {
                lastCommaSelection = getSelection(tokenStream.LA(1));
            }
            else {
                gotImplements = false;
                info.setInterfaceSelections(interfaceSelections);
                info.setImplementsInsertSelection(new Selection(interfaceSel.getEndLine(),
                        interfaceSel.getEndColumn()));
            }
        }
    }

    protected void gotMethodDeclaration(LocatableToken token, LocatableToken hiddenToken)
    {
        super.gotMethodDeclaration(token, hiddenToken);
        String lastComment = (hiddenToken != null) ? hiddenToken.getText() : null;
        currentMethod = new MethodDesc();
        currentMethod.returnType = ParseUtils.getTypeEntity(scopeStack.peek(), lastTypespecToks);
        currentMethod.name = token.getText();
        currentMethod.paramNames = "";
        currentMethod.paramTypes = new LinkedList<JavaEntity>();
        currentMethod.javadocText = lastComment;
    }

    protected void gotConstructorDecl(LocatableToken token, LocatableToken hiddenToken)
    {
        super.gotConstructorDecl(token, hiddenToken);
        String lastComment = (hiddenToken != null) ? hiddenToken.getText() : null;
        currentMethod = new MethodDesc();
        currentMethod.name = token.getText();
        currentMethod.paramNames = "";
        currentMethod.paramTypes = new LinkedList<JavaEntity>();
        currentMethod.javadocText = lastComment;
    }

    protected void gotMethodParameter(LocatableToken token)
    {
        super.gotMethodParameter(token);
        if (currentMethod != null) {
            currentMethod.paramNames += token.getText() + " ";
            currentMethod.paramTypes.add(ParseUtils.getTypeEntity(scopeStack.peek(), lastTypespecToks));
        }
    }

    protected void gotAllMethodParameters()
    {
        super.gotAllMethodParameters();
        if (storeCurrentClassInfo && classLevel == 1) {
            methodDescs.add(currentMethod);
            currentMethod = null;
        }
    }

    protected void gotTypeDef(int tdType)
    {
        isPublic = modPublic;
        super.gotTypeDef(tdType);
        lastTdType = tdType;
    }

    protected void gotTypeDefName(LocatableToken nameToken)
    {
        super.gotTypeDefName(nameToken);
        gotExtends = false; // haven't seen "extends ..." yet
        gotImplements = false;
        if (classLevel == 0) {
            if (info == null || isPublic && !info.foundPublicClass()) {
                info = new ClassInfo();
                info.setName(nameToken.getText(), isPublic);
                info.setEnum(lastTdType == TYPEDEF_ENUM);
                info.setInterface(lastTdType == TYPEDEF_INTERFACE);
                Selection insertSelection = new Selection(nameToken.getLine(), nameToken.getEndColumn());
                info.setExtendsInsertSelection(insertSelection);
                info.setImplementsInsertSelection(insertSelection);
                if (pkgSemiToken != null) {
                    info.setPackageSelections(getSelection(pkgLiteralToken), getSelection(packageTokens),
                            getClassName(packageTokens), getSelection(pkgSemiToken));
                }
                storeCurrentClassInfo = true;
            } else {
                storeCurrentClassInfo = false;
            }
        }
    }

    protected void gotTypeDefExtends(LocatableToken extendsToken)
    {
        super.gotTypeDefExtends(extendsToken);
        if (classLevel == 0 && storeCurrentClassInfo) {
            // info.setExtendsReplaceSelection(s)
            gotExtends = true;
            SourceLocation extendsStart = info.getExtendsInsertSelection().getStartLocation();
            int extendsEndCol = tokenStream.LA(1).getColumn();
            int extendsEndLine = tokenStream.LA(1).getLine();
            if (extendsStart.getLine() == extendsEndLine) {
                info.setExtendsReplaceSelection(new Selection(extendsEndLine, extendsStart.getColumn(), extendsEndCol - extendsStart.getColumn()));
            }
            else {
                info.setExtendsReplaceSelection(new Selection(extendsEndLine, extendsStart.getColumn(), extendsToken.getEndColumn() - extendsStart.getColumn()));
            }
            info.setExtendsInsertSelection(null);
        }
    }

    protected void gotTypeDefImplements(LocatableToken implementsToken)
    {
        super.gotTypeDefImplements(implementsToken);
        if (classLevel == 0 && storeCurrentClassInfo) {
            gotImplements = true;
            interfaceSelections = new LinkedList<Selection>();
            interfaceSelections.add(getSelection(implementsToken));
        }
    }

    protected void beginPackageStatement(LocatableToken token)
    {
        super.beginPackageStatement(token);
        pkgLiteralToken = token;
    }

    protected void gotPackage(List<LocatableToken> pkgTokens)
    {
        super.gotPackage(pkgTokens);
        packageTokens = pkgTokens;
    }

    protected void gotPackageSemi(LocatableToken token)
    {
        super.gotPackageSemi(token);
        pkgSemiToken = token;
    }

    @Override
    protected void gotModifier(LocatableToken token)
    {
        super.gotModifier(token);
        modPublic = true;
    }
    
    @Override
    protected void modifiersConsumed()
    {
        modPublic = false;
    }

    private Selection getSelection(LocatableToken token)
    {
        if (token.getLine() <= 0 || token.getColumn() <= 0) {
            System.out.println("" + token);
        }
        if (token.getLength() < 0) {
            System.out.println("Bad length: " + token.getLength());
            System.out.println("" + token);
        }
        return new Selection(token.getLine(), token.getColumn(), token.getLength());
    }

    private Selection getSelection(List<LocatableToken> tokens)
    {
        Iterator<LocatableToken> i = tokens.iterator();
        Selection s = getSelection(i.next());
        if (i.hasNext()) {
            LocatableToken last = i.next();
            while (i.hasNext()) {
                last = i.next();
            }
            s.combineWith(getSelection(last));
        }
        return s;
    }

    private String concatenate(List<LocatableToken> tokens)
    {
        String result = "";
        for (LocatableToken tok : tokens) {
            result += tok.getText();
        }
        return result;
    }

    /**
     * Convert a list of tokens specifying a type, which may include type parameters, into a class name
     * without type parameters.
     */
    private String getClassName(List<LocatableToken> tokens)
    {
        String name = "";
        for (Iterator<LocatableToken> i = tokens.iterator(); i.hasNext(); ) {
            name += i.next().getText();
            if (i.hasNext()) {
                // there may be type parameters, array
                LocatableToken tok = i.next();
                if (tok.getType() == JavaTokenTypes.LT) {
                    skipTypePars(i);
                    if (!i.hasNext()) {
                        return name;
                    }
                    tok = i.next(); // DOT
                }
                else if (tok.getType() == JavaTokenTypes.LBRACK) {
                    return name;
                }
                name += ".";
            }
        }
        return name;
    }

    private void skipTypePars(Iterator<LocatableToken> i)
    {
        int level = 1;
        while (level > 0 && i.hasNext()) {
            LocatableToken tok = i.next();
            if (tok.getType() == JavaTokenTypes.LT) {
                level++;
            }
            else if (tok.getType() == JavaTokenTypes.GT) {
                level--;
            }
            else if (tok.getType() == JavaTokenTypes.SR) {
                level -= 2;
            }
            else if (tok.getType() == JavaTokenTypes.BSR) {
                level -= 3;
            }
        }
    }
}
