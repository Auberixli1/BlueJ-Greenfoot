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

#include "antlr/AST.hpp"
#include <cassert>

AST::AST(ASTNode* n)
{
	node=n;
}

AST::~AST()
{
	delete node;
}

const ASTNode* AST::getNode() const
{
	return node;
}

ASTNode* AST::getNode()
{
	return node;
}

void AST::addChild(RefAST c)
{
	if (c==0)
		return;
	RefAST tmp=down;
	if (tmp) {
		while (tmp->right)
			tmp=tmp->right;
		tmp->right=c;
	} else {
		down=c;
	}
}

void AST::doWorkForFindAll(std::vector<const AST*>& v,
		RefAST target,bool partialMatch) const
{
	// Start walking sibling lists, looking for matches.
	for (const AST* sibling=this;
			sibling;
			sibling=sibling->getNextSibling())
	{
		if ( (partialMatch && sibling->equalsTreePartial(target)) ||
				(!partialMatch && sibling->equalsTree(target)) ) {
			v.push_back(sibling);
		}
/*
		if ( partialMatch ) if ( sibling->equalsTreePartial(target) ) {
			// subtree rooted at 'sibling' exact or partial equals 'target'
			v.push_back(sibling);
		}
		if ( !partialMatch ) if ( sibling->equalsTree(target) ) {
			// subtree rooted at 'sibling' exact or partial equals 'target'
			v.push_back(sibling);
		}
*/
		// regardless of match or not, check any children for matches
		if ( sibling->getFirstChild() ) {
			(sibling->getFirstChild())->doWorkForFindAll(v, target, partialMatch);
		}
	}

}

/** Is node t equal to this? */
bool AST::equals(const AST* t) const
{
	if (!t)
		return false;
	return getType() == t->getType();
}

/** Is t an exact structural and equals() match of this tree.  The
 *  'this' reference is considered the start of a sibling list.
 */
bool AST::equalsList(const AST* t) const
{
	// the empty tree is not a match of any non-null tree.
	if (!t)
		return false;

	// Otherwise, start walking sibling lists.  First mismatch, return false.
	const AST* sibling=this;
	for (;sibling && t;
			sibling=sibling->getNextSibling(), t=t->getNextSibling()) {
		// as a quick optimization, check roots first.
		if (!sibling->equals(t))
			return false;
		// if roots match, do full list match test on children.
		if (sibling->getFirstChild())
			if (!sibling->getFirstChild()->equalsList(t->getFirstChild()))
				return false;
	}

	if (!sibling && !t)
		return true;

	// one sibling list has more than the other
	return false;
}

/** Is 'sub' a subtree of this list?
 *  The siblings of the root are NOT ignored.
 */
bool AST::equalsListPartial(const AST* sub) const
{
	// the empty tree is always a subset of any tree.
	if (!sub)
		return true;

	// Otherwise, start walking sibling lists.  First mismatch, return false.
	const AST* sibling=this;
	for (;sibling && sub;
			sibling=sibling->getNextSibling(), sub=sub->getNextSibling()) {
		// as a quick optimization, check roots first.
		if (!sibling->equals(sub))
			return false;
		// if roots match, do partial list match test on children.
		if (sibling->getFirstChild())
			if (!sibling->getFirstChild()->equalsListPartial(sub->getFirstChild()))
				return false;
	}

	if (!sibling && sub)
		// nothing left to match in this tree, but subtree has more
		return false;

	// either both are null or sibling has more, but subtree doesn't
	return true;
}

/** Is tree rooted at 'this' equal to 't'?  The siblings
 *  of 'this' are ignored.
 */
bool AST::equalsTree(const AST* t) const
{
	// check roots first
	if (!equals(t))
		return false;
	// if roots match, do full list match test on children.
	if (getFirstChild())
		if (!getFirstChild()->equalsList(t->getFirstChild()))
			return false;

	return true;
}

/** Is 'sub' a subtree of the tree rooted at 'this'?  The siblings
 *  of 'this' are ignored.
 */
bool AST::equalsTreePartial(const AST* sub) const
{
	// the empty tree is always a subset of any tree.
	if (!sub)
		return true;

	// check roots first
	if (!equals(sub))
		return false;
	// if roots match, do full list partial match test on children.
	if (getFirstChild())
		if (!getFirstChild()->equalsListPartial(sub->getFirstChild()))
			return false;

	return true;
}

/** Walk the tree looking for all exact subtree matches.  Return
 *  an ASTEnumerator that lets the caller walk the list
 *  of subtree roots found herein.
 */
std::vector<const AST*> AST::findAll(RefAST target) const
{
	std::vector<const AST*> roots;

	// the empty tree cannot result in an enumeration
	if (target) {
		doWorkForFindAll(roots,target,false); // find all matches recursively
	}

	return roots;
}

/** Walk the tree looking for all subtrees.  Return
 *  an ASTEnumerator that lets the caller walk the list
 *  of subtree roots found herein.
 */
std::vector<const AST*> AST::findAllPartial(RefAST target) const
{
	std::vector<const AST*> roots;

	// the empty tree cannot result in an enumeration
	if (target) {
		doWorkForFindAll(roots,target,true); // find all matches recursively
	}

	return roots;
}

RefAST AST::getFirstChild() const
{
	return down;
}

RefAST AST::getNextSibling() const
{
	return right;
}

std::string AST::getText() const
{
	assert(node);
	return node->getText();
}

int AST::getType() const
{
	assert(node);
	return node->getType();
}

void AST::initialize(int t,const std::string& txt)
{
	assert(node);
	node->initialize(t,txt);
}

void AST::initialize(RefAST t)
{
	assert(node);
	node->initialize(t);
}

void AST::initialize(RefToken t)
{
	assert(node);
	node->initialize(t);
}

void AST::setFirstChild(RefAST c)
{
	down=c;
}

void AST::setNextSibling(RefAST n)
{
	right=n;
}

void AST::setText(const std::string& txt)
{
	assert(node);
	node->setText(txt);
}

void AST::setType(int type)
{
	assert(node);
	node->setType(type);
}

std::string AST::toString() const
{
	return getText();
}

std::string AST::toStringList() const
{
	std::string ts="";
	if (getFirstChild()) {
		ts+=" ( ";
		ts+=toString();
		ts+=getFirstChild()->toStringList();
		ts+=" )";
	} else {
		ts+=" ";
		ts+=toString();
	}
	if (getNextSibling())
		ts+=getNextSibling()->toStringList();
	return ts;
}

std::string AST::toStringTree() const
{
	const AST* t=this;
	std::string ts="";
	if (getFirstChild()) {
		ts+=" ( ";
		ts+=toString();
		ts+=getFirstChild()->toStringList();
		ts+=" )";
	} else {
		ts+=" ";
		ts+=toString();
	}
	return ts;
}

// this is nasty, but it makes the code generation easier
RefAST nullAST;

