mnemonifier
===========

Convert Unicode strings to ASCII-only strings that can be converted back
to the original strings while still trying to be human-readable.


Description
-----------

Mnemonifier provides methods for converting Unicode strings (containing any
Unicode characters including those not in the Basic Multilingual Plane) to
ASCII-only strings that can be converted back to the original strings while
still trying to be human-readable.

This is achieved by using RFC1345 mnemonics ({@code à} becomes `[a!]`)
and decomposition mappings (`Ǹ` becomes `[N|!]`). Anything not
included in either of these two lists is represented as hex code (`€`
becomes `[#20AC]`).

To enable rounddtrip conversion, any square brackets inside the original
string are replaced by `[[]` and `[]]`, respectively.

The decoder is available in two versions: The strict version will throw an
exception if any square bracket is not encoded correctly, the lax version
(for cases where the user is able to edit/type encoded strings) will pass
these unchanged.

Subclass implementations can override `#getCodepointInfo` to
provide additional information about an unencodable character, which is added
in curly braces (for example `[#20AC{EUR}]`).


Unidecode integration
---------------------

mnemonifier-unidecode uses [Unidecode](https://github.com/xuender/unidecode)
to provide codepoint info for codepoints that are not covered by RFC1345 or
by decomposition mapping.
