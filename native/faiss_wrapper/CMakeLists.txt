cmake_minimum_required(VERSION 3.10)
project(faiss_wrapper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

add_library(faiss_wrapper SHARED
    src/faiss_wrapper.cpp
)

# Exported include dir for consumers
target_include_directories(faiss_wrapper PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

# Optional: if FAISS sources are present under native/third_party/faiss, add as subdirectory
set(FAISS_SUBDIR "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/faiss")
if(EXISTS ${FAISS_SUBDIR})
    message(STATUS "FAISS source detected at ${FAISS_SUBDIR}, adding subdirectory")
    add_subdirectory(${FAISS_SUBDIR} ${CMAKE_CURRENT_BINARY_DIR}/faiss_build EXCLUDE_FROM_ALL)
endif()

# If a target named 'faiss' exists (from FAISS CMake), link it. Otherwise allow build without FAISS.
if(TARGET faiss)
    message(STATUS "Linking faiss target to faiss_wrapper")
    target_link_libraries(faiss_wrapper PUBLIC faiss)
elseif(TARGET faiss_static)
    message(STATUS "Linking faiss_static target to faiss_wrapper")
    target_link_libraries(faiss_wrapper PUBLIC faiss_static)
else()
    message(STATUS "FAISS targets not found; building faiss_wrapper without FAISS (prototype)")
endif()

