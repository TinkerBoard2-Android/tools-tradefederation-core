# Copyright (C) 2010 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH := $(call my-dir)
COMPATIBILITY.tradefed_tests_dir := \
  $(COMPATIBILITY.tradefed_tests_dir) $(LOCAL_PATH)/res/config $(LOCAL_PATH)/tests/res/config

include $(CLEAR_VARS)

# makefile rules to copy jars to HOST_OUT/tradefed
# so tradefed.sh can automatically add to classpath
deps := $(call copy-many-files,\
  $(call intermediates-dir-for,JAVA_LIBRARIES,tradefed,HOST)/javalib.jar:$(HOST_OUT)/tradefed/tradefed.jar)

# this dependency ensures the above rule will be executed if jar is installed
$(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar : $(deps)
# The copy rule for loganalysis is in tools/loganalysis/Android.mk
$(HOST_OUT_JAVA_LIBRARIES)/tradefed.jar : $(HOST_OUT)/tradefed/loganalysis.jar

#######################################################
include $(CLEAR_VARS)

# Create a simple alias to build all the TF-related targets
# Note that this is incompatible with `make dist`.  If you want to make
# the distribution, you must run `tapas` with the individual target names.
.PHONY: tradefed-core
tradefed-core: tradefed atest_tradefed tradefed-contrib tf-contrib-tests script_help

.PHONY: tradefed-all
tradefed-all: tradefed-core tradefed-tests tradefed_win verify loganalysis-tests

# ====================================
include $(CLEAR_VARS)
# copy tradefed.sh script to host dir

LOCAL_MODULE_TAGS := optional

LOCAL_PREBUILT_EXECUTABLES := tradefed.sh tradefed_win.bat script_help.sh verify.sh run_tf_cmd.sh atest_tradefed.sh
include $(BUILD_HOST_PREBUILT)

########################################################
# Zip up the built files and dist it as tradefed.zip

tradefed_dist_host_jars := tradefed tradefed-tests loganalysis loganalysis-tests tf-remote-client tradefed-contrib tf-contrib-tests
tradefed_dist_host_exes := tradefed.sh tradefed_win.bat script_help.sh verify.sh run_tf_cmd.sh atest_tradefed.sh
tradefed_dist_test_apks := TradeFedUiTestApp TradeFedTestApp DeviceSetupUtil

# Generate a src:dest list of copies to perform.
# The source should always be an intermediate / source location, not the
# installed location, as that will force installation, and cause this zip to be
# regenerated too often during incremental builds.
tradefed_dist_copy_pairs := $(foreach m, $(tradefed_dist_host_jars), $(call intermediates-dir-for,JAVA_LIBRARIES,$(m),HOST,COMMON)/javalib.jar:$(m).jar)
tradefed_dist_copy_pairs += $(foreach m, $(tradefed_dist_host_exes), $(LOCAL_PATH)/$(m):$(m))
tradefed_dist_copy_pairs += $(foreach m, $(tradefed_dist_test_apks), $(call intermediates-dir-for,APPS,$(m))/package.apk:$(m).apk)

tradefed_dist_host_jars :=
tradefed_dist_host_exes :=
tradefed_dist_test_apks :=

tradefed_dist_intermediates := $(call intermediates-dir-for,PACKAGING,tradefed_dist,HOST,COMMON)
tradefed_dist_zip := $(tradefed_dist_intermediates)/tradefed.zip
$(tradefed_dist_zip) : PRIVATE_COPY_PAIRS := $(tradefed_dist_copy_pairs)
$(tradefed_dist_zip) : $(SOONG_ZIP) $(foreach f,$(tradefed_dist_copy_pairs),$(call word-colon,1,$(f)))
	rm -rf $(dir $@)/tmp && mkdir -p $(dir $@)/tmp
	$(foreach f,$(PRIVATE_COPY_PAIRS), \
	  cp -f $(call word-colon,1,$(f)) $(dir $@)/tmp/$(call word-colon,2,$(f)) &&) true
	echo $(BUILD_NUMBER_FROM_FILE) > $(dir $@)/tmp/version.txt
	$(SOONG_ZIP) -o $@ -C $(dir $@)/tmp -f $(dir $@)/tmp/version.txt \
	  $(foreach f,$(PRIVATE_COPY_PAIRS),-f $(dir $@)/tmp/$(call word-colon,2,$(f)))

$(call dist-for-goals, tradefed, $(tradefed_dist_zip))

tradefed_dist_copy_pairs :=
tradefed_dist_intermediates :=
tradefed_dist_zip :=

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
