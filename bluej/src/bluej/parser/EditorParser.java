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

import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Stack;

import bluej.debugger.gentype.GenTypeExtends;
import bluej.debugger.gentype.GenTypeSolid;
import bluej.debugger.gentype.GenTypeUnbounded;
import bluej.debugger.gentype.JavaPrimitiveType;
import bluej.debugger.gentype.JavaType;
import bluej.editor.moe.Token;
import bluej.parser.ast.LocatableToken;
import bluej.parser.ast.gen.JavaTokenTypes;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.TypeEntity;
import bluej.parser.entity.UnresolvedEntity;
import bluej.parser.entity.WildcardSuperEntity;
import bluej.parser.nodes.ColourNode;
import bluej.parser.nodes.ContainerNode;
import bluej.parser.nodes.ExpressionNode;
import bluej.parser.nodes.FieldNode;
import bluej.parser.nodes.MethodNode;
import bluej.parser.nodes.ParentParsedNode;
import bluej.parser.nodes.ParsedCUNode;
import bluej.parser.nodes.ParsedNode;
import bluej.parser.nodes.ParsedTypeNode;
import bluej.parser.nodes.TypeInnerNode;
import bluej.parser.symtab.Selection;

/**
 * Parser which builds parse node tree.
 * 
 * @author Davin McCall
 */
public class EditorParser extends JavaParser
{
    private Stack<ParsedNode> scopeStack = new Stack<ParsedNode>();
    
    private LocatableToken pcuStmtBegin;
    private ParsedCUNode pcuNode;
    private List<LocatableToken> commentQueue = new LinkedList<LocatableToken>();
    private List<LocatableToken> lastTypeSpec;
    
    public EditorParser(Reader r)
    {
        super(r);
    }
    
    protected void error(String msg)
    {
        // ignore for now
    }
    
    public void parseCU(ParsedCUNode pcuNode)
    {
        this.pcuNode = pcuNode;
        scopeStack.push(pcuNode);
        parseCU();
        scopeStack.pop();
        completedNode(pcuNode, 0, pcuNode.getSize());
    }
                
    private void endTopNode(LocatableToken token, boolean included)
    {
        int topPos = getTopNodeOffset();
        ParsedNode top = scopeStack.pop();

        int endPos;
        if (included) {
            endPos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
        }
        else {
            endPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
        }
        top.setNodeSize(endPos - topPos);
        
        completedNode(top, topPos, endPos - topPos);
    }

    /**
     * A node end has been reached. This method adds any appropriate comment nodes as
     * children of the new node.
     * 
     * @param node  The new node
     * @param position  The absolute position of the new node
     * @param size  The size of the new node
     */
    protected void completedNode(ParsedNode node, int position, int size)
    {
        ListIterator<LocatableToken> i = commentQueue.listIterator();
        while (i.hasNext()) {
            LocatableToken token = i.next();
            int startpos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            if (startpos >= position && startpos < (position + size)) {
                Selection s = new Selection(token.getLine(), token.getColumn());
                s.extendEnd(token.getEndLine(), token.getEndColumn());
                int endpos = pcuNode.lineColToPosition(s.getEndLine(), s.getEndColumn());

                ColourNode cn = new ColourNode(node, Token.COMMENT1);
                node.insertNode(cn, startpos - position, endpos - startpos);
                
                i.remove();
            }
        }
    }

    protected void beginNode(int position)
    {
        ListIterator<LocatableToken> i = commentQueue.listIterator();
        while (i.hasNext()) {
            LocatableToken token = i.next();
            int startpos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            int endpos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
            if (startpos >= position) {
                break;
            }
            i.remove();
            int topOffset = getTopNodeOffset();
            ColourNode cn = new ColourNode(scopeStack.peek(), Token.COMMENT1);
            scopeStack.peek().insertNode(cn, startpos - topOffset, endpos - startpos);
        }
    }
    
    /**
     * Get the start position of the top node in the scope stack.
     */
    private int getTopNodeOffset()
    {
        Iterator<ParsedNode> i = scopeStack.iterator();
        if (!i.hasNext()) {
            return 0;
        }
        
        int rval = 0;
        i.next();
        while (i.hasNext()) {
            rval += i.next().getOffsetFromParent();
        }
        return rval;
    }
    
    //  -------------- Callbacks from the superclass ----------------------

    @Override
    protected void beginPackageStatement(LocatableToken token)
    {
        pcuStmtBegin = token;
    }
    
    @Override
    protected void gotTypeDefName(LocatableToken nameToken)
    {
        ParsedNode pnode = new ParsedTypeNode(scopeStack.peek(), nameToken.getText());
        int curOffset = getTopNodeOffset();
        LocatableToken hidden = pcuStmtBegin.getHiddenBefore();
        if (hidden != null && hidden.getType() == JavaTokenTypes.ML_COMMENT) {
            pcuStmtBegin = hidden; 
            // TODO: make certain hidden token not already consumed by prior sibling node
        }
        int insPos = pcuNode.lineColToPosition(pcuStmtBegin.getLine(), pcuStmtBegin.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void beginTypeBody(LocatableToken token)
    {
        TypeInnerNode bodyNode = new TypeInnerNode(scopeStack.peek());
        bodyNode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        ParsedTypeNode top = (ParsedTypeNode) scopeStack.peek();
        top.insertInner(bodyNode, insPos - curOffset, 0);
        scopeStack.push(bodyNode);
    }
    
    @Override
    protected void beginForLoop(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_ITERATION);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginForLoopBody(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endForLoopBody(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_ITERATION) {
            endTopNode(token, false);
        }
    }
    
    @Override
    protected void beginWhileLoop(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_ITERATION);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginWhileLoopBody(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endWhileLoopBody(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_ITERATION) {
            endTopNode(token, false);
        }
    }
    
    @Override
    protected void beginDoWhile(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_ITERATION);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginDoWhileBody(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endDoWhileBody(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_ITERATION) {
            endTopNode(token, false);
        }
    }
        
    @Override
    protected void beginIfStmt(LocatableToken token)
    {
        ParentParsedNode loopNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_SELECTION);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
        scopeStack.push(loopNode);
    }
    
    @Override
    protected void beginIfCondBlock(LocatableToken token)
    {
        // If the token is an LCURLY, it will be seen as a compound statement and scope
        // handling is done by beginStmtBlockBody
        if (token.getType() != JavaTokenTypes.LCURLY) {
            ParentParsedNode loopNode = new ParentParsedNode(scopeStack.peek());
            loopNode.setInner(true);
            int curOffset = getTopNodeOffset();
            int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(loopNode, insPos - curOffset, 0);
            scopeStack.push(loopNode);
        }
    }
    
    @Override
    protected void endIfCondBlock(LocatableToken token, boolean included)
    {
        if (scopeStack.peek().getNodeType() != ParsedNode.NODETYPE_SELECTION) {
            endTopNode(token, false);
        }
    }
    
    @Override
    protected void endIfStmt(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void beginTryCatchSmt(LocatableToken token)
    {
        ParentParsedNode tryNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_SELECTION);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(tryNode, insPos - curOffset, 0);
        scopeStack.push(tryNode);
    }
    
    @Override
    protected void beginTryBlock(LocatableToken token)
    {
        ParentParsedNode tryBlockNode = new ParentParsedNode(scopeStack.peek());
        tryBlockNode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(tryBlockNode, insPos - curOffset, 0);
        scopeStack.push(tryBlockNode);
    }
    
    @Override
    protected void endTryBlock(LocatableToken token, boolean included)
    {
        endTopNode(token, false);
    }
    
    @Override
    protected void endTryCatchStmt(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void beginStmtblockBody(LocatableToken token)
    {
        int curOffset = getTopNodeOffset();
        if (scopeStack.peek().getNodeType() == ParsedNode.NODETYPE_NONE) {
            // This is conditional, because the outer block may be a loop or selection
            // statement which already exists.
            ParentParsedNode blockNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_NONE);
            blockNode.setInner(false);
            int insPos = pcuNode.lineColToPosition(token.getLine(), token.getColumn());
            beginNode(insPos);
            scopeStack.peek().insertNode(blockNode, insPos - curOffset, 0);
            scopeStack.push(blockNode);
            curOffset = insPos;
        }
        ParentParsedNode blockInner = new ParentParsedNode(scopeStack.peek());
        blockInner.setInner(true);
        int insPos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(blockInner, insPos - curOffset, 0);
        scopeStack.push(blockInner);
    }
   
    @Override
    protected void endStmtblockBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false); // inner
        if (scopeStack.peek().getNodeType() == ParsedNode.NODETYPE_NONE) {
            endTopNode(token, included);
        }
    }
    
    @Override
    protected void beginInitBlock(LocatableToken first, LocatableToken lcurly)
    {
        int curOffset = getTopNodeOffset();
        ParentParsedNode blockNode = new ContainerNode(scopeStack.peek(), ParsedNode.NODETYPE_NONE);
        blockNode.setInner(false);
        int insPos = pcuNode.lineColToPosition(first.getLine(), first.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(blockNode, insPos - curOffset, 0);
        scopeStack.push(blockNode);
        curOffset = insPos;

        ParentParsedNode blockInner = new ParentParsedNode(scopeStack.peek());
        blockInner.setInner(true);
        insPos = pcuNode.lineColToPosition(lcurly.getEndLine(), lcurly.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(blockInner, insPos - curOffset, 0);
        scopeStack.push(blockInner);
    }
    
    @Override
    protected void endInitBlock(LocatableToken rcurly, boolean included)
    {
        endStmtblockBody(rcurly, included);
    }
    
    protected void beginElement(LocatableToken token)
    {
        pcuStmtBegin = token;
    }
    
    protected void endTypeBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false);
    }
    
    protected void gotTypeDefEnd(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void endForLoop(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void endWhileLoop(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void endDoWhile(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }

    /*
     * We have the end of a package statement.
     */
    protected void gotPackageSemi(LocatableToken token)
    {
        Selection s = new Selection(pcuStmtBegin.getLine(), pcuStmtBegin.getColumn());
        s.extendEnd(token.getLine(), token.getColumn() + token.getLength());
        
        int startpos = pcuNode.lineColToPosition(s.getLine(), s.getColumn());
        int endpos = pcuNode.lineColToPosition(s.getEndLine(), s.getEndColumn());
        
        // PkgStmtNode psn = new PkgStmtNode();
        ColourNode cn = new ColourNode(pcuNode, Token.KEYWORD1);
        beginNode(startpos);
        pcuNode.insertNode(cn, startpos, endpos - startpos);
        completedNode(cn, startpos, endpos - startpos);
    }
    
    protected void gotImportStmtSemi(LocatableToken token)
    {
        Selection s = new Selection(pcuStmtBegin.getLine(), pcuStmtBegin.getColumn());
        s.extendEnd(token.getLine(), token.getColumn() + token.getLength());
        
        int startpos = pcuNode.lineColToPosition(s.getLine(), s.getColumn());
        int endpos = pcuNode.lineColToPosition(s.getEndLine(), s.getEndColumn());
        
        // PkgStmtNode psn = new PkgStmtNode();
        ParentParsedNode cn = new ParentParsedNode(pcuNode);
        beginNode(startpos);
        pcuNode.insertNode(cn, startpos, endpos - startpos);
        completedNode(cn, startpos, endpos - startpos);
    }
    
    public void gotComment(LocatableToken token)
    {
        commentQueue.add(token);
    }
    
    @Override
    protected void gotConstructorDecl(LocatableToken token,
            LocatableToken hiddenToken)
    {
        super.gotConstructorDecl(token, hiddenToken);
        LocatableToken start = pcuStmtBegin;
        if (hiddenToken != null) {
            start = hiddenToken;
        }
        
        ParsedNode pnode = new MethodNode(scopeStack.peek(), token.getText());
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(start.getLine(), start.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void gotMethodDeclaration(LocatableToken token,
            LocatableToken hiddenToken)
    {
        LocatableToken start = pcuStmtBegin;
        if (hiddenToken != null) {
            start = hiddenToken;
            // TODO: make certain hidden token not already consumed by prior sibling node
        }
        
        JavaEntity rtype = getTypeEntity(scopeStack.peek(), lastTypeSpec);
        MethodNode pnode = new MethodNode(scopeStack.peek(), token.getText(), rtype);
        
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(start.getLine(), start.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void endMethodDecl(LocatableToken token, boolean included)
    {
        MethodNode mNode = (MethodNode) scopeStack.peek();
        endTopNode(token, included);
        TypeInnerNode topNode = (TypeInnerNode) scopeStack.peek();
        topNode.methodAdded(mNode);
    }
    
    @Override
    protected void beginMethodBody(LocatableToken token)
    {
        ParsedNode pnode = new ParentParsedNode(scopeStack.peek());
        pnode.setInner(true);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
    }
    
    @Override
    protected void endMethodBody(LocatableToken token, boolean included)
    {
        endTopNode(token, false);
    }
    
    @Override
    protected void gotTypeSpec(List<LocatableToken> tokens)
    {
        lastTypeSpec = tokens;
    }
    
    @Override
    protected void gotField(LocatableToken idToken)
    {
        FieldNode field = new FieldNode(scopeStack.peek(), idToken.getText(), lastTypeSpec);
        int curOffset = getTopNodeOffset();
        int insPos = pcuNode.lineColToPosition(pcuStmtBegin.getLine(), pcuStmtBegin.getEndColumn());
        beginNode(insPos);
        TypeInnerNode top = (TypeInnerNode) scopeStack.peek();
        top.insertField(field, insPos - curOffset, 0);
        scopeStack.push(field);
    }
    
    @Override
    protected void endField(LocatableToken token, boolean included)
    {
        endTopNode(token, included);
    }
    
    @Override
    protected void beginAnonClassBody(LocatableToken token)
    {
        ParsedTypeNode pnode = new ParsedTypeNode(scopeStack.peek(), null); // TODO generate Abc$1 ?
        int curOffset = getTopNodeOffset();
        LocatableToken begin = lastTypeSpec.get(0);
        int insPos = pcuNode.lineColToPosition(begin.getLine(), begin.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(pnode, insPos - curOffset, 0);
        scopeStack.push(pnode);
        
        TypeInnerNode bodyNode = new TypeInnerNode(scopeStack.peek());
        bodyNode.setInner(true);
        curOffset = getTopNodeOffset();
        insPos = pcuNode.lineColToPosition(token.getEndLine(), token.getEndColumn());
        beginNode(insPos);
        pnode.insertInner(bodyNode, insPos - curOffset, 0);
        scopeStack.push(bodyNode);
    }
    
    @Override
    protected void endAnonClassBody(LocatableToken token, boolean included)
    {
        endTopNode(token, included);  // inner node
        endTopNode(token, included);  // outer node
    }
    
    @Override
    protected void beginExpression(LocatableToken token)
    {
        ExpressionNode nnode = new ExpressionNode(scopeStack.peek());
        int curOffset = getTopNodeOffset();
        LocatableToken begin = token;
        int insPos = pcuNode.lineColToPosition(begin.getLine(), begin.getColumn());
        beginNode(insPos);
        scopeStack.peek().insertNode(nnode, insPos - curOffset, 0);
        scopeStack.push(nnode);
    }
    
    @Override
    protected void endExpression(LocatableToken token)
    {
        endTopNode(token, false);
    }
    
    private class DepthRef
    {
        int depth = 0;
    }

    /**
     * Resolve a type specification. Returns null if the type couldn't be resolved.
     */
    private JavaEntity getTypeEntity(EntityResolver resolver, List<LocatableToken> tokens)
    {
        DepthRef dr = new DepthRef();
        return getTypeEntity(resolver, tokens.listIterator(), dr);
    }
    
    /**
     * Resolve a type specification. Returns null if the type couldn't be resolved.
     */
    private JavaEntity getTypeEntity(EntityResolver resolver,
            ListIterator<LocatableToken> i, DepthRef depthRef)
    {
        LocatableToken token = i.next();
        
        if (isPrimitiveType(token)) {
            if (token.getType() == JavaTokenTypes.LITERAL_void) {
                return new TypeEntity(JavaPrimitiveType.getVoid());
            }
            
            JavaType type = null;
            switch (token.getType()) {
            case JavaTokenTypes.LITERAL_int:
                type = JavaPrimitiveType.getInt();
                break;
            case JavaTokenTypes.LITERAL_short:
                type = JavaPrimitiveType.getShort();
                break;
            case JavaTokenTypes.LITERAL_char:
                type = JavaPrimitiveType.getChar();
                break;
            case JavaTokenTypes.LITERAL_byte:
                type = JavaPrimitiveType.getByte();
                break;
            case JavaTokenTypes.LITERAL_boolean:
                type = JavaPrimitiveType.getBoolean();
                break;
            case JavaTokenTypes.LITERAL_double:
                type = JavaPrimitiveType.getDouble();
                break;
            case JavaTokenTypes.LITERAL_float:
                type = JavaPrimitiveType.getFloat();
            }
            
            while (i.hasNext()) {
                token = i.next();
                if (token.getType() == JavaTokenTypes.LBRACK) {
                    type = type.getArray();
                    i.next();  // RBRACK
                }
                else {
                    return null;
                }
            }
            
            return new TypeEntity(type);
        }
        
        String text = token.getText();
        
        //PackageOrClass poc = resolver.resolvePackageOrClass(text, null);
        JavaEntity poc = UnresolvedEntity.getEntity(resolver, text, "");
        while (poc != null && i.hasNext()) {
            token = i.next();
            if (token.getType() == JavaTokenTypes.LT) {
                // Type arguments
                poc = processTypeArgs(resolver, poc, i, depthRef);
                if (poc == null) {
                    return null;
                }
                if (! i.hasNext()) {
                    return poc;
                }
                token = i.next();
            }
            if (token.getType() != JavaTokenTypes.DOT) {
                poc = poc.resolveAsType();
                if (poc == null) {
                    return null;
                }
                
                while (token.getType() == JavaTokenTypes.LBRACK) {
                    poc = new TypeEntity(poc.getType().getCapture().getArray());
                    if (i.hasNext()) {
                        token = i.next(); // RBRACK
                    }
                    if (! i.hasNext()) {
                        return poc.resolveAsType();
                    }
                    token = i.next();
                }
                
                i.previous(); // allow token to be re-read by caller
                return poc.resolveAsType();
            }
            token = i.next();            
            if (token.getType() != JavaTokenTypes.IDENT) {
                break;
            }
            poc = poc.getSubentity(token.getText());
        }
        
        return poc;
    }
    
    /**
     * Process tokens as type arguments
     * @param base  The base type, i.e. the type to which the arguments are applied
     * @param i     A ListIterator to iterate through the tokens
     * @param depthRef  The argument depth
     * @return   A ClassEntity representing the type with type arguments applied (or null)
     */
    private JavaEntity processTypeArgs(EntityResolver resolver, JavaEntity base,
            ListIterator<LocatableToken> i, DepthRef depthRef)
    {
        int startDepth = depthRef.depth;
        List<JavaEntity> taList = new LinkedList<JavaEntity>();
        depthRef.depth++;
        
        mainLoop:
        while (i.hasNext() && depthRef.depth > startDepth) {
            LocatableToken token = i.next();
            if (token.getType() == JavaTokenTypes.QUESTION) {
                if (! i.hasNext()) {
                    return null;
                }
                token = i.next();
                if (token.getType() == JavaTokenTypes.LITERAL_super) {
                    JavaEntity taEnt = getTypeEntity(resolver, i, depthRef);
                    taList.add(new WildcardSuperEntity(taEnt));
                }
                else if (token.getType() == JavaTokenTypes.LITERAL_extends) {
                    JavaEntity taEnt = getTypeEntity(resolver, i, depthRef);
                    if (taEnt == null) {
                        return null;
                    }
                    GenTypeSolid bound = taEnt.getType().getCapture().asSolid();
                    taList.add(new TypeEntity(new GenTypeExtends(bound)));
                }
                else {
                    taList.add(new TypeEntity(new GenTypeUnbounded()));
                    i.previous();
                }
            }
            else {
                i.previous();
                JavaEntity taEnt = getTypeEntity(resolver, i, depthRef);
                if (taEnt == null) {
                    return null;
                }
                taList.add(taEnt);
            }
            
            if (! i.hasNext()) {
                return null;
            }
            token = i.next();
            int ttype = token.getType();
            while (ttype == JavaTokenTypes.GT || ttype == JavaTokenTypes.SR || ttype == JavaTokenTypes.BSR) {
                switch (ttype) {
                case JavaTokenTypes.BSR:
                    depthRef.depth--;
                case JavaTokenTypes.SR:
                    depthRef.depth--;
                default:
                    depthRef.depth--;
                }
                if (! i.hasNext()) {
                    break mainLoop;
                }
                token = i.next();
                ttype = token.getType();
            }
            
            if (ttype != JavaTokenTypes.COMMA) {
                i.previous();
                break;
            }
        }
        // TODO check the type arguments are actually valid
        return base.setTypeArgs(taList);
    }

}
