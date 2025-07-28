cmake_minimum_required(VERSION 3.22.1)

project("athena")

add_library(${CMAKE_PROJECT_NAME} SHARED
        athena.h
        athena.c
        session/ip.c
        session/session.c
        protocols/icmp.c
        protocols/tcp.c
        protocols/udp.c
        utils/util.c
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)

# Add 16KB page alignment for Android 15+ compatibility
if(ANDROID_PLATFORM_LEVEL GREATER_EQUAL 35)
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        -Wl,-z,max-page-size=16384
    )
endif()

# Always add 16KB alignment for future compatibility
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
    -Wl,-z,max-page-size=16384
)