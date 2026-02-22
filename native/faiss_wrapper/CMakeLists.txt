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

# Prefer prebuilt FAISS artifacts if present (workflow may extract to ../third_party/faiss_prebuilt)
set(FAISS_PREBUILT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/faiss_prebuilt")
if(EXISTS ${FAISS_PREBUILT_DIR})
    if(DEFINED ANDROID_ABI)
        set(FAISS_PREBUILT_LIB_DIR "${FAISS_PREBUILT_DIR}/${ANDROID_ABI}/lib")
        set(FAISS_PREBUILT_INCLUDE_DIR "${FAISS_PREBUILT_DIR}/${ANDROID_ABI}/include")
    else()
        set(FAISS_PREBUILT_LIB_DIR "${FAISS_PREBUILT_DIR}/lib")
        set(FAISS_PREBUILT_INCLUDE_DIR "${FAISS_PREBUILT_DIR}/include")
    endif()

    if(EXISTS ${FAISS_PREBUILT_LIB_DIR})
        message(STATUS "Using prebuilt FAISS from ${FAISS_PREBUILT_LIB_DIR}")
        target_include_directories(faiss_wrapper PRIVATE ${FAISS_PREBUILT_INCLUDE_DIR})
        # Try to find a shared lib first, then static archive
        find_library(FAISS_PREBUILT_LIB faiss PATHS ${FAISS_PREBUILT_LIB_DIR} NO_DEFAULT_PATH)
        if(FAISS_PREBUILT_LIB)
            target_link_libraries(faiss_wrapper PRIVATE ${FAISS_PREBUILT_LIB})
        elseif(EXISTS ${FAISS_PREBUILT_LIB_DIR}/libfaiss.a)
            target_link_libraries(faiss_wrapper PRIVATE ${FAISS_PREBUILT_LIB_DIR}/libfaiss.a)
        else()
            message(STATUS "No libfaiss found in prebuilt dir, falling back to source build if available")
        endif()
    endif()
endif()

# Ensure PIC for static linking into shared libs on Android
set_target_properties(faiss_wrapper PROPERTIES POSITION_INDEPENDENT_CODE ON)

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

