// RNNoise 0.2's vector code (vec.h, vec_neon.h) includes Opus's os_support.h, which RNNoise's release doesn't ship.
// This stands in for it with the little that code uses.
#ifndef OS_SUPPORT_H
#define OS_SUPPORT_H

#include <string.h>

#ifndef OPUS_INLINE
#define OPUS_INLINE inline
#endif

#define OPUS_CLEAR(dst, n) (memset((dst), 0, (n) * sizeof(*(dst))))
#define OPUS_COPY(dst, src, n) (memcpy((dst), (src), (n) * sizeof(*(dst))))

#endif
