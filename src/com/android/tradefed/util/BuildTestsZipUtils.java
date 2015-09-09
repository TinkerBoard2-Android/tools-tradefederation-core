/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tradefed.util;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.IDeviceBuildInfo;
import com.android.tradefed.targetprep.AltDirBehavior;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A helper class for operations related to tests zip generated by Android build system
 */
public class BuildTestsZipUtils {

    /**
     * Resolve the actual apk path based on testing artifact information inside build info.
     *
     * @param buildInfo build artifact information
     * @param apkFileName filename of the apk to install
     * @param altDirs alternative search paths, in addition to path inside {@code buildInfo}
     * @param altDirBehavior how alternative search paths should be used against path inside
     *        {@code buildInfo}: as fallback, or as override; if unspecified, fallback will be used
     * @param lookupInResource if the file should be looked up in test harness resources as a final
     *        fallback mechanism
     * @param deviceSigningKey
     * @return a {@link File} representing the physical apk file on host or {@code null} if the
     *     file does not exist.
     */
    public static File getApkFile(IBuildInfo buildInfo, String apkFileName,
            List<File> altDirs, AltDirBehavior altDirBehavior,
            boolean lookupInResource, String deviceSigningKey) throws IOException {
        String apkBase = apkFileName.split("\\.")[0];

        List<File> dirs = new ArrayList<>();
        if (altDirs != null) {
            for (File dir : altDirs) {
                dirs.add(dir);
                // Files in tests zip file will be in DATA/app/ or
                // DATA/app/apk_name
                dirs.add(FileUtil.getFileForPath(dir, "DATA", "app"));
                dirs.add(FileUtil.getFileForPath(dir, "DATA", "app", apkBase));
                // Files in out dir will be in in uses data/app/apk_name
                dirs.add(FileUtil.getFileForPath(dir, "data", "app", apkBase));
            }
        }
        // reverse the order so ones provided via command line last can be searched first
        Collections.reverse(dirs);

        List<File> expandedTestDirs = new ArrayList<>();
        if (buildInfo != null && buildInfo instanceof IDeviceBuildInfo) {
            File testsDir = ((IDeviceBuildInfo)buildInfo).getTestsDir();
            if (testsDir != null && testsDir.exists()) {
                expandedTestDirs.add(FileUtil.getFileForPath(testsDir, "DATA", "app"));
                expandedTestDirs.add(FileUtil.getFileForPath(testsDir, "DATA", "app", apkBase));
            }
        }
        if (altDirBehavior == null) {
            altDirBehavior = AltDirBehavior.FALLBACK;
        }
        if (altDirBehavior == AltDirBehavior.FALLBACK) {
            // alt dirs are appended after build artifact dirs
            expandedTestDirs.addAll(dirs);
            dirs = expandedTestDirs;
        } else if (altDirBehavior == AltDirBehavior.OVERRIDE) {
            dirs.addAll(expandedTestDirs);
        } else {
            throw new IOException("Missing handler for alt-dir-behavior: " + altDirBehavior);
        }
        if (dirs.isEmpty() && !lookupInResource) {
            throw new IOException(
                    "Provided buildInfo does not contain a valid tests directory and no " +
                    "fallback options were provided");
        }

        for (File dir : dirs) {
            File testAppFile = new File(dir, apkFileName);
            if (testAppFile.exists()) {
                return testAppFile;
            }
        }
        if (lookupInResource) {
            List<String> resourceLookup = new ArrayList<>();
            if (deviceSigningKey != null) {
                resourceLookup.add(String.format("/apks/%s-%s.apk", apkBase, deviceSigningKey));
            }
            resourceLookup.add(String.format("/apks/%s", apkFileName));
            File apkTempFile = FileUtil.createTempFile(apkFileName, ".apk");
            URL apkUrl = null;
            for (String path :  resourceLookup) {
                apkUrl = BuildTestsZipUtils.class.getResource(path);
                if (apkUrl != null) {
                    break;
                }
            }
            if (apkUrl != null) {
                FileUtil.writeToFile(apkUrl.openStream(), apkTempFile);
                // since we don't know when the file would be no longer needed, we set the file to
                // be deleted on VM termination
                apkTempFile.deleteOnExit();
                return apkTempFile;
            }
        }
        return null;
    }
}
