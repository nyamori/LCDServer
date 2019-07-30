LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
 
LOCAL_SRC_FILES := $(call all-subdir-java-files)
 
LOCAL_PACKAGE_NAME := com.example.lcdserver
 
LOCAL_CERTIFICATE := platform
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -lfriendlyarm-hardware
 
include $(BUILD_PACKAGE)
