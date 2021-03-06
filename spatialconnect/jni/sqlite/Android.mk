LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# If using SEE, uncomment the following:
# LOCAL_CFLAGS += -DSQLITE_HAS_CODEC

# This is important - it causes SQLite to use memory for temp files. Since
# Android has no globally writable temp directory, if this is not defined the
# application throws an exception when it tries to create a temp file.
#
LOCAL_CFLAGS += -DSQLITE_TEMP_STORE=3

# enable rtree module
LOCAL_CFLAGS += -DSQLITE_ENABLE_RTREE=1

LOCAL_CFLAGS += -DHAVE_CONFIG_H -DKHTML_NO_EXCEPTIONS -DGKWQ_NO_JAVA
LOCAL_CFLAGS += -DNO_SUPPORT_JS_BINDING -DQT_NO_WHEELEVENT -DKHTML_NO_XBL
LOCAL_CFLAGS += -U__APPLE__
LOCAL_CFLAGS += -DHAVE_STRCHRNUL=0
LOCAL_CFLAGS += -Wno-unused-parameter -Wno-int-to-pointer-cast
LOCAL_CFLAGS += -Wno-maybe-uninitialized -Wno-parentheses
LOCAL_CPPFLAGS += -Wno-conversion-null

ifeq ($(TARGET_ARCH), arm)
	LOCAL_CFLAGS += -DPACKED="__attribute__ ((packed))"
else
	LOCAL_CFLAGS += -DPACKED=""
endif

LOCAL_SRC_FILES:=                             \
	android_database_SQLiteCommon.cpp     \
	android_database_SQLiteConnection.cpp \
	android_database_SQLiteGlobal.cpp     \
	android_database_SQLiteDebug.cpp      \
	JNIHelp.cpp JniConstants.cpp

LOCAL_SRC_FILES += sqlite3.c

LOCAL_C_INCLUDES += $(LOCAL_PATH) $(LOCAL_PATH)/nativehelper/

LOCAL_MODULE:= libsqliteX
LOCAL_LDLIBS += -ldl -llog

include $(BUILD_SHARED_LIBRARY)

# Build the statically linked shell for android
include $(CLEAR_VARS)
LOCAL_MODULE            := sqlite3-static-cli
LOCAL_MODULE_FILENAME   := sqlite3-static
# TODO build static versions for linking
LOCAL_STATIC_LIBRARIES  := libsqlite3X
LOCAL_SRC_FILES         := shell.c sqlite3.c
LOCAL_C_INCLUDES        := $(LOCAL_PATH)
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_CFLAGS            := -DSQLITE_THREADSAFE=1 -fPIE -DSQLITE_ENABLE_RTREE=1
LOCAL_LDFLAGS           := -fPIE -pie
include $(BUILD_EXECUTABLE)
