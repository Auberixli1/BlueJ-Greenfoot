#ifndef INC_CircularQueue_hpp__
#define INC_CircularQueue_hpp__

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
#include <vector>

template <class T>
class CircularQueue {
private:
	std::vector<T> storage;

public:
	CircularQueue()
		: storage() {}
	~CircularQueue()
		{}

	T elementAt(int idx) const
		{ return storage[idx]; }
	void removeFirst()
		{ storage.erase(storage.begin()); }
	void append(const T& t)
		{ storage.push_back(t); }
	int entries() const
		{ return storage.size(); }

private:
	CircularQueue(const CircularQueue&);
	const CircularQueue& operator=(const CircularQueue&);
};

#endif //INC_CircularQueue_hpp__
