#ifndef INC_CharBuffer_hpp__
#define INC_CharBuffer_hpp__

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

/**A Stream of characters fed to the lexer from a InputStream that can
 * be rewound via mark()/rewind() methods.
 * <p>
 * A dynamic array is used to buffer up all the input characters.  Normally,
 * "k" characters are stored in the buffer.  More characters may be stored during
 * guess mode (testing syntactic predicate), or when LT(i>k) is referenced.
 * Consumption of characters is deferred.  In other words, reading the next
 * character is not done by conume(), but deferred until needed by LA or LT.
 * <p>
 *
 * @see antlr.CharQueue
 */

#include "antlr/config.hpp"
#include <istream>
#include "antlr/CircularQueue.hpp"

class CharBuffer {
private:
	// char source
	std::istream& input;

	// Number of active markers
	int nMarkers; // = 0;

	// Additional offset used when markers are active
	int markerOffset; // = 0;

	// Number of calls to consume() since last LA() or LT() call
	int numToConsume; // = 0;

	// Circular queue
	CircularQueue<int> queue;







public:
	/** Create a character buffer */
	CharBuffer(std::istream& input_);

	virtual ~CharBuffer() {}

	/** Mark another character for deferred consumption */
	void consume();

private:
	/** Ensure that the character buffer is sufficiently full */
	void fill(int amount);

public:
	/** Get a lookahead character */
	int LA(int i);

	/**Return an integer marker that can be used to rewind the buffer to
	 * its current state.
	 */
	int mark();

	/**Rewind the character buffer to a marker.
	 * @param mark Marker returned previously from mark()
	 */
	void rewind(int mark);

private:
	/** Sync up deferred consumption */
	void syncConsume();

	CharBuffer(const CharBuffer& other);
	CharBuffer& operator=(const CharBuffer& other);
};

#endif //INC_CharBuffer_hpp__
