cmake_minimum_required(VERSION 3.22)
project(house_snapclient LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set_property(GLOBAL PROPERTY JOB_POOLS native_compile=2)
set(CMAKE_JOB_POOL_COMPILE native_compile)

# Build only the upstream TCP / FLAC / PCM / OpenSL client used by the Pi stream.
# No Snapcast server, discovery, TLS, or optional codec libraries are linked.
foreach(option BUILD_CXXLIBS BUILD_PROGRAMS BUILD_EXAMPLES BUILD_TESTING BUILD_DOCS WITH_OGG BUILD_SHARED_LIBS INSTALL_MANPAGES)
    set(${option} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory(deps/flac EXCLUDE_FROM_ALL)
set(SNAP "${CMAKE_CURRENT_SOURCE_DIR}/vendor")
add_executable(libsnapclient.so
    launcher.cpp
    ${SNAP}/client/client_connection.cpp ${SNAP}/client/controller.cpp
    ${SNAP}/client/snapclient.cpp ${SNAP}/client/stream.cpp ${SNAP}/client/time_provider.cpp
    ${SNAP}/client/decoder/pcm_decoder.cpp ${SNAP}/client/decoder/null_decoder.cpp
    ${SNAP}/client/decoder/flac_decoder.cpp ${SNAP}/client/player/player.cpp
    ${SNAP}/client/player/file_player.cpp ${SNAP}/client/player/opensl_player.cpp
    ${SNAP}/common/resampler.cpp ${SNAP}/common/sample_format.cpp ${SNAP}/common/utils/string_utils.cpp)
set_source_files_properties(${SNAP}/client/snapclient.cpp PROPERTIES COMPILE_DEFINITIONS "main=snapclient_main")
target_include_directories(libsnapclient.so PRIVATE ${SNAP} ${SNAP}/client)
target_include_directories(libsnapclient.so SYSTEM PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/deps/boost)
target_compile_definitions(libsnapclient.so PRIVATE ANDROID HAS_OPENSL HAS_FLAC
    BOOST_ERROR_CODE_HEADER_ONLY BOOST_ASIO_NO_TS_EXECUTORS VERSION="0.31.0"
    REVISION="cf2be07155b850fd3d660416164970688179ce5c")
target_link_libraries(libsnapclient.so PRIVATE FLAC log OpenSLES)
target_link_options(libsnapclient.so PRIVATE "-Wl,-z,max-page-size=16384")
