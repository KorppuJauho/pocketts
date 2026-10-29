# RNNoise, Xiph's AI noise removal, for the microphone (Settings › Capture › AI noise removal).
#
# Its release is downloaded when building and checked against its SHA-256, then built here: the repository holds
# only this file and the bridge to Kotlin (rnnoise_jni.c), not RNNoise's code or its several-megabyte model.
cmake_minimum_required(VERSION 3.22)
project(pocketts_native C)

include(FetchContent)
FetchContent_Declare(rnnoise
    URL https://github.com/xiph/rnnoise/releases/download/v0.2/rnnoise-0.2.tar.gz
    URL_HASH SHA256=90fce4b00b9ff24c08dbfe31b82ffd43bae383d85c5535676d28b0a2b11c0d37
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
)
FetchContent_MakeAvailable(rnnoise)
set(RNNOISE ${rnnoise_SOURCE_DIR})

# The library's own sources, as its Makefile.am lists them (without the x86-only ones)
add_library(pocketts_rnnoise SHARED
    rnnoise_jni.c
    ${RNNOISE}/src/denoise.c
    ${RNNOISE}/src/rnn.c
    ${RNNOISE}/src/pitch.c
    ${RNNOISE}/src/kiss_fft.c
    ${RNNOISE}/src/celt_lpc.c
    ${RNNOISE}/src/nnet.c
    ${RNNOISE}/src/nnet_default.c
    ${RNNOISE}/src/parse_lpcnet_weights.c
    ${RNNOISE}/src/rnnoise_data.c
    ${RNNOISE}/src/rnnoise_tables.c
)
# compat: what the release leaves out (see compat/os_support.h)
target_include_directories(pocketts_rnnoise PRIVATE ${RNNOISE}/include ${RNNOISE}/src compat)
# Optimised in test builds too: unoptimised, it would be too slow to test what the release does
target_compile_options(pocketts_rnnoise PRIVATE -O2 -w)
target_link_libraries(pocketts_rnnoise PRIVATE m)
