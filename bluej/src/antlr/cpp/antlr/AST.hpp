#ifndef INC_AST_hpp__
#define INC_AST_hpp__

/**
 * <b>SOFTWARE RIGHTS</b>
 * <p>
 * ANTLR 2.3.0 MageLang Insitute, 1998
 * <p>
 * We reserve no legal rights to the ANTLR--it is fully in the
 * public domain. An individual or company may do whatever
 * they wish with source code distributed with ANTLR or the
 * code generated by ANTLR, including the incorporation of
 * ANTLR, or its output, into commerical software.
 * <p>
 * We encourage users to develop software with ANTLR. However,
 * we do ask that credit is given to us for developing
 * ANTLR. By "credit", we mean that if you use ANTLR or
 * incorporate any source code into one of your programs
 * (commercial product, research project, or otherwise) that
 * you acknowledge this fact somewhere in the documentation,
 * research report, etc... If you like ANTLR and have
 * developed a nice tool with the output, please mention that
 * you developed it using ANTLR. In addition, we ask that the
 * headers remain intact in our source code. As long as these
 * guidelines are kept, we expect to continue enhancing this
 * system and expect to make other tools available as they are
 * completed.
 * <p>
 * The ANTLR gang:
 * @version ANTLR 2.3.0 MageLang Insitute, 1998
 * @author Terence Parr, <a href=http://www.MageLang.com>MageLang Institute</a>
 * @author <br>John Lilley, <a href=http://www.Empathy.com>Empathy Software</a>
 * @author <br><a href="mailto:pete@yamuna.demon.co.uk">Pete Wells</a>
 */

#include "antlr/config.hpp"
#include "antlr/ASTNode.hpp"
#include <vector>

class AST {
public:
	AST(ASTNode* n);
	~AST();

	const ASTNode* getNode() const;
	ASTNode* getNode();

	void addChild(RefAST c);

	bool equals(const AST* t) const;
	bool equalsList(const AST* t) const;
	bool equalsListPartial(const AST* t) const;
	bool equalsTree(const AST* t) const;
	bool equalsTreePartial(const AST* t) const;

	std::vector<const AST*> findAll(RefAST t) const;
	std::vector<const AST*> findAllPartial(RefAST t) const;

	RefAST getFirstChild() const;
	RefAST getNextSibling() const;

	std::string getText() const;
	int getType() const;

	void initialize(int t,const std::string& txt);
	void initialize(RefAST t);
	void initialize(RefToken t);

	void setFirstChild(RefAST c);
	void setNextSibling(RefAST n);

	void setText(const std::string& txt);
	void setType(int type);

	std::string toString() const;
	std::string toStringList() const;
	std::string toStringTree() const;
private:
	RefAST down;
	RefAST right;
	ASTNode* node;

	void doWorkForFindAll(std::vector<const AST*>& v,
			RefAST target,bool partialMatch) const;

	AST();
	AST(const AST& other);
	AST& operator=(const AST& other);
};

extern RefAST nullAST;

#ifdef NEEDS_OPERATOR_LESS_THAN
inline operator<(RefAST l,RefAST r); // {return true;}
#endif

#endif //INC_AST_hpp__
